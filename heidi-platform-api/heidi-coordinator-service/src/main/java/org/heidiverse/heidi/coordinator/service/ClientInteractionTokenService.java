// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;

/** Creates and hashes opaque capabilities intended for the integrating client's UI. */
@Service
public class ClientInteractionTokenService {

    private static final String PREFIX = "cit_";

    /**
     * Domain-separation context for deriving this service's signing key out of the shared
     * {@code heidi.platform.process-token-signing-key}, so a CIT HMAC is computed under a key distinct from the one
     * {@link TokenSignatureService} signs process tokens with, without provisioning and
     * rotating a second deployment secret.
     */
    private static final String KEY_DERIVATION_CONTEXT = "heidi-client-interaction-token-key-v1";

    private final SecureRandom secureRandom = new SecureRandom();

    private final byte[] signingKey;

    public ClientInteractionTokenService(@Value("${heidi.platform.process-token-signing-key}") byte[] masterKey) {
        this.signingKey = deriveKey(masterKey);
    }

    private static byte[] deriveKey(byte[] masterKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(masterKey, "HmacSHA256"));
            return mac.doFinal(KEY_DERIVATION_CONTEXT.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to derive client interaction token key", e);
        }
    }

    public String generate() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Derives the client capability from the server-generated process id. A process has one stable
     * CIT, which lets a retried start call return the same capability without storing it in clear
     * text. HMAC output is pseudorandom to callers who do not have the signing key.
     */
    public String generate(UUID processId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return PREFIX
                    + Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(
                                    mac.doFinal(
                                            ("client-interaction-token:" + processId)
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate client interaction token", e);
        }
    }

    public String hash(String token) {
        try {
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public boolean isClientInteractionToken(String token) {
        return token != null && token.startsWith(PREFIX) && token.length() > PREFIX.length();
    }
}
