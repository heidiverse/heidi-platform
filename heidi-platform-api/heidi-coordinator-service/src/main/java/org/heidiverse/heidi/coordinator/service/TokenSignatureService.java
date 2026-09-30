// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service;

import org.heidiverse.heidi.coordinator.model.Signable;
import org.heidiverse.heidi.coordinator.model.issuance.SignatureToken;
import org.heidiverse.heidi.coordinator.model.issuance.SignatureTokenWithTxCode;
import org.heidiverse.heidi.coordinator.service.utils.CryptoUtils;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class TokenSignatureService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final ObjectMapper objectMapper;

    @Value("${heidi.platform.process-token-signing-key}")
    private byte[] secretKey;

    @Value("${heidi.platform.transaction-code-master-key}")
    private String txnCodeMasterKey;

    public TokenSignatureService(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Generate a 6-digit numeric transaction code
    private String generateTransactionCode() {
        return String.format("%06d", (int) (Math.random() * 1_000_000));
    }

    // Encrypt the transaction code with AES encryption
    private String encryptTransactionCode(String transactionCode, String salt) {
        try {
            return CryptoUtils.encryptBlob(transactionCode, salt, txnCodeMasterKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt transaction code", e);
        }
    }

    public SignatureTokenWithTxCode generateToken(final Signable object) {
        return generateToken(object, null, false, null);
    }

    public SignatureTokenWithTxCode generateToken(
            final Signable object, final String credentialIdentifier, final Boolean includeTxCode) {
        return generateToken(object, credentialIdentifier, includeTxCode, null);
    }

    public SignatureTokenWithTxCode generateToken(
            final Signable object,
            final String credentialIdentifier,
            final Boolean includeTxCode,
            final String txCode) {
        try {
            final String serializedObject = objectMapper.writeValueAsString(object);

            String serializedObjectWithTxCode = null;
            String finalTxCode = null;
            if (Boolean.TRUE.equals(includeTxCode)) {
                if (credentialIdentifier == null) {
                    throw new IllegalArgumentException(
                            "Credential Identifier is required when including transaction code.");
                }
                Map<String, Object> objectMap =
                        objectMapper.readValue(
                                serializedObject, new TypeReference<Map<String, Object>>() {});

                // Use provided txCode if available, otherwise generate a new one
                finalTxCode = (txCode != null) ? txCode : generateTransactionCode();
                String encryptedTransactionCode =
                        encryptTransactionCode(finalTxCode, credentialIdentifier);

                // Add the encrypted transaction code to the map
                objectMap.put("txCodeEncrypted", encryptedTransactionCode);

                // Serialize the map back to a JSON string
                serializedObjectWithTxCode = objectMapper.writeValueAsString(objectMap);
            }

            // Create an HMAC SHA-256 signature
            final SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, HMAC_SHA256);
            final Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(secretKeySpec);

            // Generate the signature
            final String payload =
                    serializedObjectWithTxCode != null
                            ? serializedObjectWithTxCode
                            : serializedObject;
            final String signature =
                    Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes()));

            final String fullPayload = payload + "." + signature;
            return new SignatureTokenWithTxCode(
                    Base64.getUrlEncoder().encodeToString(fullPayload.getBytes()), finalTxCode);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to generate token", e);
        }
    }

    public <T extends Signable> T verifyAndGetObject(
            final SignatureToken token, final Class<T> clazz) {
        try {
            final String decodedToken = new String(Base64.getUrlDecoder().decode(token.token()));
            final int separatorIndex = decodedToken.lastIndexOf('.');
            if (separatorIndex == -1) {
                throw new Exception("Invalid token");
            }

            final String serializedObject = decodedToken.substring(0, separatorIndex);
            final String signature = decodedToken.substring(separatorIndex + 1);

            final Mac sha256HMAC = Mac.getInstance(HMAC_SHA256);
            final SecretKeySpec secretKey = new SecretKeySpec(this.secretKey, HMAC_SHA256);
            sha256HMAC.init(secretKey);

            final String expectedSignature =
                    Base64.getEncoder()
                            .encodeToString(sha256HMAC.doFinal(serializedObject.getBytes()));

            if (!expectedSignature.equals(signature)) {
                throw new Exception("Invalid token");
            }

            final T object = objectMapper.readValue(serializedObject, clazz);

            // check if token is expired, 30 minutes
            final ZonedDateTime tokenTimestamp = object.getExpiresAt();
            if (tokenTimestamp != null && tokenTimestamp.isBefore(ZonedDateTime.now())) {
                throw new Exception("Token expired");
            }

            return object;
        } catch (final Exception e) {
            throw new RuntimeException("Failed to verify token", e);
        }
    }
}
