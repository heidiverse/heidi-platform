// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service.utils;

import com.google.crypto.tink.subtle.Hkdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoUtils {

    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final int SHA256_BYTE_COUNT = 32;
    private static final int NONCE_BYTE_COUNT = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtils() {}

    /**
     * Encrypt the given blob using AES/GCM/NoPadding. The symmetric key is derived using HKDF with
     * the given salt and a fixed master key.
     *
     * @param blob payload to be encrypted
     * @param salt salt used for key derivation function
     * @param masterKey 32 bytes random hex string
     * @return encrypted blob (base64 encoded, URL-safe without padding)
     */
    public static String encryptBlob(String blob, String salt, String masterKey)
            throws GeneralSecurityException, IOException {
        // HKDF -> derive AES key
        byte[] expandedAesKey = deriveAes256Key(masterKey, salt);

        // Encrypt using symmetric encryption key
        byte[] encryptedBytes = aesEncrypt(blob, expandedAesKey);

        // Return base-64 encoding (without padding)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(encryptedBytes);
    }

    /**
     * Decrypt given encrypted blob.
     *
     * @param blob encrypted payload (base64 encoded, URL-safe without padding)
     * @param salt salt used for key derivation function
     * @param masterKey 32 bytes random hex string
     * @return original blob string
     */
    public static String decryptBlob(String blob, String salt, String masterKey)
            throws GeneralSecurityException {
        // HKDF -> derive AES key
        byte[] expandedAesKey = deriveAes256Key(masterKey, salt);

        // Decrypt
        byte[] encryptedBytes = Base64.getUrlDecoder().decode(blob);
        return aesDecrypt(encryptedBytes, expandedAesKey);
    }

    public static String generateNonce() {
        byte[] nonce = new byte[NONCE_BYTE_COUNT];
        SECURE_RANDOM.nextBytes(nonce);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
    }

    /**
     * Compute the SHA-256 hash of the input string and return it as a URL-safe Base64-encoded
     * string without padding, prefixed with "sha256-".
     *
     * @param input the string to hash
     * @return the hash in the format "sha256-<url-safe-base64-encoded-hash>"
     * @throws NoSuchAlgorithmException if SHA-256 is not available
     */
    public static String computeSha256Hash(String input) throws NoSuchAlgorithmException {
        return computeSha256Hash(input, Base64.getUrlEncoder().withoutPadding());
    }

    /**
     * Compute a SHA-256 SRI integrity value using standard Base64.
     *
     * @param input the string to hash
     * @return the SRI value in the format "sha256-<base64-encoded-hash>"
     * @throws NoSuchAlgorithmException if SHA-256 is not available
     */
    public static String computeSha256HashSri(String input) throws NoSuchAlgorithmException {
        return computeSha256Hash(input, Base64.getEncoder());
    }

    private static String computeSha256Hash(String input, Base64.Encoder encoder)
            throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        String base64Hash = encoder.encodeToString(hash);
        return "sha256-" + base64Hash;
    }

    private static byte[] deriveAes256Key(String ikm, String salt) throws GeneralSecurityException {
        return Hkdf.computeHkdf(
                "HMACSHA256",
                new BigInteger(ikm, 16).toByteArray(),
                salt.getBytes(StandardCharsets.UTF_8),
                "SSI_ISSUER_ENCRYPTION_KEY".getBytes(StandardCharsets.UTF_8),
                SHA256_BYTE_COUNT);
    }

    private static byte[] aesEncrypt(String payload, byte[] symmetricKey)
            throws GeneralSecurityException, IOException {
        // Encrypt
        SecretKey key = new SecretKeySpec(symmetricKey, "AES");
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] cipherText = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));

        // Concatenate with IV
        byte[] iv = cipher.getIV();
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        byteArray.write(iv);
        byteArray.write(cipherText);
        return byteArray.toByteArray();
    }

    private static String aesDecrypt(byte[] data, byte[] symmetricKey)
            throws GeneralSecurityException {
        // Extract IV
        byte[] iv = Arrays.copyOfRange(data, 0, 12);
        byte[] cipherText = Arrays.copyOfRange(data, 12, data.length);

        // Decrypt
        SecretKey key = new SecretKeySpec(symmetricKey, "AES");
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);
        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }
}
