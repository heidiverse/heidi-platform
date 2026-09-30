// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import static uniffi.heidi_signing.Heidi_signing_jvmKt.generatePsk;
import static uniffi.heidi_signing.Heidi_signing_jvmKt.verify;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.heidiverse.heidi.shared.signing.SigningKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uniffi.heidi_signing.AuthException;

/**
 * Server half of the registered-key profile described in the signing protocol.
 *
 * <p>The seed never reaches this class. The client derives the Ed25519 key from its seed; this
 * service stores only the public key and the PSK issued for the registration. The Rust signer
 * interface is used for verification because it is the normative implementation of Ed25519ph
 * with context strings in this repository.
 */
@Service
public final class RegisteredKeyAuthenticationService {
    private static final Duration DEFAULT_REGISTRATION_TIMEOUT = Duration.ofMinutes(5);
    private final String provider;
    private final long timestampWindowMillis;
    private final Duration registrationTimeout;
    private final RegisteredKeyAuthenticationStore store;
    private final SigningClients clients;

    public RegisteredKeyAuthenticationService(
            @Value("${heidi.signing.provider-scheme:software}") String provider,
            @Value("${heidi.signing.auth.timestamp-window:60s}") Duration timestampWindow) {
        this(provider, timestampWindow, new InMemoryRegisteredKeyAuthenticationStore(),
                DEFAULT_REGISTRATION_TIMEOUT,
                new SigningClients(Map.of(), SigningClients.Acceptance.ALLOW_LIST, "platform"));
    }

    public RegisteredKeyAuthenticationService(
            String provider,
            Duration timestampWindow,
            RegisteredKeyAuthenticationStore store) {
        this(provider, timestampWindow, store, DEFAULT_REGISTRATION_TIMEOUT,
                new SigningClients(Map.of(), SigningClients.Acceptance.ALLOW_LIST, "platform"));
    }

    @Autowired
    public RegisteredKeyAuthenticationService(
            @Value("${heidi.signing.provider-scheme:software}") String provider,
            @Value("${heidi.signing.auth.timestamp-window:60s}") Duration timestampWindow,
            RegisteredKeyAuthenticationStore store,
            SigningClients clients,
            @Value("${heidi.signing.auth.registration-timeout:5m}")
                    Duration registrationTimeout) {
        this(provider, timestampWindow, store, registrationTimeout, clients);
    }

    private RegisteredKeyAuthenticationService(
            String provider,
            Duration timestampWindow,
            RegisteredKeyAuthenticationStore store,
            Duration registrationTimeout,
            SigningClients clients) {
        this.provider = provider;
        this.timestampWindowMillis = timestampWindow.toMillis();
        this.registrationTimeout = registrationTimeout == null || registrationTimeout.isNegative()
                ? DEFAULT_REGISTRATION_TIMEOUT : registrationTimeout;
        this.store = store;
        this.clients = clients;
    }

    public RegisteredKeyAuthenticationService(
            String provider,
            Duration timestampWindow,
            RegisteredKeyAuthenticationStore store,
            SigningClients clients) {
        this(provider, timestampWindow, store, DEFAULT_REGISTRATION_TIMEOUT, clients);
    }

    /**
     * Opens, or returns, the registration of a named client. The client proves possession in the
     * second leg, so handing a known client back its PSK gives nothing away: only the holder of the
     * matching private key can use it.
     */
    public Registration open(String requestedProvider, String client, byte[] publicKey) {
        store.purgeUncompletedBefore(Instant.now().minus(registrationTimeout));
        if (requestedProvider == null || !provider.equals(requestedProvider)) {
            throw new SigningKeyException("Unknown signing provider: " + requestedProvider);
        }
        if (client == null || client.isBlank()) {
            throw new SigningAuthorizationException("This signing service requires a named client");
        }
        if (!clients.accepts(client, publicKey)) {
            throw new SigningAuthorizationException("Unaccepted signing client: " + client);
        }

        if (publicKey != null) {
            var existing = store.findByClient(client, publicKey);
            if (existing.isPresent()) {
                var registration = existing.get();
                return new Registration(registration.id(), registration.psk(), registration.publicKey());
            }
        }

        try {
            var id = UUID.randomUUID().toString();
            var psk = generatePsk(provider);
            store.create(id, psk, client);
            return new Registration(id, psk, null);
        } catch (AuthException exception) {
            throw new SigningKeyException("Could not open signing registration", exception);
        }
    }

