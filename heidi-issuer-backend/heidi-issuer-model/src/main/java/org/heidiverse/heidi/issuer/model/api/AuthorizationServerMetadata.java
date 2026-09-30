// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthorizationServerMetadata(
        String issuer,
        @JsonProperty("authorization_endpoint") String authorizationEndpoint,
        @JsonProperty("pushed_authorization_request_endpoint")
                String pushedAuthorizationRequestEndpoint,
        @JsonProperty("token_endpoint") String tokenEndpoint,
        @JsonProperty("response_types_supported") List<String> responseTypesSupported,
        @JsonProperty("grant_types_supported") List<String> grantTypesSupported,
        @JsonProperty("code_challenge_methods_supported") List<String> codeChallengeMethodsSupported,
        @JsonProperty("token_endpoint_auth_methods_supported")
                List<String> tokenEndpointAuthMethodsSupported,
        @JsonProperty("authorization_details_types_supported")
                List<String> authorizationDetailsTypesSupported,
        @JsonProperty("dpop_signing_alg_values_supported")
                List<String> dpopSigningAlgValuesSupported) {

    /** Constructor for the OSS pre-authorized-code-only authorization server metadata. */
    public AuthorizationServerMetadata(
            String issuer,
            String tokenEndpoint,
            List<String> grantTypesSupported,
            List<String> tokenEndpointAuthMethodsSupported,
            List<String> authorizationDetailsTypesSupported,
            List<String> dpopSigningAlgValuesSupported) {
        this(
                issuer,
                null,
                null,
                tokenEndpoint,
                null,
                grantTypesSupported,
                null,
                tokenEndpointAuthMethodsSupported,
                authorizationDetailsTypesSupported,
                dpopSigningAlgValuesSupported);
    }
}
