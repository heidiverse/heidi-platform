// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CredentialResponse(
        @JsonProperty("c_nonce") String cNonce,
        @JsonProperty("c_nonce_expires_in") Integer cNonceExpiresIn,
        String credential,
        List<IssuedCredential> credentials,
        @JsonProperty("transaction_id") String transactionId,
        Long interval) {

    public static CredentialResponse single(String nonce, String credential) {
        return new CredentialResponse(nonce, 300, credential, null, null, null);
    }

    public static CredentialResponse batch(String nonce, List<String> credentials) {
        return new CredentialResponse(
                nonce,
                300,
                null,
                credentials.stream().map(IssuedCredential::new).toList(),
                null,
                null);
    }

    public static CredentialResponse deferred(String transactionId, long interval) {
        return new CredentialResponse(null, null, null, null, transactionId, interval);
    }

    public record IssuedCredential(String credential) {}
}
