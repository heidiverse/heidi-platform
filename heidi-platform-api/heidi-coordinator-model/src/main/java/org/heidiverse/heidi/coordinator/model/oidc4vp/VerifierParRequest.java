// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.oidc4vp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.heidiverse.heidi.coordinator.model.converter.DcqlQueryDeserializer;
import org.heidiverse.heidi.coordinator.model.converter.DcqlQuerySerializer;
import org.heidiverse.heidi.shared.trustframework.TrustConfiguration;
import org.kapunsdk.presentation.request.model.OID4VPVersion;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import uniffi.kapun_dcql_rust.DcqlQuery;

import java.util.List;
import java.util.Map;

public record VerifierParRequest(
        @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("oid4vp_draft_version")
                OID4VPVersion OID4VPVersion,
        @JsonProperty(value = "nonce", required = true) String nonce,
        @JsonProperty(value = "client_id", required = true) String clientId,
        @JsonProperty(value = "response_mode", defaultValue = "direct_post.jwt")
                String responseMode,
        @JsonProperty(value = "redirect_uri", required = true) String redirectUri,
        @JsonInclude(JsonInclude.Include.NON_NULL)
                @JsonProperty(value = "dcql_query")
                @JsonDeserialize(using = DcqlQueryDeserializer.class)
                @JsonSerialize(using = DcqlQuerySerializer.class)
                DcqlQuery dcqlQuery,
        @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty(value = "scope") String scope,
        @JsonInclude(JsonInclude.Include.NON_NULL)
                @JsonProperty(value = "include_dcql_query") Boolean includeDcqlQuery,
        @JsonProperty(value = "transaction_data") List<String> transactionData,
        @JsonProperty(value = "validation_logic") String validationLogic,
        @JsonProperty(value = "validation_mode") String validationMode,
        @JsonProperty(value = "tenant_id") String tenantId,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) @JsonProperty(value = "client_metadata")
                Map<String, String> clientMetadata,
        @JsonProperty(value = "signing_identity") String signingIdentity,
        @JsonProperty(value = "signing_key_id") String signingKeyId,
        @JsonProperty(value = "signing_trust_system") String signingTrustSystem,
        @JsonProperty(value = "client_id_scheme") String clientIdScheme,
        @JsonInclude(JsonInclude.Include.NON_NULL)
                @JsonProperty(value = "trust_configuration") TrustConfiguration trustConfiguration,
        @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty(value = "verifier_info")
                List<VerifierAttestation> verifierAttestations,
        @JsonProperty(value = "store_vp_token", defaultValue = "false") boolean storeVpToken,
        @JsonProperty(value = "presentation_profile_id") String presentationProfileId) {}
