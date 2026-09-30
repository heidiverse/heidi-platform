// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.heidiverse.heidi.coordinator.model.oid4vci.SignedData;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Map;

class SignedDataJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void preservesExpiryWhenSignedDataIsSerializedAndReadBack() throws Exception {
        ZonedDateTime issuedAt = ZonedDateTime.parse("2026-08-26T13:00:00Z");
        ZonedDateTime expiresAt = issuedAt.plusMinutes(5);
        SignedData signedData =
                new SignedData(issuedAt, expiresAt, Map.of("action", "PRESENTATION"));

        String serialized = objectMapper.writeValueAsString(signedData);
        SignedData roundTripped = objectMapper.readValue(serialized, SignedData.class);

        assertTrue(serialized.contains("\"expiresAt\""));
        assertEquals(expiresAt, roundTripped.expiresAt());
    }
}
