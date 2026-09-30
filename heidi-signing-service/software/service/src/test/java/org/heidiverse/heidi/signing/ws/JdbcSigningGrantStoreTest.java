// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import org.heidiverse.heidi.signing.server.SigningGrants.Grant;
import org.heidiverse.heidi.shared.signing.SigningPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcSigningGrantStoreTest {
    private static final String KEY_SCOPE = "kc/0f7a2f2a-3a0e-4f28-9a94-6d5f2b0f1a11";

    @Test
    void grantsSurviveRestartAndAreReplacedWhole() throws Exception {
        var database = Files.createTempFile("heidi-signing-grants", ".db");
        try {
            var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:" + database));
            jdbc.execute(
                    """
                    CREATE TABLE t_signing_grant_policy
                    (
                        scope TEXT PRIMARY KEY,
                        written_at TEXT
                    )
                    """);
            jdbc.execute(
                    """
                    CREATE TABLE t_signing_grant
                    (
                        scope TEXT NOT NULL,
                        client_name TEXT NOT NULL,
                        purposes TEXT NOT NULL,
                        PRIMARY KEY (scope, client_name)
                    )
                    """);
            var store = new JdbcSigningGrantStore(jdbc);
            jdbc.execute("CREATE TABLE t_signing_key_revocation (scope TEXT PRIMARY KEY)");

            store.replace(KEY_SCOPE, List.of(
                    new Grant("platform", Set.of(SigningPurpose.KEY_MANAGEMENT)),
                    new Grant("issuer", Set.of(SigningPurpose.SIGNING, SigningPurpose.OPERATIONS))));

            var restarted = new JdbcSigningGrantStore(jdbc);
            var grants = restarted.find(KEY_SCOPE).orElseThrow();
            assertEquals(2, grants.size());
            assertEquals(
                    Set.of(SigningPurpose.SIGNING, SigningPurpose.OPERATIONS),
                    grants.stream().filter(grant -> grant.client().equals("issuer"))
                            .findFirst().orElseThrow().purposes());

            // Unbinding a signer writes the shortened list; what is gone is gone.
            restarted.replace(KEY_SCOPE, List.of(
                    new Grant("platform", Set.of(SigningPurpose.KEY_MANAGEMENT))));
            assertEquals(List.of("platform"),
                    restarted.find(KEY_SCOPE).orElseThrow().stream().map(Grant::client).toList());

            // A scope nobody wrote a policy for is open; an empty policy denies everyone.
            assertTrue(restarted.find("kc/unknown").isEmpty());
            restarted.replace(KEY_SCOPE, List.of());
            assertEquals(List.of(), restarted.find(KEY_SCOPE).orElseThrow());

            // Revocation is durable and independent of subsequent grant replacement.
            store.revoke(KEY_SCOPE);
            store.revoke(KEY_SCOPE);
            restarted.replace(KEY_SCOPE, List.of(new Grant("issuer", Set.of(SigningPurpose.SIGNING))));
            assertTrue(new JdbcSigningGrantStore(jdbc).isRevoked(KEY_SCOPE));
        } finally {
            Files.deleteIfExists(database);
        }
    }
}
