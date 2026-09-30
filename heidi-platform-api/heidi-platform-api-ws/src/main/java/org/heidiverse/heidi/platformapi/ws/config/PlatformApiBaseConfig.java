// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.config;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.heidiverse.heidi.coordinator.data.util.JacksonJsonFormatMapper;
import org.heidiverse.heidi.coordinator.service.TokenSignatureService;
import org.heidiverse.heidi.shared.springdoc.SpringDocConfig;
import org.hibernate.cfg.AvailableSettings;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableMethodSecurity
@EntityScan({
        "org.heidiverse.heidi.entity.model.entity",
        "org.heidiverse.heidi.coordinator.model.entity"
})
@EnableJpaRepositories({
        "org.heidiverse.heidi.entity.data.repository",
        "org.heidiverse.heidi.coordinator.data.repository"
})
@EnableTransactionManagement
public class PlatformApiBaseConfig {

    static {
        SpringDocConfig.setDefaultConfig();
    }

    @Bean
    ObjectMapper objectMapper() {
        return JsonMapper.builder().build();
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder.build();
    }

    @Bean
    public TokenSignatureService tokenSignatureService() {
        return new TokenSignatureService(JsonMapper.builder().build());
    }

    @Bean
    HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return hibernateProperties ->
                hibernateProperties.put(
                        AvailableSettings.JSON_FORMAT_MAPPER,
                        JacksonJsonFormatMapper.class.getName());
    }
}
