// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IssuerMetadata(
        @JsonProperty("credential_issuer") String credentialIssuer,
        @JsonProperty("authorization_servers") List<String> authorizationServers,
        @JsonProperty("credential_endpoint") String credentialEndpoint,
        @JsonProperty("deferred_credential_endpoint") String deferredCredentialEndpoint,
        @JsonProperty("nonce_endpoint") String nonceEndpoint,
        @JsonProperty("credential_request_encryption")
                CredentialRequestEncryption credentialRequestEncryption,
        @JsonProperty("credential_response_encryption")
                CredentialResponseEncryption credentialResponseEncryption,
        @JsonProperty("credential_configurations_supported")
                Map<String, CredentialConfiguration> credentialConfigurationsSupported,
        @JsonProperty("batch_credential_issuance") BatchCredentialIssuance batchCredentialIssuance,
        @JsonProperty("credential_issuer_identity_trust_statement")
                String credentialIssuerIdentityTrustStatement,
        @JsonProperty("profile_version") String profileVersion) {

    public IssuerMetadata(
            String credentialIssuer,
            List<String> authorizationServers,
            String credentialEndpoint,
            String deferredCredentialEndpoint,
            String nonceEndpoint,
            CredentialRequestEncryption credentialRequestEncryption,
            CredentialResponseEncryption credentialResponseEncryption,
            Map<String, CredentialConfiguration> credentialConfigurationsSupported,
            BatchCredentialIssuance batchCredentialIssuance,
            String credentialIssuerIdentityTrustStatement) {
        this(credentialIssuer, authorizationServers, credentialEndpoint, deferredCredentialEndpoint,
                nonceEndpoint, credentialRequestEncryption, credentialResponseEncryption,
                credentialConfigurationsSupported, batchCredentialIssuance,
                credentialIssuerIdentityTrustStatement, null);
    }

    public IssuerMetadata withTrustStatements(
            String identityStatement, Map<String, CredentialConfiguration> configurations) {
        return new IssuerMetadata(
                credentialIssuer,
                authorizationServers,
                credentialEndpoint,
                deferredCredentialEndpoint,
                nonceEndpoint,
                credentialRequestEncryption,
                credentialResponseEncryption,
                configurations,
                batchCredentialIssuance,
                identityStatement,
                profileVersion);
    }

    public record JwkSet(List<Map<String, Object>> keys) {}

    public record CredentialRequestEncryption(
            JwkSet jwks,
            @JsonProperty("enc_values_supported") List<String> encValuesSupported,
            @JsonProperty("zip_values_supported") List<String> zipValuesSupported,
            @JsonProperty("encryption_required") boolean encryptionRequired) {}

    public record CredentialResponseEncryption(
            @JsonProperty("alg_values_supported") List<String> algValuesSupported,
            @JsonProperty("enc_values_supported") List<String> encValuesSupported,
            @JsonProperty("zip_values_supported") List<String> zipValuesSupported,
            @JsonProperty("encryption_required") boolean encryptionRequired) {}

    public record BatchCredentialIssuance(@JsonProperty("batch_size") int batchSize) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CredentialConfiguration(
            String format,
            String vct,
            String doctype,
            @JsonProperty("credential_definition") CredentialDefinition credentialDefinition,
            @JsonProperty("cryptographic_binding_methods_supported")
                    List<String> cryptographicBindingMethodsSupported,
            @JsonProperty("credential_signing_alg_values_supported")
                    List<String> credentialSigningAlgValuesSupported,
            @JsonProperty("proof_types_supported") ProofTypesSupported proofTypesSupported,
            @JsonProperty("credential_metadata") CredentialMetadata credentialMetadata,
            @JsonProperty("protected_issuance_authorization_trust_statement")
                    String protectedIssuanceAuthorizationTrustStatement,
            @JsonProperty("vct_metadata_uri") String vctMetadataUri,
            @JsonProperty("credential_refresh_disabled") Boolean credentialRefreshDisabled) {

        public CredentialConfiguration(
                String format,
                String vct,
                String doctype,
                CredentialDefinition credentialDefinition,
                List<String> cryptographicBindingMethodsSupported,
                List<String> credentialSigningAlgValuesSupported,
                ProofTypesSupported proofTypesSupported,
                    CredentialMetadata credentialMetadata,
                    String protectedIssuanceAuthorizationTrustStatement) {
            this(
                    format,
                    vct,
                    doctype,
                    credentialDefinition,
                    cryptographicBindingMethodsSupported,
                    credentialSigningAlgValuesSupported,
                    proofTypesSupported,
                    credentialMetadata,
                    protectedIssuanceAuthorizationTrustStatement,
                    null,
                    null);
        }

        public CredentialConfiguration(
                String format,
                String vct,
                String doctype,
                CredentialDefinition credentialDefinition,
                List<String> cryptographicBindingMethodsSupported,
                List<String> credentialSigningAlgValuesSupported,
                ProofTypesSupported proofTypesSupported,
                CredentialMetadata credentialMetadata,
                String protectedIssuanceAuthorizationTrustStatement,
                String vctMetadataUri) {
            this(
                    format,
                    vct,
                    doctype,
                    credentialDefinition,
                    cryptographicBindingMethodsSupported,
                    credentialSigningAlgValuesSupported,
                    proofTypesSupported,
                    credentialMetadata,
                    protectedIssuanceAuthorizationTrustStatement,
                    vctMetadataUri,
                    null);
        }

        public CredentialConfiguration withCredentialMetadata(CredentialMetadata metadata) {
            return new CredentialConfiguration(
                    format,
                    vct,
                    doctype,
                    credentialDefinition,
                    cryptographicBindingMethodsSupported,
                    credentialSigningAlgValuesSupported,
                    proofTypesSupported,
                    metadata,
                    protectedIssuanceAuthorizationTrustStatement,
                    vctMetadataUri,
                    credentialRefreshDisabled);
        }

        public CredentialConfiguration withProtectedIssuanceStatement(String statement) {
            return new CredentialConfiguration(
                    format,
                    vct,
                    doctype,
                    credentialDefinition,
                    cryptographicBindingMethodsSupported,
                    credentialSigningAlgValuesSupported,
                    proofTypesSupported,
                    credentialMetadata,
                    statement,
                    vctMetadataUri,
                    credentialRefreshDisabled);
        }

        public CredentialConfiguration withCredentialRefreshDisabled(boolean disabled) {
            return new CredentialConfiguration(
                    format,
                    vct,
                    doctype,
                    credentialDefinition,
                    cryptographicBindingMethodsSupported,
                    credentialSigningAlgValuesSupported,
                    proofTypesSupported,
                    credentialMetadata,
                    protectedIssuanceAuthorizationTrustStatement,
                    vctMetadataUri,
                    disabled);
        }
    }

    public record CredentialDefinition(List<String> type) {}

    public record ProofTypesSupported(JwtProofType jwt) {}

    public record JwtProofType(
            @JsonProperty("proof_signing_alg_values_supported")
                    List<String> proofSigningAlgValuesSupported) {}

    public record CredentialMetadata(List<CredentialDisplay> display) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CredentialDisplay(
            String name,
            String locale,
            @JsonProperty("background_color") String backgroundColor,
            @JsonProperty("background_image") BackgroundImage backgroundImage,
            @JsonProperty("text_color") String textColor) {}

    public record BackgroundImage(String uri) {}
}
