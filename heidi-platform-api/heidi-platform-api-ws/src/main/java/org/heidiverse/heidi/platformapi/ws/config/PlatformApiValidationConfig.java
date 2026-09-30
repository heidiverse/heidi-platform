// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.config;

import org.heidiverse.heidi.shared.localized.LocalizedValueValueExtractor;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlatformApiValidationConfig {

    @Bean
    ValidationConfigurationCustomizer localizedValueValidationCustomizer() {
        return configuration ->
                configuration.addValueExtractor(new LocalizedValueValueExtractor());
    }
}
