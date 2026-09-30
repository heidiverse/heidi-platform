// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class SigningServicePropertiesTest {
    @Test
    void localProfilePersistsKeys() throws IOException {
        var properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/application-local.properties")) {
            properties.load(input);
        }

        assertEquals(
                "${HEIDI_SIGNING_SOFTWARE_DATABASE_ENABLED:true}",
                properties.getProperty("heidi.signing.software.database.enabled"));

        var applicationProperties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/application.properties")) {
            applicationProperties.load(input);
        }

        assertEquals(
                "${heidi.signing.software.database.enabled:false}",
                applicationProperties.getProperty("spring.flyway.enabled"));
    }
}
