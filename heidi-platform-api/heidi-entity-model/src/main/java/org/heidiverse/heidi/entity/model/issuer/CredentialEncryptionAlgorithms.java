// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import java.util.List;

/**
 * JWE parameters the issuer backend can process for OID4VCI Credential Request and Credential
 * Response encryption.
 *
 * <p>Mirrors the algorithm lists in {@code CredentialEncryptionService} of heidi-issuer-backend,
 * which are the ones its JWE library actually implements. This copy exists because the platform
 * validates an issuer's selection and Cockpit offers it; keep the two in step.
 */
public final class CredentialEncryptionAlgorithms {
    /** JWE {@code alg}: how the content encryption key is agreed or wrapped. */
    public static final List<String> KEY_MANAGEMENT =
            List.of(
                    "ECDH-ES",
                    "ECDH-ES+A128KW",
                    "ECDH-ES+A192KW",
                    "ECDH-ES+A256KW",
                    "RSA-OAEP-256");

    /** JWE {@code enc}: how the payload itself is encrypted. */
    public static final List<String> CONTENT_ENCRYPTION =
            List.of(
                    "A128GCM",
                    "A192GCM",
                    "A256GCM",
                    "A128CBC-HS256",
                    "A192CBC-HS384",
                    "A256CBC-HS512");

    /** JWE {@code zip}: payload compression before encryption. */
    public static final List<String> COMPRESSION = List.of("DEF");

    private CredentialEncryptionAlgorithms() {}

    public static List<String> requireSupported(List<String> values, List<String> supported, String field) {
        if (values == null) return List.of();
        var selected = values.stream().map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
        var unsupported = selected.stream().filter(value -> !supported.contains(value)).toList();
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unsupported " + field + ": " + unsupported + ". Supported values: " + supported);
        }
        return selected;
    }
}
