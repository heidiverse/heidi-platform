// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import org.heidiverse.heidi.shared.localized.LocalizedValue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record IssuerDefinition(
        @NotNull Integer id,
        @NotNull String logo,
        @NotNull String slug,
        @Valid LocalizedValue<@NotNull String> displayName) {}
