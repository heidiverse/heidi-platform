// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class SchemaValidationTest extends BaseDataServiceTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    void startsWithMigratedSchema() {}

    @Test
    void identityKeySlotHasNoSourceColumn() {
        var sourceColumns = jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 't_identity_key_slot'
                  AND column_name = 'source'
                """, Integer.class);

        assertEquals(0, sourceColumns);
    }
}
