// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.credentialscheme;

import org.heidiverse.heidi.entity.model.issuer.IssuerSettings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CredentialSchemeOverviewEntry(
        @NotNull UUID id,
        @NotNull String credentialIdentifier,
        String version,
        String displayName,
        @NotNull Instant createdOn,
        Instant updatedAt,
        @NotNull CredentialSchemeState state,
        @NotNull IssuerSettings issuerSettings,
        List<@Valid CredentialSchemeStyleDetail> credentialSchemeStyleDetails,
        String tenantId) {}
