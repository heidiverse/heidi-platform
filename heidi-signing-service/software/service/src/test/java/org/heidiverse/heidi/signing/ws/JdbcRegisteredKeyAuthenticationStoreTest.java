// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.ws;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcRegisteredKeyAuthenticationStoreTest {
    @Test
    void credentialsAndReplayStateSurviveStoreRestart() throws Exception {
        var database = Files.createTempFile("heidi-signing-auth", ".db");
        try {
            var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:" + database));
            jdbc.execute(
                    """
                    CREATE TABLE t_signing_auth_registration
                    (
                        registration_id TEXT PRIMARY KEY,
                        encrypted_psk TEXT NOT NULL,
                        public_key TEXT,
                        client_name TEXT,
                        last_used_at TEXT,
                        created_at TEXT
                    )
                    """);
            jdbc.execute(
                    """
                    CREATE TABLE t_signing_auth_replay
                    (
                        signature TEXT PRIMARY KEY,
                        seen_at INTEGER NOT NULL
                    )
                    """);

            var psk = new byte[] {1, 2, 3, 4};
            var publicKey = new byte[] {5, 6, 7};
            var masterKey = "05".repeat(32);
            var store = new JdbcRegisteredKeyAuthenticationStore(jdbc, masterKey);
            store.create("registration-1", psk, "issuer");
            assertNotEquals(
                    java.util.Base64.getEncoder().encodeToString(psk),
                    jdbc.queryForObject(
                            "SELECT encrypted_psk FROM t_signing_auth_registration",
                            String.class));
            assertTrue(store.setPublicKeyIfAbsent("registration-1", publicKey));
            assertFalse(store.setPublicKeyIfAbsent("registration-1", new byte[] {8}));
            assertTrue(store.recordSignatureIfAbsent("signature-1", 10));
            assertFalse(store.recordSignatureIfAbsent("signature-1", 11));

            var restarted = new JdbcRegisteredKeyAuthenticationStore(jdbc, masterKey);
            var registration = restarted.find("registration-1").orElseThrow();
            assertArrayEquals(psk, registration.psk());
            assertArrayEquals(publicKey, registration.publicKey());
            assertEquals("issuer", registration.client());
            assertFalse(restarted.recordSignatureIfAbsent("signature-1", 12));

            // The client comes back after a restart and gets its registration, not a second one.
            var returning = restarted.findByClient("issuer", publicKey).orElseThrow();
            assertEquals("registration-1", returning.id());
            assertArrayEquals(psk, returning.psk());
            assertTrue(restarted.hasCompletedRegistration("issuer"));
            assertFalse(restarted.hasCompletedRegistration("verifier"));
            restarted.recordUse("registration-1", java.time.Instant.now());
            assertTrue(restarted.lastUse("issuer").isPresent());
        } finally {
            Files.deleteIfExists(database);
        }
    }
}
