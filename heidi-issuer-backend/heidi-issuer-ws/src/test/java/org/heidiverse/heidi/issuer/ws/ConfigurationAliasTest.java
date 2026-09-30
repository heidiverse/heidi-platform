// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.ws;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class ConfigurationAliasTest {
    private static final String TRANSACTION_KEY = "heidi.issuer.transaction-code-master-key";

    @Test
    void transactionKeyStaysOptional() throws IOException {
        assertThat(environment(Map.of()).getProperty(TRANSACTION_KEY)).isEmpty();
    }

    @Test
    void sharedTransactionKeyBinds() throws IOException {
        // The coordinator uses this same fallback to encrypt codes for the issuer.
        assertThat(environment(Map.of("HEIDI_TRANSACTION_CODE_MASTER_KEY", "shared"))
                .getProperty(TRANSACTION_KEY)).isEqualTo("shared");
    }

    @Test
    void issuerKeyOverridesShared() throws IOException {
        assertThat(environment(Map.of(
                "HEIDI_ISSUER_TRANSACTION_CODE_MASTER_KEY", "issuer",
                "HEIDI_TRANSACTION_CODE_MASTER_KEY", "shared"))
                .getProperty(TRANSACTION_KEY)).isEqualTo("issuer");
    }

    @Test
    void legacyEndpointsStillResolve() throws IOException {
        var environment = environment(Map.of(
                "HEIDI_PLATFORM_BASE_URL", "http://platform.internal",
                "HEIDI_SIGNING_BASE_URL", "http://signing.internal"));
        assertThat(environment.getProperty("heidi.issuer.platform-internal-base-url"))
                .isEqualTo("http://platform.internal");
        assertThat(environment.getProperty("heidi.issuer.signing-provider.base-url"))
                .isEqualTo("http://signing.internal");
    }

    @Test
    void canonicalEndpointsWin() throws IOException {
        var environment = environment(Map.of(
                "HEIDI_ISSUER_PLATFORM_INTERNAL_BASE_URL", "http://new-platform.internal",
                "HEIDI_PLATFORM_BASE_URL", "http://old-platform.internal",
                "HEIDI_ISSUER_SIGNING_PROVIDER_BASE_URL", "http://new-signing.internal",
                "HEIDI_SIGNING_BASE_URL", "http://old-signing.internal"));
        assertThat(environment.getProperty("heidi.issuer.platform-internal-base-url"))
                .isEqualTo("http://new-platform.internal");
        assertThat(environment.getProperty("heidi.issuer.signing-provider.base-url"))
                .isEqualTo("http://new-signing.internal");
    }

    private static MockEnvironment environment(Map<String, Object> values) throws IOException {
        var properties = new Properties();
        try (var input = new ClassPathResource("application.properties").getInputStream()) {
            properties.load(input);
        }
        var environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-environment", values));
        environment.getPropertySources().addLast(new PropertiesPropertySource("application", properties));
        return environment;
    }
}
