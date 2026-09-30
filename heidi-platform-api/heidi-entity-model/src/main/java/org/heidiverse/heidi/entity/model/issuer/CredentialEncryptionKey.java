// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

/** Public metadata for an issuer credential-request decryption key. */
public record CredentialEncryptionKey(
        String keyId,
        String keyUri,
        String algorithm,
        String publicJwk,
        String providerEndpoint,
        String providerAuthenticationMode,
        String keyAlgorithm,
        /** Null means this key applies to every trust framework. */
        IssuerTrustSystem trustSystem) {
    /** Backwards-compatible constructor for keys without a trust-framework scope. */
    public CredentialEncryptionKey(
            String keyId, String keyUri, String algorithm, String publicJwk,
            String providerEndpoint, String providerAuthenticationMode,
            String keyAlgorithm) {
        this(keyId, keyUri, algorithm, publicJwk, providerEndpoint,
                providerAuthenticationMode, keyAlgorithm, null);
    }

    public CredentialEncryptionKey(
            String keyId, String keyUri, String algorithm, String publicJwk,
            String providerEndpoint, String providerAuthenticationMode) {
        this(keyId, keyUri, algorithm, publicJwk, providerEndpoint,
                providerAuthenticationMode, algorithm, null);
    }
}
