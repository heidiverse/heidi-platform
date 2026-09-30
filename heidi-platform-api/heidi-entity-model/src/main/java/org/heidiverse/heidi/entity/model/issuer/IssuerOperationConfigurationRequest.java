// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import tools.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Generic profile configuration for one issuer identity operation. */
public record IssuerOperationConfigurationRequest(
        @NotNull JsonNode configuration,
        @Positive Integer schemaVersion) {}
