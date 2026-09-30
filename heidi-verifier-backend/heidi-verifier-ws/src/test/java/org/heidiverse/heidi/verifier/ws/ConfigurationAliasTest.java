// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.ws;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.io.ClassPathResource;

class ConfigurationAliasTest {
    @Test
    void canonicalOid4vpWins() throws IOException {
        assertThat(resolve("heidi.verifier.oid4vp.predefined-jwks", Map.of(
                        "HEIDI_VERIFIER_OID4VP_PREDEFINED_JWKS", "canonical",
                        "OID4VP_VERIFIER_PREDEFINED_JWKS", "legacy")))
                .isEqualTo("canonical");
    }

    @Test
    void legacyOid4vpFallback() throws IOException {
        assertThat(resolve("heidi.verifier.oid4vp.predefined-jwks", Map.of(
                        "OID4VP_VERIFIER_PREDEFINED_JWKS", "legacy")))
                .isEqualTo("legacy");
    }

    private static String resolve(String key, Map<String, Object> environment) throws IOException {
        var properties = new Properties();
        try (InputStream input = new ClassPathResource("application.properties").getInputStream()) {
            properties.load(input);
        }

        var expression = properties.getProperty(key);
        assertThat(expression).as("property %s", key).isNotNull();
        var standardEnvironment = new MockEnvironment();
        standardEnvironment.getPropertySources().addFirst(
                new MapPropertySource("test-environment", environment));
        standardEnvironment.getPropertySources().addLast(
                new PropertiesPropertySource("application", properties));
        return standardEnvironment.resolveRequiredPlaceholders(expression);
    }
}
