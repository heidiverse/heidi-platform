// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.credentialscheme;

import org.heidiverse.heidi.entity.model.issuer.IssuerSettings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CredentialSchemeDetail(
        UUID id, // Generated when a new scheme is created; the update path uses its URL id.
        @NotNull String credentialIdentifier,
        String version,
        String displayName,
        List<@NotNull @Valid CredentialSchemeAttribute> attributes,
        List<@NotNull @Valid CredentialSchemeStylePayload> credentialSchemeStylePayloads,
        @NotNull @Valid CredentialSchemeMetadata metadata,
        @NotNull @Valid IssuerSettings issuerSettings,
        Integer maxBatchSize,
        UUID templateId // Optional field
        ) {}
