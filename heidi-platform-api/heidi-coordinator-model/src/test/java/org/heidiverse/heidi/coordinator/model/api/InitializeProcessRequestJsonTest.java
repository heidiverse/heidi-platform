// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class InitializeProcessRequestJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void retainsUnknownPayloadsForProcessActionExtensions() throws Exception {
        var request =
                objectMapper.readValue(
                        "{\"action\":\"hosted_action\",\"privatePayload\":{\"value\":\"kept\"}}",
                        InitializeProcessRequest.class);

        assertEquals("hosted_action", request.action());
        assertEquals("kept", request.extensionData().get("privatePayload").get("value").asString());
    }

    @Test
    void deserializesVpTokenOptInAsAProcessProperty() throws Exception {
        var request =
                objectMapper.readValue(
                        "{\"action\":\"PRESENTATION\",\"includeVpToken\":true,\"clientDisplayClaims\":[\"/credential/name\"]}",
                        InitializeProcessRequest.class);

        assertTrue(request.includeVpToken());
        assertEquals(java.util.List.of("/credential/name"), request.clientDisplayClaims());
        assertFalse(request.extensionData().containsKey("includeVpToken"));
        assertFalse(request.extensionData().containsKey("clientDisplayClaims"));
    }

    @Test
    void deserializesClientConfigurationAsAProcessProperty() throws Exception {
        var request =
                objectMapper.readValue(
                        "{\"action\":\"PRESENTATION\",\"clientConfiguration\":{\"wallet\":{\"defaultWallet\":\"heidi\"}}}",
                        InitializeProcessRequest.class);

        assertEquals(
                "heidi",
                request.clientConfiguration().get("wallet").get("defaultWallet").asString());
        assertFalse(request.extensionData().containsKey("clientConfiguration"));
    }

    @Test
    void treatsNullUseDcApiAsFalse() throws Exception {
        var request =
                objectMapper.readValue(
                        "{\"action\":\"PRESENTATION\",\"presentationData\":{\"proofSchemeId\":\"proof-scheme\",\"useDcApi\":null}}",
                        InitializeProcessRequest.class);

        assertFalse(request.presentationData().useDcApi());
    }
}
