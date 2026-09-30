// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.heidiverse.heidi.coordinator.model.connection.ConnectionStateV2;

import tools.jackson.databind.JsonNode;

import java.util.UUID;

public record IntegrationProcessResult(
        UUID processId,
        ConnectionStateV2 connectionState,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode vpToken) {}
