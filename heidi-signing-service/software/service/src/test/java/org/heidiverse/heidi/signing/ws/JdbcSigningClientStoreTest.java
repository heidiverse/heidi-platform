// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.ws;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcSigningClientStoreTest {
    @Test
    void acceptedClientsSurviveRestartAndWithdrawal() throws Exception {
        var database = Files.createTempFile("heidi-signing-clients", ".db");
        try {
            var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:" + database));
            jdbc.execute("""
                    CREATE TABLE t_signing_auth_client
                    (
                        client_name TEXT PRIMARY KEY,
                        public_key TEXT NOT NULL,
                        created_at TEXT,
                        updated_at TEXT
                    )
                    """);
            var key = new byte[] {1, 2, 3};
            new JdbcSigningClientStore(jdbc).put("issuer", key);

            var restarted = new JdbcSigningClientStore(jdbc);
            assertEquals(java.util.Set.of("issuer"), restarted.findAll().keySet());
            assertArrayEquals(key, restarted.findAll().get("issuer"));

            restarted.remove("issuer");
            assertEquals(java.util.Map.of(), restarted.findAll());
        } finally {
            Files.deleteIfExists(database);
        }
    }
}
