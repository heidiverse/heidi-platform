// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.config;

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
    void canonicalKeyWins() throws IOException {
        assertThat(resolvePlatform("heidi.platform.transaction-code-master-key", Map.of(
                        "HEIDI_PLATFORM_TRANSACTION_CODE_MASTER_KEY", "canonical",
                        "HEIDI_TRANSACTION_CODE_MASTER_KEY", "shared",
                        "HEIDI_ENCRYPTION_MASTER_KEY", "legacy")))
                .isEqualTo("canonical");
    }

    @Test
    void sharedKeyFallback() throws IOException {
        assertThat(resolvePlatform("heidi.platform.transaction-code-master-key", Map.of(
                        "HEIDI_TRANSACTION_CODE_MASTER_KEY", "shared",
                        "HEIDI_ENCRYPTION_MASTER_KEY", "legacy")))
                .isEqualTo("shared");
    }

    @Test
    void legacyKeyFallback() throws IOException {
        assertThat(resolvePlatform("heidi.platform.transaction-code-master-key", Map.of(
                        "HEIDI_ENCRYPTION_MASTER_KEY", "legacy")))
                .isEqualTo("legacy");
    }

    @Test
    void languageFallbacks() throws IOException {
        var key = "heidi.platform.localization.default-language";
        assertThat(resolvePlatform(key, Map.of())).isEqualTo("en");
        assertThat(resolvePlatform(key, Map.of("HEIDI_LOCALIZATION_DEFAULT_LANGUAGE", "de")))
                .isEqualTo("de");
        assertThat(resolvePlatform(key, Map.of(
                "HEIDI_PLATFORM_LOCALIZATION_DEFAULT_LANGUAGE", "fr",
                "HEIDI_LOCALIZATION_DEFAULT_LANGUAGE", "de"))).isEqualTo("fr");
    }

    private static String resolvePlatform(String key, Map<String, Object> environment)
            throws IOException {
        return resolve("application.properties", key, environment);
    }

    private static String resolve(String resourceName, String key, Map<String, Object> environment)
            throws IOException {
        var properties = new Properties();
        var resource = new ClassPathResource(resourceName);
        try (InputStream input = resource.getInputStream()) {
            properties.load(input);
        }

        var expression = properties.getProperty(key);
        assertThat(expression).as("property %s", key).isNotNull();
        var environmentProperties = new MockEnvironment();
        environmentProperties.getPropertySources().addFirst(
                new MapPropertySource("test-environment", environment));
        environmentProperties.getPropertySources().addLast(
                new PropertiesPropertySource("application", properties));
        return environmentProperties.resolveRequiredPlaceholders(expression);
    }
}
