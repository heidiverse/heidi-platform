// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.ws.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiDocumentationConfig {

    @Bean
    GroupedOpenApi verifierInternalApi() {
        return GroupedOpenApi.builder()
                .group("internal")
                .pathsToMatch("/internal/verifier/**")
                .build();
    }

    @Bean
    GroupedOpenApi verifierProtocolApi() {
        return GroupedOpenApi.builder()
                .group("protocol")
                .pathsToExclude(
                        "/internal/**",
                        "/health",
                        "/healthz",
                        "/actuator/**")
                .build();
    }

    @Bean
    GroupedOpenApi verifierOperationsApi() {
        return GroupedOpenApi.builder()
                .group("operations")
                .pathsToMatch("/health", "/healthz", "/actuator/**")
                .build();
    }
}
