// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Process-local store used for development and when no durable store is configured. */
public final class InMemoryRegisteredKeyAuthenticationStore
        implements RegisteredKeyAuthenticationStore {
    private final ConcurrentMap<String, RegistrationRecord> registrations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> createdAt = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> seenSignatures = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> lastUse = new ConcurrentHashMap<>();

    @Override
    public void create(String registrationId, byte[] psk, String client) {
        registrations.put(registrationId, new RegistrationRecord(registrationId, psk, null, client));
        createdAt.put(registrationId, Instant.now());
    }

    @Override
    public Optional<RegistrationRecord> find(String registrationId) {
        return Optional.ofNullable(registrations.get(registrationId));
    }

    @Override
    public Optional<RegistrationRecord> findByClient(String client, byte[] publicKey) {
        return registrations.values().stream()
                .filter(registration -> client.equals(registration.client()))
                .filter(registration -> registration.publicKey() != null
                        && MessageDigest.isEqual(registration.publicKey(), publicKey))
                .findFirst();
    }

    @Override
    public boolean hasCompletedRegistration(String client) {
        return registrations.values().stream()
                .anyMatch(registration -> client.equals(registration.client())
                        && registration.publicKey() != null);
    }

    @Override
    public void recordUse(String registrationId, Instant usedAt) {
        registrations.computeIfPresent(registrationId, (id, registration) -> {
            lastUse.put(id, usedAt.toEpochMilli());
            return registration;
        });
    }

    @Override
    public Optional<Instant> lastUse(String client) {
        return registrations.entrySet().stream()
                .filter(entry -> client != null && client.equals(entry.getValue().client()))
                .filter(entry -> entry.getValue().publicKey() != null)
                .map(entry -> lastUse.get(entry.getKey()))
                .filter(java.util.Objects::nonNull)
                .max(Long::compareTo)
                .map(Instant::ofEpochMilli);
    }

    @Override
    public boolean setPublicKeyIfAbsent(String registrationId, byte[] publicKey) {
        var updated = new boolean[1];
        registrations.computeIfPresent(registrationId, (id, registration) -> {
            if (registration.publicKey() != null) return registration;
            updated[0] = true;
            return new RegistrationRecord(id, registration.psk(), publicKey, registration.client());
        });
        return updated[0];
    }

    @Override
    public boolean recordSignatureIfAbsent(String signature, long seenAt) {
        return seenSignatures.putIfAbsent(signature, seenAt) == null;
    }

    @Override
    public void removeSignature(String signature, long seenAt) {
        seenSignatures.remove(signature, seenAt);
    }

    @Override
    public void purgeSignaturesBefore(long cutoff) {
        seenSignatures.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    @Override
    public void purgeUncompletedBefore(Instant cutoff) {
        registrations.entrySet().removeIf(entry -> {
            var registration = entry.getValue();
            var created = createdAt.get(entry.getKey());
            return registration.publicKey() == null && created != null && created.isBefore(cutoff);
        });
        createdAt.entrySet().removeIf(entry -> !registrations.containsKey(entry.getKey()));
    }
}
