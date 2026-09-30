// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

class ClientInteractionTokenServiceTest {

    private final ClientInteractionTokenService service =
            new ClientInteractionTokenService("test-signing-key".getBytes(StandardCharsets.UTF_8));

    @Test
    void generatesOpaqueHighEntropyClientToken() {
        String first = service.generate();
        String second = service.generate();

        assertTrue(first.startsWith("cit_"));
        assertEquals(43, first.substring("cit_".length()).length());
        assertNotEquals(first, second);
        assertTrue(service.isClientInteractionToken(first));
    }

    @Test
    void hashesTokenForPersistenceInsteadOfStoringIt() {
        String token = service.generate();

        String hash = service.hash(token);

        assertNotEquals(token, hash);
        assertEquals(hash, service.hash(token));
    }

    @Test
    void derivesTheSameClientTokenForRetriesOfOneProcess() {
        UUID processId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        String first = service.generate(processId);
        String second = service.generate(processId);

        assertEquals(first, second);
        assertTrue(first.startsWith("cit_"));
        assertEquals(43, first.substring("cit_".length()).length());
        assertNotEquals(first, service.generate(UUID.randomUUID()));
    }
}
