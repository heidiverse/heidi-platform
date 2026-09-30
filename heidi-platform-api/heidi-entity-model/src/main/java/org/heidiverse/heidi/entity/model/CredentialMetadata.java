// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model;

import jakarta.validation.constraints.NotNull;

public record CredentialMetadata(
        @NotNull String credentialIdentifier, // e.g. "Mitarbeiterausweis"
        @NotNull String version // e.g. "1.0.0"
        ) {}
