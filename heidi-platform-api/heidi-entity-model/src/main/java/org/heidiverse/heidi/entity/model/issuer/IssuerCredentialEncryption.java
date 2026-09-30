// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import java.util.List;

/**
 * One issuer's OID4VCI message-encryption policy, named after the metadata it produces.
 *
 * <p>An empty list means "no restriction": the issuer backend then advertises and accepts
 * everything it supports, which is what every issuer did before this policy existed. A null
 * required flag likewise leaves the issuer backend's own default in place. Narrowing a list
 * narrows both the published metadata and what the endpoints accept, so the two can never drift.
 *
 * <p>{@code requestAlgValues} has no direct metadata counterpart: {@code credential_request
 * _encryption} advertises decryption keys rather than algorithms, so this list selects which of
 * the issuer backend's keys are published, by their JWK {@code alg}.
 */
public record IssuerCredentialEncryption(
        List<String> requestAlgValues,
        List<String> requestEncValues,
        List<String> requestZipValues,
        List<String> responseAlgValues,
        List<String> responseEncValues,
        List<String> responseZipValues,
        Boolean requestEncryptionRequired,
        Boolean responseEncryptionRequired,
        List<CredentialEncryptionKey> requestKeys) {

    public IssuerCredentialEncryption {
        requestAlgValues = requestAlgValues == null ? List.of() : List.copyOf(requestAlgValues);
        requestEncValues = requestEncValues == null ? List.of() : List.copyOf(requestEncValues);
        requestZipValues = requestZipValues == null ? List.of() : List.copyOf(requestZipValues);
        responseAlgValues = responseAlgValues == null ? List.of() : List.copyOf(responseAlgValues);
        responseEncValues = responseEncValues == null ? List.of() : List.copyOf(responseEncValues);
        responseZipValues = responseZipValues == null ? List.of() : List.copyOf(responseZipValues);
        requestKeys = requestKeys == null ? List.of() : List.copyOf(requestKeys);
    }

    public IssuerCredentialEncryption(
            List<String> requestAlgValues,
            List<String> requestEncValues,
            List<String> requestZipValues,
            List<String> responseAlgValues,
            List<String> responseEncValues,
            List<String> responseZipValues,
            Boolean requestEncryptionRequired,
            Boolean responseEncryptionRequired) {
        this(requestAlgValues, requestEncValues, requestZipValues, responseAlgValues,
                responseEncValues, responseZipValues, requestEncryptionRequired,
                responseEncryptionRequired, List.of());
    }

    public static IssuerCredentialEncryption unrestricted() {
        return new IssuerCredentialEncryption(null, null, null, null, null, null, null, null, null);
    }

    /** Validates a selection against what the issuer backend can actually process. */
    public IssuerCredentialEncryption validated() {
        return new IssuerCredentialEncryption(
                CredentialEncryptionAlgorithms.requireSupported(
                        requestAlgValues, CredentialEncryptionAlgorithms.KEY_MANAGEMENT,
                        "Credential Request encryption alg values"),
                CredentialEncryptionAlgorithms.requireSupported(
                        requestEncValues, CredentialEncryptionAlgorithms.CONTENT_ENCRYPTION,
                        "Credential Request encryption enc values"),
                CredentialEncryptionAlgorithms.requireSupported(
                        requestZipValues, CredentialEncryptionAlgorithms.COMPRESSION,
                        "Credential Request encryption zip values"),
                CredentialEncryptionAlgorithms.requireSupported(
                        responseAlgValues, CredentialEncryptionAlgorithms.KEY_MANAGEMENT,
                        "Credential Response encryption alg values"),
                CredentialEncryptionAlgorithms.requireSupported(
                        responseEncValues, CredentialEncryptionAlgorithms.CONTENT_ENCRYPTION,
                        "Credential Response encryption enc values"),
                CredentialEncryptionAlgorithms.requireSupported(
                        responseZipValues, CredentialEncryptionAlgorithms.COMPRESSION,
                        "Credential Response encryption zip values"),
                requestEncryptionRequired,
                responseEncryptionRequired,
                List.of());
    }

    /** Adds public key-routing metadata to the internal issuer response. */
    public IssuerCredentialEncryption withRequestKeys(List<CredentialEncryptionKey> keys) {
        return new IssuerCredentialEncryption(
                requestAlgValues, requestEncValues, requestZipValues,
                responseAlgValues, responseEncValues, responseZipValues,
                requestEncryptionRequired, responseEncryptionRequired, keys);
    }
}
