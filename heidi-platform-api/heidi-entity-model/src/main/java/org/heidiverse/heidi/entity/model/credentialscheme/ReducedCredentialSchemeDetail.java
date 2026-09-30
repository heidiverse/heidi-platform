// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.credentialscheme;

import org.heidiverse.heidi.entity.model.issuer.IssuerDefinition;
import org.heidiverse.heidi.entity.model.issuer.IssuerSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReducedCredentialSchemeDetail(
        @NotNull UUID id, // scheme exists already!
        @NotNull String credentialIdentifier,
        String version,
        String displayName,
        @NotNull IssuerDefinition issuerDefinition,
        List<@NotNull @Valid CredentialSchemeAttribute> attributes,
        @NotNull @Valid IssuerSettings issuerSettings) {}
