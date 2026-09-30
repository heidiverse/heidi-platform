// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.api;

import org.heidiverse.heidi.coordinator.model.ProofSchemeResponse;
import org.heidiverse.heidi.coordinator.model.connection.ConnectionStateV2;

import tools.jackson.databind.JsonNode;

public record ClientInteractionResponse(
        String action,
        boolean useDcApi,
        ClientProcessData crossDevice,
        ClientProcessData sameDevice,
        ConnectionStateV2.ConnectionStateEnum state,
        DisplayClaims displayClaims,
        JsonNode clientConfiguration) {

    public record ClientProcessData(String qrCodeDataPath, String qrCodeDataScheme) {}

    public record DisplayClaims(ProofSchemeResponse schema, JsonNode claims) {}
}
