// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Profile("local")
@Configuration
public class PlatformApiLocalCorsConfig implements WebMvcConfigurer {

    private final String webPort;

    public PlatformApiLocalCorsConfig(@Value("${HEIDI_WEB_PORT:5173}") String webPort) {
        this.webPort = webPort;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:" + webPort, "http://127.0.0.1:" + webPort)
                .allowedMethods("*")
                .allowedHeaders("*")
                .exposedHeaders("Location");
    }
}
