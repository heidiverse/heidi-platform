// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.ws.config;

import static org.springframework.security.config.Customizer.withDefaults;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Profile("!no-security")
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WsSecurityConfig {

    private static final String SWAGGER = "SWAGGER";

    @Bean
    SecurityFilterChain swaggerFilterChain(final HttpSecurity http) throws Exception {
        // swagger-ui
        http.securityMatcher("/api-docs/**", "/api-docs.yaml")
                .authorizeHttpRequests(
                        customizer ->
                                customizer
                                        .requestMatchers("/api-docs/**", "/api-docs.yaml")
                                        .hasRole(SWAGGER))
                .httpBasic(withDefaults());

        return http.build();
    }

    @Bean
    SecurityFilterChain bearerFilterChain(final HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);

        http.securityMatcher("/v1/**", "/actuator/**", "/health", "/healthz")
                .authorizeHttpRequests(
                        customizer ->
                                customizer
                                        .requestMatchers(HttpMethod.OPTIONS, "/v1/**")
                                        .permitAll()
                                        .requestMatchers(
                                                "/v1/wallet/**", "/v1/check/**")
                                        .permitAll());

        http.authorizeHttpRequests(
                customizer ->
                        customizer
                                .requestMatchers(HttpMethod.GET, "/", "/info", "/check")
                                .permitAll()
                                .requestMatchers("/health", "/healthz")
                                .permitAll()
                                .requestMatchers(
                                        "/actuator/health/**",
                                        "/actuator/info",
                                        "/actuator/prometheus")
                                .permitAll()
                                .anyRequest() // deny all others
                                .denyAll());

        return http.build();
    }

    @Bean
    InMemoryUserDetailsManager userDetailsService() {
        final var user =
                User.builder()
                        .username("ubswagger")
                        .password("$2a$10$kkl4QFGZPM2i.TwQPuXhMewLtDBvF.FRohAtMp7dZ4wq8q1N.U7yy")
                        .roles(SWAGGER)
                        .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearer-key",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")));
    }
}
