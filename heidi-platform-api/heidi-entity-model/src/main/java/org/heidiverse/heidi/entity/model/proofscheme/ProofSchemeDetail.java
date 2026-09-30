// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.proofscheme;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.heidiverse.heidi.entity.model.credentialscheme.ReducedCredentialSchemeDetail;
import org.heidiverse.heidi.entity.model.issuer.IssuerDefinition;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProofSchemeDetail(
        @NotNull UUID uuid,
        @NotNull String title,
        @NotNull String purpose,
        @NotBlank(message = "Presentation profile is required") String presentationProfileId,
        String validationLogic,
        @NotNull ValidationMode validationMode,
        String redirectUri,
        String tenantId,
        IssuerDefinition verifierIdentity,
        boolean verifierIdentityOverridden,
        String verifierSigningKeyId,
        Integer proofSigningProviderId,
        IssuerTrustSystem verifierTrustSystem,
        VerifierClientIdScheme verifierClientIdScheme,
        boolean verifierClientIdSchemeOverridden,
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
        List<VerifierInfo> verifierInfos,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt,
        @NotNull List<@Valid ReducedCredentialSchemeDetail> credentialSchemes) {}
