// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

import java.util.Set;

/**
 * A signing key, identified by URI.
 *
 * <p>The URI carries both which provider holds the key and which key it is, so one value is enough
 * to route an operation: {@code software://7/issuer-key}, {@code qes://swisscom/creator-a},
 * {@code vault://transit/keys/issuer-1}. The scheme selects the provider; everything after it is
 * that provider's business and is never parsed here.
 *
 * <p>Deliberately carries no private key material. Where the key material actually lives — a
 * database column, an HSM slot, a QTSP account — is the provider's concern, and a reference that
 * could carry secrets would invite passing them around. The public JWK travels with the reference
 * because callers need it constantly (JWKS, {@code kid} derivation, SubjectPublicKeyInfo for a
 * certificate request) and asking the provider every time would be a round-trip for public data.
 *
 * @param uri scheme-qualified key identifier
 * @param publicJwk the key's public half, as a JWK document
 * @param algorithm JOSE algorithm name, e.g. {@code ES256}, {@code EdDSA}, {@code ML-DSA-65}
 * @param usages technical operations allowed for this key
 */
public record SigningKeyRef(
        String uri, String publicJwk, String algorithm, Set<SigningKeyUsage> usages) {

    public SigningKeyRef(String uri, String publicJwk, String algorithm) {
        this(uri, publicJwk, algorithm, Set.of(SigningKeyUsage.SIGN));
    }

    public SigningKeyRef {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("Signing key URI must not be blank");
        }
        var separator = uri.indexOf("://");
        if (separator < 1 || separator == uri.length() - 3) {
            throw new IllegalArgumentException("Signing key URI must be scheme-qualified");
        }
        if (publicJwk == null || publicJwk.isBlank()) {
            throw new IllegalArgumentException("Signing key public JWK must not be blank");
        }
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("Signing key algorithm must not be blank");
        }
        if (usages == null || usages.isEmpty()) {
            throw new IllegalArgumentException("Signing key usages must not be empty");
        }
        usages = Set.copyOf(usages);
    }

    /** The scheme that selects the provider, e.g. {@code software} for {@code software://7/issuer-key}. */
    public String scheme() {
        var separator = uri.indexOf("://");
        return uri.substring(0, separator);
    }
}
