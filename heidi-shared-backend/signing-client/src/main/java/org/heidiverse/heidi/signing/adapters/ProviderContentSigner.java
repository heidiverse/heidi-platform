// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.adapters;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.heidiverse.heidi.shared.signing.SigningKeyException;
import org.heidiverse.heidi.shared.signing.SigningKeyProvider;
import org.heidiverse.heidi.shared.signing.SigningKeyRef;

/** BouncyCastle {@link ContentSigner} backed by a raw provider signature. */
public final class ProviderContentSigner implements ContentSigner {
    private final SigningKeyProvider provider;
    private final SigningKeyRef key;
    private final String joseAlgorithm;
    private final AlgorithmIdentifier algorithmIdentifier;
    private final ByteArrayOutputStream message = new ByteArrayOutputStream();

    /**
     * @param signatureAlgorithm JCA/BC name such as {@code SHA256withECDSA}
     * @param joseAlgorithm provider algorithm such as {@code ES256}
     */
    public ProviderContentSigner(
            SigningKeyProvider provider,
            SigningKeyRef key,
            String signatureAlgorithm,
            String joseAlgorithm) {
        this.provider = provider;
        this.key = key;
        this.joseAlgorithm = joseAlgorithm;
        try {
            this.algorithmIdentifier =
                    new DefaultSignatureAlgorithmIdentifierFinder().find(signatureAlgorithm);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Unknown BouncyCastle signature algorithm: " + signatureAlgorithm, exception);
        }
        if (!joseAlgorithm.equals(key.algorithm())) {
            throw new IllegalArgumentException("JOSE algorithm does not match the signing key");
        }
    }

    @Override
    public AlgorithmIdentifier getAlgorithmIdentifier() {
        return algorithmIdentifier;
    }

    @Override
    public OutputStream getOutputStream() {
        return message;
    }

    @Override
    public byte[] getSignature() {
        try {
            var joseSignature = provider.sign(key, message.toByteArray());
            return "ES256".equals(joseAlgorithm)
                    || "ES384".equals(joseAlgorithm)
                    || "ES512".equals(joseAlgorithm)
                    ? ecdsaJoseToDer(joseSignature)
                    : joseSignature;
        } catch (SigningKeyException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not produce certificate signature", exception);
        }
    }

    private static byte[] ecdsaJoseToDer(byte[] joseSignature) {
        if (joseSignature == null || joseSignature.length == 0 || joseSignature.length % 2 != 0) {
            throw new IllegalArgumentException("ECDSA JOSE signature must be an even-length R||S value");
        }
        int componentLength = joseSignature.length / 2;
        var vector = new ASN1EncodableVector();
        vector.add(new ASN1Integer(new BigInteger(1, copy(joseSignature, 0, componentLength))));
        vector.add(new ASN1Integer(new BigInteger(1, copy(joseSignature, componentLength, componentLength))));
        try {
            return new DERSequence(vector).getEncoded();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not encode ECDSA signature", exception);
        }
    }

    private static byte[] copy(byte[] value, int offset, int length) {
        var result = new byte[length];
        System.arraycopy(value, offset, result, 0, length);
        return result;
    }
}
