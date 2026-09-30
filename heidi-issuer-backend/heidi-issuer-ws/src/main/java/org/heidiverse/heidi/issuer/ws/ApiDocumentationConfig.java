// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.ws;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiDocumentationConfig {

    @Bean
    GroupedOpenApi issuerInternalApi() {
        return GroupedOpenApi.builder()
                .group("internal")
                .pathsToMatch("/internal/issuer/**")
                .build();
    }

    @Bean
    GroupedOpenApi issuerProtocolApi() {
        return GroupedOpenApi.builder()
                .group("protocol")
                .pathsToExclude("/internal/**", "/health", "/healthz", "/actuator/**")
                .build();
    }

    @Bean
    GroupedOpenApi issuerOperationsApi() {
        return GroupedOpenApi.builder()
                .group("operations")
                .pathsToMatch("/health", "/healthz", "/actuator/**")
                .build();
    }
}
