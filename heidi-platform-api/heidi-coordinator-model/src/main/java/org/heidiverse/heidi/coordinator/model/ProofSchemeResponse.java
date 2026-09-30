// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.heidiverse.heidi.coordinator.model.oidc4vp.VerifierAttestation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProofSchemeResponse(
        String uuid,
        String title,
        String purpose,
        String validationLogic,
        String validationMode,
        Instant createdAt,
        Instant updatedAt,
        String redirectUri, // Nullable
        String tenantId,
        IssuerDefinition verifierIdentity,
        boolean verifierIdentityOverridden,
        String verifierSigningKeyId,
        Integer proofSigningProviderId,
        String verifierTrustSystem,
        String verifierClientIdScheme,
        boolean verifierClientIdSchemeOverridden,
        List<CredentialScheme> credentialSchemes,
        String registrationCertificate,
        String swissIdentityStatement,
        String swissVerificationQueryStatement,
        boolean swissVerificationQueryEnabled,
        boolean alwaysIncludeDcqlQuery,
        List<String> swissProtectedVerificationStatements,
        List<String> eudiVerificationTrustAnchors,
        String swissVerificationTrustAnchor,
        String swissTrustRegistryBaseUrl,
        List<TrustedAuthorityQuery> trustedAuthorities,
        List<VerifierAttestation> verifierInfos,
        String presentationProfileId) {

    public ProofSchemeResponse(
            String uuid,
            String title,
            String purpose,
            String validationLogic,
            String validationMode,
            Instant createdAt,
            Instant updatedAt,
            String redirectUri,
            String tenantId,
            IssuerDefinition verifierIdentity,
            boolean verifierIdentityOverridden,
            String verifierSigningKeyId,
            Integer proofSigningProviderId,
            String verifierTrustSystem,
            String verifierClientIdScheme,
            boolean verifierClientIdSchemeOverridden,
            List<CredentialScheme> credentialSchemes,
            String registrationCertificate,
            String swissIdentityStatement,
            String swissVerificationQueryStatement,
            List<String> swissProtectedVerificationStatements,
            List<String> eudiVerificationTrustAnchors,
            String swissVerificationTrustAnchor,
            String swissTrustRegistryBaseUrl,
            List<TrustedAuthorityQuery> trustedAuthorities,
            String presentationProfileId) {
        this(uuid, title, purpose, validationLogic, validationMode, createdAt, updatedAt,
                redirectUri, tenantId, verifierIdentity, verifierIdentityOverridden,
                verifierSigningKeyId, proofSigningProviderId, verifierTrustSystem,
                verifierClientIdScheme, verifierClientIdSchemeOverridden, credentialSchemes,
                registrationCertificate, swissIdentityStatement, swissVerificationQueryStatement,
                true, false,
                swissProtectedVerificationStatements, eudiVerificationTrustAnchors,
                swissVerificationTrustAnchor, swissTrustRegistryBaseUrl, trustedAuthorities,
                List.of(), presentationProfileId);
    }

    /** Backward-compatible constructor retaining explicit verifier infos. */
    public ProofSchemeResponse(
            String uuid,
            String title,
            String purpose,
            String validationLogic,
            String validationMode,
            Instant createdAt,
            Instant updatedAt,
            String redirectUri,
            String tenantId,
            IssuerDefinition verifierIdentity,
            boolean verifierIdentityOverridden,
            String verifierSigningKeyId,
            Integer proofSigningProviderId,
            String verifierTrustSystem,
            String verifierClientIdScheme,
            boolean verifierClientIdSchemeOverridden,
            List<CredentialScheme> credentialSchemes,
            String registrationCertificate,
            String swissIdentityStatement,
            String swissVerificationQueryStatement,
            List<String> swissProtectedVerificationStatements,
            List<String> eudiVerificationTrustAnchors,
            String swissVerificationTrustAnchor,
            String swissTrustRegistryBaseUrl,
            List<TrustedAuthorityQuery> trustedAuthorities,
            List<VerifierAttestation> verifierInfos,
            String presentationProfileId) {
        this(uuid, title, purpose, validationLogic, validationMode, createdAt, updatedAt,
                redirectUri, tenantId, verifierIdentity, verifierIdentityOverridden,
                verifierSigningKeyId, proofSigningProviderId, verifierTrustSystem,
                verifierClientIdScheme, verifierClientIdSchemeOverridden, credentialSchemes,
                registrationCertificate, swissIdentityStatement, swissVerificationQueryStatement,
                true, false, swissProtectedVerificationStatements, eudiVerificationTrustAnchors,
                swissVerificationTrustAnchor, swissTrustRegistryBaseUrl, trustedAuthorities,
                verifierInfos, presentationProfileId);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TrustedAuthorityQuery(String type, List<String> values, String credentialId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CredentialScheme(
            String id,
            String credentialIdentifier,
            String version,
            String displayName,
            IssuerDefinition issuerDefinition, // Optional
            List<Attribute> attributes,
            IssuerSettings issuerSettings) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IssuerDefinition(
            int id, String logo, String keyType, String slug, DisplayNames displayName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IssuerSettings(
            int id,
            String issuerKeyType,
            String doctype,
            String namespace,
            String vct,
            Set<String> supportedCredentialTypes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DisplayNames(String fr, String en, String de, String it) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attribute(
            int id,
            String name,
            AttributeType type,
            Map<String, String> displayName, // Locale-specific display names
            Map<String, String> attributeNameOverrides) {}
}
