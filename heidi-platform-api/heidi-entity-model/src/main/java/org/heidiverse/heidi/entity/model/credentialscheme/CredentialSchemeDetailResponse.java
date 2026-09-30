// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.credentialscheme;

import org.heidiverse.heidi.entity.model.issuer.IssuerSettings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CredentialSchemeDetailResponse(
        @NotNull UUID id, // scheme exists already!
        @NotNull String credentialIdentifier,
        String version,
        String displayName,
        @NotNull CredentialSchemeState state,
        List<@NotNull @Valid CredentialSchemeAttribute> attributes,
        List<@NotNull @Valid CredentialSchemeStyleDetail> credentialSchemeStyleDetails,
        @NotNull @Valid CredentialSchemeMetadata schemeMetadata,
        @NotNull @Valid IssuerSettings issuerSettings,
        String tenantId,
        int maxBatchSize,
        UUID templateId) {}