    /** The named client a registration belongs to. */
    public String client(String registrationId) {
        return requiredRegistration(registrationId).client();
    }

    /** Whether the client completed a registration, which the connection check reports. */
    public boolean isRegistered(String client) {
        return store.hasCompletedRegistration(client);
    }

    public Instant lastUse(String client) {
        return store.lastUse(client).orElse(null);
    }

    public void registerPublicKey(String registrationId, byte[] publicKey, byte[] signature) {
        if (publicKey == null || signature == null) {
            throw new SigningKeyException("Public key and registration proof are required");
        }
        var registration = requiredRegistration(registrationId);
        if (registration.publicKey() != null) {
            if (MessageDigest.isEqual(registration.publicKey(), publicKey)) return;
            throw new SigningKeyException("Signing registration is already completed");
        }
        // Opening proves nothing: the key it names is public. Completion has to present the key
        // this service accepts for that client, or a caller could open under another's name.
        if (registration.client() != null
                && !clients.accepts(registration.client(), publicKey)) {
            throw new SigningAuthorizationException(
                    "Signing registration completed with an unaccepted key for "
                            + registration.client());
        }
        try {
            verify(publicKey, registration.psk(), null, publicKey, signature);
        } catch (AuthException exception) {
            throw new SigningKeyException("Invalid signing registration proof", exception);
        }
        if (!store.setPublicKeyIfAbsent(registrationId, publicKey)) {
            throw new SigningKeyException("Signing registration is already completed");
        }
    }

    public void authenticate(
            String registrationId,
            String keyId,
            String timestamp,
            String purpose,
            byte[] canonicalRequest,
            byte[] signature) {
        var registration = requiredRegistration(registrationId);
        if (registration.publicKey() == null) {
            throw new SigningKeyException("Signing registration has no public key");
        }
        if (registration.client() != null
                && !clients.accepts(registration.client(), registration.publicKey())) {
            throw new SigningAuthorizationException(
                    "Signing client is no longer accepted: " + registration.client());
        }
        long timestampMillis;
        try {
            timestampMillis = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            throw new SigningKeyException("Invalid signing authentication timestamp", exception);
        }
        if (Math.abs(System.currentTimeMillis() - timestampMillis) > timestampWindowMillis) {
            throw new SigningKeyException("Signing authentication timestamp is outside its window");
        }
        if (purpose == null || purpose.isBlank() || canonicalRequest == null || signature == null) {
            throw new SigningKeyException("Incomplete signing authentication");
        }

        var encodedSignature = Base64.getEncoder().encodeToString(signature);
        purgeExpiredSignatures();
        if (!store.recordSignatureIfAbsent(encodedSignature, timestampMillis)) {
            throw new SigningKeyException("Signing authentication was replayed");
        }
        var requestHash = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sha256(canonicalRequest));
        var payload = (timestamp + ":" + purpose + ":" + requestHash)
                .getBytes(StandardCharsets.UTF_8);
        try {
            verify(
                    registration.publicKey(),
                    registration.psk(),
                    keyId == null || keyId.isBlank() ? null : keyId,
                    payload,
                    signature);
            store.recordUse(registration.id(), Instant.now());
        } catch (AuthException exception) {
            store.removeSignature(encodedSignature, timestampMillis);
            throw new SigningKeyException("Invalid signing authentication proof", exception);
        }
    }

    private RegisteredKeyAuthenticationStore.RegistrationRecord requiredRegistration(String registrationId) {
        if (registrationId == null || registrationId.isBlank()) {
            throw new SigningKeyException("Signing registration ID is required");
        }
        return store.find(registrationId)
                .orElseThrow(() -> new SigningKeyException("Unknown signing registration"));
    }

    private void purgeExpiredSignatures() {
        var cutoff = System.currentTimeMillis() - timestampWindowMillis;
        store.purgeSignaturesBefore(cutoff);
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JRE has no SHA-256", exception);
        }
    }

    public record Registration(String id, byte[] psk, byte[] publicKey) {
        public Registration {
            psk = psk.clone();
            publicKey = publicKey == null ? null : publicKey.clone();
        }

        @Override
        public byte[] psk() {
            return psk.clone();
        }

        @Override
        public byte[] publicKey() {
            return publicKey == null ? null : publicKey.clone();
        }
    }
}
