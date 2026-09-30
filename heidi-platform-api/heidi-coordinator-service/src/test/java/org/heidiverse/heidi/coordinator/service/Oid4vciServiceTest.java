// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

class Oid4vciServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Oid4vciService service =
            new Oid4vciService("https://issuer.example", "", objectMapper);

    @Test
    void requiresExplicitConnectionId() throws Exception {
        var offer = objectMapper.readTree(
                "{\"credential_issuer\":\"https://issuer.example/legacy-connection\"}");

        var exception = assertThrows(
                ResponseStatusException.class, () -> service.extractConnectionId(offer));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertEquals("Issuer response is missing connection_id", exception.getReason());
    }
}
