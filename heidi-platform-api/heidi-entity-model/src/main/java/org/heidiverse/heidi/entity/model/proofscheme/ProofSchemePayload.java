// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.proofscheme;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.heidiverse.heidi.entity.model.credentialscheme.CredentialScheme;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;

import java.util.List;

public record ProofSchemePayload(
        @NotNull String title,
        @NotNull String purpose,
        @NotBlank(message = "Presentation profile is required") String presentationProfileId,
        IssuerTrustSystem verifierTrustSystem,
        String validationLogic,
        ValidationMode validationMode,
        String redirectUri,
        Integer verifierIdentityId,
        String verifierSigningKeyId,
        Integer proofSigningProviderId,
        VerifierClientIdScheme verifierClientIdScheme,
        String registrationCertificate,
        String swissIdentityStatement,
        String swissVerificationQueryStatement,
        Boolean swissVerificationQueryEnabled,
        Boolean alwaysIncludeDcqlQuery,
        List<String> swissProtectedVerificationStatements,
        List<@Valid TrustedAuthorityQuery> trustedAuthorities,
        List<@Valid VerifierInfo> verifierInfos,
        @NotEmpty(message = "At least one credential schema is required")
                List<@Valid CredentialScheme> credentialSchemes) {
    public ProofSchemePayload(
            String title,
            String purpose,
            String presentationProfileId,
            String validationLogic,
            ValidationMode validationMode,
            String redirectUri,
            Integer verifierIdentityId,
            String verifierSigningKeyId,
            Integer proofSigningProviderId,
            VerifierClientIdScheme verifierClientIdScheme,
            String registrationCertificate,
            String swissIdentityStatement,
            String swissVerificationQueryStatement,
            List<String> swissProtectedVerificationStatements,
            List<TrustedAuthorityQuery> trustedAuthorities,
            List<@Valid CredentialScheme> credentialSchemes) {
        this(title, purpose, presentationProfileId, null, validationLogic, validationMode,
                redirectUri, verifierIdentityId, verifierSigningKeyId, proofSigningProviderId,
                verifierClientIdScheme, registrationCertificate, swissIdentityStatement,
                swissVerificationQueryStatement, null, null,
                swissProtectedVerificationStatements,
                trustedAuthorities, List.of(), credentialSchemes);
    }

    /** Backward-compatible constructor retaining the explicit verifier trust system. */
    public ProofSchemePayload(
            String title,
            String purpose,
            String presentationProfileId,
            IssuerTrustSystem verifierTrustSystem,
            String validationLogic,
            ValidationMode validationMode,
            String redirectUri,
            Integer verifierIdentityId,
            String verifierSigningKeyId,
            Integer proofSigningProviderId,
            VerifierClientIdScheme verifierClientIdScheme,
            String registrationCertificate,
            String swissIdentityStatement,
            String swissVerificationQueryStatement,
            List<String> swissProtectedVerificationStatements,
            List<TrustedAuthorityQuery> trustedAuthorities,
            List<@Valid CredentialScheme> credentialSchemes) {
        this(title, purpose, presentationProfileId, verifierTrustSystem, validationLogic,
                validationMode, redirectUri, verifierIdentityId, verifierSigningKeyId,
                proofSigningProviderId, verifierClientIdScheme, registrationCertificate,
                swissIdentityStatement, swissVerificationQueryStatement,
                null, null, swissProtectedVerificationStatements,
                trustedAuthorities, List.of(),
                credentialSchemes);
    }

    @JsonIgnore
    public ValidationMode effectiveValidationMode() {
        return validationMode == null ? ValidationMode.DISABLED : validationMode;
    }

    public boolean effectiveSwissVerificationQueryEnabled() {
        return swissVerificationQueryEnabled == null || swissVerificationQueryEnabled;
    }

    public boolean effectiveAlwaysIncludeDcqlQuery() {
        return Boolean.TRUE.equals(alwaysIncludeDcqlQuery);
    }

    @AssertTrue(message = "Validation logic is required when Possum validation is enabled")
    @JsonIgnore
    public boolean isValidationConfigurationValid() {
        return effectiveValidationMode() == ValidationMode.DISABLED
                || (validationLogic != null && !validationLogic.isBlank());
    }
}
