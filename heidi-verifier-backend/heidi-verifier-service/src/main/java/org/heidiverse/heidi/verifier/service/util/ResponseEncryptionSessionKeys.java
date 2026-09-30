// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.util;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.ECFieldFp;
import java.security.spec.ECPoint;
import java.security.spec.ECParameterSpec;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Creates short-lived response keys and protects their private halves at rest. */
@Service
public final class ResponseEncryptionSessionKeys {
    private static final int MASTER_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final byte FORMAT_VERSION = 1;

    private final SecureRandom random = new SecureRandom();
    private final byte[] masterKey;

    @Autowired
    public ResponseEncryptionSessionKeys(
            @Value("${heidi.verifier.session.encryption-master-key:}") final String configured) {
        try {
            masterKey = HexFormat.of().parseHex(configured == null ? "" : configured.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "heidi.verifier.session.encryption-master-key must be hex", exception);
        }
        if (masterKey.length != MASTER_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "heidi.verifier.session.encryption-master-key must contain 32 bytes");
        }
    }

    /** Constructor used by focused tests. */
    public ResponseEncryptionSessionKeys(final byte[] masterKey) {
        if (masterKey == null || masterKey.length != MASTER_KEY_BYTES) {
            throw new IllegalArgumentException("Verifier session master key must contain 32 bytes");
        }
        this.masterKey = masterKey.clone();
    }

    public Generated generate(final String requestId) {
        try {
            var key = new ECKeyGenerator(Curve.P_256)
                    .keyID("response-" + UUID.randomUUID())
                    .generate();
            var publicJwk = key.toPublicJWK().toJSONString();
            return new Generated(key.getKeyID(), publicJwk, encrypt(requestId, key.toJSONString()));
        } catch (JOSEException exception) {
            throw new IllegalStateException("Could not generate response encryption key", exception);
        }
    }

    public ECKey decrypt(final ResponseKey key, final JWEObject response) {
        try {
            if (!JWEAlgorithm.ECDH_ES.equals(response.getHeader().getAlgorithm())
                    || (!EncryptionMethod.A128GCM.equals(response.getHeader().getEncryptionMethod())
                    && !EncryptionMethod.A256GCM.equals(response.getHeader().getEncryptionMethod()))) {
                throw new IllegalArgumentException("Unsupported response encryption parameters");
            }
            var epk = response.getHeader().getEphemeralPublicKey();
            if (!(epk instanceof ECKey ephemeral)
                    || !Curve.P_256.equals(ephemeral.getCurve())) {
                throw new IllegalArgumentException("Response encryption epk must be P-256");
            }
            validatePoint(ephemeral.toECPublicKey().getW(), ephemeral.toECPublicKey().getParams());
            var privateJwk = JWK.parse(decrypt(key.requestId(), key.encryptedPrivateJwk()));
            if (!(privateJwk instanceof ECKey ec)
                    || !ec.isPrivate()
                    || !Curve.P_256.equals(ec.getCurve())
                    || !key.keyId().equals(ec.getKeyID())) {
                throw new IllegalArgumentException("Stored response encryption key is invalid");
            }
            var publicJwk = JWK.parse(key.publicJwk());
            if (!key.keyId().equals(publicJwk.getKeyID())
                    || !publicJwk.computeThumbprint().equals(ec.toPublicJWK().computeThumbprint())) {
                throw new IllegalArgumentException("Stored response encryption key does not match");
            }
            response.decrypt(new ECDHDecrypter(ec));
            return ec;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "No response encryption key decrypts this response", exception);
        }
    }

    public record Generated(String keyId, String publicJwk, String encryptedPrivateJwk) {}

    public record ResponseKey(
            String requestId, String keyId, String publicJwk, String encryptedPrivateJwk) {}

    /** Reject invalid peer points before the response private key participates in ECDH. */
    private static void validatePoint(ECPoint point, ECParameterSpec parameters) {
        if (point == null || ECPoint.POINT_INFINITY.equals(point)) {
            throw new IllegalArgumentException("Response encryption epk must not be the point at infinity");
        }
        if (!(parameters.getCurve().getField() instanceof ECFieldFp field)) {
            throw new IllegalArgumentException("Response encryption epk must use a prime-field curve");
        }
        var p = field.getP();
        var x = point.getAffineX();
        var y = point.getAffineY();
        if (x.signum() < 0 || y.signum() < 0 || x.compareTo(p) >= 0 || y.compareTo(p) >= 0) {
            throw new IllegalArgumentException("Response encryption epk is outside the curve field");
        }
        var left = y.modPow(BigInteger.TWO, p);
        var right = x.modPow(BigInteger.valueOf(3), p)
                .add(parameters.getCurve().getA().multiply(x))
                .add(parameters.getCurve().getB())
                .mod(p);
        if (!left.equals(right)) {
            throw new IllegalArgumentException("Response encryption epk is not on the curve");
        }
    }

    private String encrypt(final String requestId, final String plaintext) {
        var iv = new byte[GCM_IV_BYTES];
        random.nextBytes(iv);
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(masterKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(aad(requestId));
            var ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            var value = new byte[1 + iv.length + ciphertext.length];
            value[0] = FORMAT_VERSION;
            System.arraycopy(iv, 0, value, 1, iv.length);
            System.arraycopy(ciphertext, 0, value, 1 + iv.length, ciphertext.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encrypt response encryption key", exception);
        }
    }

    private String decrypt(final String requestId, final String encrypted) {
        try {
            var value = Base64.getUrlDecoder().decode(encrypted);
            if (value.length <= 1 + GCM_IV_BYTES + 16 || value[0] != FORMAT_VERSION) {
                throw new IllegalArgumentException("Invalid encrypted response key");
            }
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(masterKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, value, 1, GCM_IV_BYTES));
            cipher.updateAAD(aad(requestId));
            return new String(
                    cipher.doFinal(value, 1 + GCM_IV_BYTES, value.length - 1 - GCM_IV_BYTES),
                    StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not decrypt response encryption key", exception);
        }
    }

    private static byte[] aad(final String requestId) {
        return ("heidi-verifier-response:" + requestId + ":v1")
                .getBytes(StandardCharsets.UTF_8);
    }
}
