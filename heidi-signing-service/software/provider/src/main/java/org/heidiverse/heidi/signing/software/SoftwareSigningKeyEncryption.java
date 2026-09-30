// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.software;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.subtle.AesGcmJce;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/** Tink AEAD encryption backed by the software signing master key. */
public final class SoftwareSigningKeyEncryption {
    private static final int KEY_LENGTH = 32;
    private final Aead aead;

    public SoftwareSigningKeyEncryption(String masterKeyHex) {
        if (masterKeyHex == null || masterKeyHex.isBlank()) {
            throw new IllegalArgumentException(
                    "A 32-byte hex encryption master key is required for database key storage");
        }
        final byte[] masterKey;
        try {
            masterKey = HexFormat.of().parseHex(masterKeyHex.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Signing encryption master key must be hexadecimal", exception);
        }
        if (masterKey.length != KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Signing encryption master key must contain exactly 32 bytes");
        }
        try {
            aead = new AesGcmJce(masterKey);
        } catch (GeneralSecurityException exception) {
            Arrays.fill(masterKey, (byte) 0);
            throw new IllegalArgumentException(
                    "Could not initialize the signing key encryption primitive", exception);
        }
        Arrays.fill(masterKey, (byte) 0);
    }

    String encrypt(byte[] plaintext, String salt, String keyId, String algorithm) {
        try {
            var ciphertext = encryptWithAead(plaintext, aad(salt, keyId, algorithm));
            return "v2:" + Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt software signing key", exception);
        }
    }

    byte[] decrypt(String ciphertext, String salt, String keyId, String algorithm) {
        try {
            if (ciphertext == null || !ciphertext.startsWith("v2:")) {
                throw new IllegalArgumentException("Unsupported encrypted signing key format");
            }
            var encrypted = Base64.getUrlDecoder().decode(ciphertext.substring("v2:".length()));
            return decryptWithAead(encrypted, aad(salt, keyId, algorithm));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not decrypt software signing key", exception);
        }
    }

    public byte[] encrypt(byte[] plaintext, byte[] associatedData) {
        try {
            return encryptWithAead(plaintext, associatedData);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt software signing material", exception);
        }
    }

    public byte[] decrypt(byte[] ciphertext, byte[] associatedData) {
        try {
            return decryptWithAead(ciphertext, associatedData);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not decrypt software signing material", exception);
        }
    }

    private byte[] encryptWithAead(byte[] plaintext, byte[] associatedData)
            throws GeneralSecurityException {
        return aead.encrypt(plaintext, associatedData);
    }

    private byte[] decryptWithAead(byte[] ciphertext, byte[] associatedData)
            throws GeneralSecurityException {
        return aead.decrypt(ciphertext, associatedData);
    }

    private static byte[] aad(String salt, String keyId, String algorithm) {
        if (salt == null || salt.isBlank()) {
            throw new IllegalArgumentException("Signing encryption salt must not be blank");
        }
        return ("heidi-signing-key:v2:" + salt + ":" + keyId + ":" + algorithm)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
