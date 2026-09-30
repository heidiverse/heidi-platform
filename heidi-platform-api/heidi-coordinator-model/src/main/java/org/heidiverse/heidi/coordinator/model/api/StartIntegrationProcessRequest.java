// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.api;

import tools.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;

/** Backend-only request that activates an initialized process. */
public record StartIntegrationProcessRequest(
        @NotBlank String processToken, JsonNode clientConfiguration) {

    public StartIntegrationProcessRequest(String processToken) {
        this(processToken, null);
    }
}
