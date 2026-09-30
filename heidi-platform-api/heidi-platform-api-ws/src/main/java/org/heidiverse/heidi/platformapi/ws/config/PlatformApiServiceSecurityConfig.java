// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Authenticates the other services rather than people: the issuer backend reading
 * deferred credentials and signing configuration. These callers hold a shared
 * credential, not a token, so they authenticate here whatever the user-facing profile
 * does — including the one that disables user authentication entirely.
 */
@Configuration
@EnableWebSecurity
public class PlatformApiServiceSecurityConfig {

    private static final Logger logger =
            LoggerFactory.getLogger(PlatformApiServiceSecurityConfig.class);

    /**
     * Endpoints only another service calls. Read alongside the user-facing chain: a
     * path listed here never reaches it.
     */
    static final String[] SERVICE_PATHS = {
        "/v1/deferred-credential/status/**",
        "/v1/deferred-credential/attribute/**",
        "/v1/deferred-credential/*/invalidate",
        "/internal/platform/v1/**",
    };

    private final String basicAuth;

    public PlatformApiServiceSecurityConfig(
            @Value("${heidi.platform.server.api.basic-auth:}") String basicAuth) {
        this.basicAuth = basicAuth;
    }

    @Bean
    @Order(1)
    SecurityFilterChain serviceFilterChain(final HttpSecurity http) throws Exception {
        http.securityMatcher(SERVICE_PATHS);
        http.csrf(AbstractHttpConfigurer::disable);
        http.sessionManagement(
                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
        http.httpBasic(basic -> {});
        http.authenticationManager(serviceAuthenticationManager());

        return http.build();
    }

    /**
     * Builds the caller from {@code heidi.platform.server.api.basic-auth}, which holds the
     * base64 of {@code user:password}. Without it no caller can authenticate and the
     * endpoints stay closed, which is the safe direction for a missing secret.
     */
    private AuthenticationManager serviceAuthenticationManager() {
        var users = new InMemoryUserDetailsManager();

        if (basicAuth.isBlank()) {
            logger.warn(
                    "heidi.platform.server.api.basic-auth is not set; service endpoints ({}) will reject"
                            + " every caller.",
                    String.join(", ", SERVICE_PATHS));
        } else {
            var decoded = new String(Base64.getDecoder().decode(basicAuth), StandardCharsets.UTF_8);
            var separator = decoded.indexOf(':');
            if (separator < 0) {
                throw new IllegalStateException(
                        "heidi.platform.server.api.basic-auth must be the base64 of user:password");
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
