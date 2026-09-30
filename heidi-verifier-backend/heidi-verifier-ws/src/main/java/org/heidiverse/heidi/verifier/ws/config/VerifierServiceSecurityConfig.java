// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.ws.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Protects verifier endpoints that are callable only by the platform service. */
@Configuration
@EnableWebSecurity
public class VerifierServiceSecurityConfig {

    private static final Logger logger =
            LoggerFactory.getLogger(VerifierServiceSecurityConfig.class);
    private static final String SERVICE_PATH = "/internal/verifier/v1/**";

    @Bean
    @Order(1)
    SecurityFilterChain serviceFilterChain(
            final HttpSecurity http,
            @Value("${heidi.verifier.platform-basic-auth:}") String basicAuth)
            throws Exception {
        http.securityMatcher(SERVICE_PATH);
        http.csrf(AbstractHttpConfigurer::disable);
        http.sessionManagement(
                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
        http.httpBasic(basic -> {});
        http.authenticationManager(serviceAuthenticationManager(basicAuth));
        return http.build();
    }

    private AuthenticationManager serviceAuthenticationManager(String basicAuth) {
        var users = new InMemoryUserDetailsManager();
        if (basicAuth.isBlank()) {
            logger.warn(
                    "heidi.verifier.platform-basic-auth is not set; service endpoints ({}) will"
                            + " reject every caller.",
                    SERVICE_PATH);
        } else {
            var decoded = new String(Base64.getDecoder().decode(basicAuth), StandardCharsets.UTF_8);
            var separator = decoded.indexOf(':');
            if (separator < 0) {
                throw new IllegalStateException(
                        "heidi.verifier.platform-basic-auth must be the base64 of user:password");
            }
            users.createUser(
                    User.withUsername(decoded.substring(0, separator))
                            .password("{noop}" + decoded.substring(separator + 1))
                            .authorities("SERVICE")
                            .build());
        }
        return new ProviderManager(new DaoAuthenticationProvider(users));
    }
}
