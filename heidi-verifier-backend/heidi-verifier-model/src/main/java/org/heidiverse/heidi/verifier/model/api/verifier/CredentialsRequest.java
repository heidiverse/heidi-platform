// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.api.verifier;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.heidiverse.heidi.verifier.model.converter.DcqlQueryDeserializer;
import org.heidiverse.heidi.verifier.model.converter.DcqlQuerySerializer;
import org.heidiverse.heidi.shared.trustframework.TrustConfiguration;
import org.heidiverse.heidi.verifier.model.validation.ValidationMode;
import org.heidiverse.heidi.verifier.model.vp.VerifierAttestation;
import org.kapunsdk.presentation.request.model.OID4VPVersion;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import uniffi.kapun_dcql_rust.DcqlQuery;

import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CredentialsRequest(
        @JsonProperty(value = "nonce", required = true) String nonce,
        @JsonProperty(value = "client_id", required = true) String clientId,
        @JsonProperty(value = "response_mode", defaultValue = "direct_post.jwt")
                String responseMode,
        @JsonProperty(value = "redirect_uri", required = true) String redirectUri,
        @JsonProperty(value = "oid4vp_draft_version", defaultValue = "DRAFT_28")
                OID4VPVersion OID4VPVersion,
        @JsonProperty("dcql_query")
                @JsonDeserialize(using = DcqlQueryDeserializer.class)
                @JsonSerialize(using = DcqlQuerySerializer.class)
                DcqlQuery dcqlQuery,
        @JsonProperty("scope") String scope,
        @JsonProperty("include_dcql_query") Boolean includeDcqlQuery,
        @JsonProperty("transaction_data") List<String> transactionData,
        @JsonProperty("validation_logic") String validationLogic,
        @JsonProperty("validation_mode") ValidationMode validationMode,
        @JsonProperty(value = "tenant_id") String tenantId,
        @JsonProperty(value = "client_metadata") Map<String, String> clientMetadata,
        @JsonProperty(value = "signing_identity") String signingIdentity,
        @JsonProperty(value = "signing_key_id") String signingKeyId,
        @JsonProperty(value = "signing_trust_system") String signingTrustSystem,
        @JsonProperty(value = "client_id_scheme") String clientIdScheme,
        @JsonProperty(value = "trust_configuration") TrustConfiguration trustConfiguration,
        @JsonAlias("verifier_attestations") @JsonProperty(value = "verifier_info")
                List<VerifierAttestation> verifierAttestations,
        @JsonProperty(value = "store_vp_token", defaultValue = "false") Boolean storeVpToken,
        @NotBlank(message = "Presentation profile is required")
                @JsonProperty(value = "presentation_profile_id") String presentationProfileId) {

    public CredentialsRequest(
            String nonce,
            String clientId,
            String responseMode,
            String redirectUri,
            OID4VPVersion OID4VPVersion,
            DcqlQuery dcqlQuery,
            String scope,
            List<String> transactionData,
            String validationLogic,
            ValidationMode validationMode,
            String tenantId,
            Map<String, String> clientMetadata,
            String signingIdentity,
            String signingKeyId,
            String signingTrustSystem,
            String clientIdScheme,
            TrustConfiguration trustConfiguration,
            List<VerifierAttestation> verifierAttestations,
            Boolean storeVpToken,
            String presentationProfileId) {
        this(nonce, clientId, responseMode, redirectUri, OID4VPVersion, dcqlQuery, scope, false,
                transactionData, validationLogic, validationMode, tenantId, clientMetadata,
                signingIdentity, signingKeyId, signingTrustSystem, clientIdScheme,
                trustConfiguration,
                verifierAttestations, storeVpToken, presentationProfileId);
    }

    public CredentialsRequest {
        if (OID4VPVersion == null) {
            OID4VPVersion = OID4VPVersion.DRAFT_28;
        }
        if (validationMode == null) {
            validationMode = ValidationMode.DISABLED;
        }
        if (storeVpToken == null) {
            storeVpToken = false;
        }
        if (includeDcqlQuery == null) {
            includeDcqlQuery = false;
        }
    }
}
