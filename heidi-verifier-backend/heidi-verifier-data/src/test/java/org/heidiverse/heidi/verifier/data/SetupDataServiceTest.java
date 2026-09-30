// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SetupDataServiceTest extends BaseDataServiceTest {
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void testSetup() {
        final var version =
                jdbcTemplate.queryForObject(
                        "SELECT split_part(split_part(version(), ' ', 2), '.', 1) AS major_version",
                        String.class);
        assertEquals("16", version);
    }
}
