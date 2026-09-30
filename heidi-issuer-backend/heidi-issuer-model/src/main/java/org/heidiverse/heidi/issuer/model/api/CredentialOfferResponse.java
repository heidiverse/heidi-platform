// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CredentialOfferResponse(
        @JsonProperty("credential_issuer") String credentialIssuer,
        @JsonProperty("credential_configuration_ids") List<String> credentialConfigurationIds,
        Grants grants,
        @JsonProperty("connection_id") String connectionId,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("credential_offer_uri") String credentialOfferUri) {

    public CredentialOfferResponse(
            String credentialIssuer,
            List<String> credentialConfigurationIds,
            Grants grants,
            String connectionId) {
        this(credentialIssuer, credentialConfigurationIds, grants, connectionId, null);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Grants(
            @JsonProperty("urn:ietf:params:oauth:grant-type:pre-authorized_code")
                    PreAuthorizedCodeGrant preAuthorizedCode,
            @JsonProperty("authorization_code") AuthorizationCodeGrant authorizationCode) {

        public Grants(PreAuthorizedCodeGrant preAuthorizedCode) {
            this(preAuthorizedCode, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PreAuthorizedCodeGrant(
            @JsonProperty("pre-authorized_code") String preAuthorizedCode,
            @JsonProperty("tx_code") TransactionCode transactionCode) {}

    public record TransactionCode(
            @JsonProperty("input_mode") String inputMode, int length, String description) {}

    public record AuthorizationCodeGrant(@JsonProperty("issuer_state") String issuerState) {}
}
