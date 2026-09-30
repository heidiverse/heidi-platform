// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platform.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class EcosystemProfilesMigrationTest {
    private static final String MIGRATION =
            "db/migration/postgresql/V0_1_40__ecosystem_profiles.sql";

    @Test
    void upgradesExistingDatabasesIdempotently() throws IOException {
        var sql = migration();

        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS issuance_profile_id"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS presentation_profile_id"));
        assertTrue(sql.contains("WHERE credential.issuance_profile_id IS NULL"));
        assertTrue(sql.contains("WHERE proof.presentation_profile_id IS NULL"));
    }

    @Test
    void mapsLegacyTrustToCurrentProfiles() throws IOException {
        var sql = migration();

        assertTrue(sql.contains("'EUDI_ISSUANCE_2026_1'"));
        assertTrue(sql.contains("'SWISS_ISSUANCE_2026_1'"));
        assertTrue(sql.contains("'CUSTOM_ISSUANCE_2026_1'"));
        assertTrue(sql.contains("'EUDI_PRESENTATION_2026_1'"));
        assertTrue(sql.contains("'SWISS_PRESENTATION_2026_1'"));
        assertTrue(sql.contains("'CUSTOM_PRESENTATION_2026_1'"));
        assertFalse(sql.contains("STANDALONE_X509"));
    }

    @Test
    void rejectsAmbiguousLegacyRows() throws IOException {
        var sql = migration();

        assertTrue(sql.contains("Cannot classify credential schemas without an explicit trust system"));
        assertTrue(sql.contains("Cannot classify presentation schemes without an explicit trust system"));
        assertTrue(sql.contains("ALTER COLUMN issuance_profile_id SET NOT NULL"));
        assertTrue(sql.contains("ALTER COLUMN presentation_profile_id SET NOT NULL"));
    }

    private static String migration() throws IOException {
        try (InputStream stream = EcosystemProfilesMigrationTest.class
                .getClassLoader().getResourceAsStream(MIGRATION)) {
            assertNotNull(stream, "Migration resource is missing");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
