// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import org.heidiverse.heidi.shared.signing.SigningPurpose;

/**
 * Transport authentication for the reference service.
 *
 * <p>mTLS is terminated by the HTTP server and this filter only checks that a client certificate
 * was supplied. Bearer mode is useful where mutual TLS is not available. Production defaults to
 * {@code bearer}; local development opts into {@code none} through its local Spring profile.
 */
@Configuration
@ConditionalOnWebApplication
public class SigningSecurityConfiguration {
    @Bean
    SecurityFilterChain signingSecurity(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }

    /** The service authenticates transport/profile credentials, never Spring form users. */
    @Bean
    UserDetailsService noSpringUsers() {
        return username -> {
            throw new UsernameNotFoundException("No Spring user exists: " + username);
        };
    }

    @Bean
    SigningAuthenticationFilter signingAuthenticationFilter(
            @Value("${heidi.signing.auth.mode:bearer}") String mode,
            @Value("${heidi.signing.auth.bearer-token:}") String bearerToken,
            @Value("${heidi.signing.auth.client:}") String client,
            RegisteredKeyAuthenticationService registeredKeyAuthentication) {
        return new SigningAuthenticationFilter(
                mode, bearerToken, client, registeredKeyAuthentication);
    }

    static final class SigningAuthenticationFilter extends OncePerRequestFilter {
        /** Registration and capabilities are discovery endpoints; all key use identifies its caller. */
        private static final String REGISTRATION_PATH = "/v1/auth/registrations";

        private final String mode;
        private final byte[] bearerToken;
        private final String client;
        private final RegisteredKeyAuthenticationService registeredKeyAuthentication;

        SigningAuthenticationFilter(
                String mode,
                String bearerToken,
                String client,
                RegisteredKeyAuthenticationService registeredKeyAuthentication) {
            this.mode = mode.trim().toLowerCase(java.util.Locale.ROOT);
            this.bearerToken = bearerToken.getBytes(StandardCharsets.UTF_8);
            this.client = client == null ? "" : client.trim();
            this.registeredKeyAuthentication = registeredKeyAuthentication;
            if (!java.util.Set.of("none", "bearer", "mtls", "registered").contains(this.mode)) {
                throw new IllegalArgumentException("Unsupported signing authentication mode: " + this.mode);
            }
            if ("bearer".equals(this.mode) && this.bearerToken.length == 0) {
                throw new IllegalArgumentException("Bearer signing authentication requires a token");
            }
            if (("bearer".equals(this.mode) || "mtls".equals(this.mode))
                    && this.client.isBlank()) {
                throw new IllegalArgumentException(
                        this.mode + " signing authentication requires a client name");
            }
        }

        SigningAuthenticationFilter(
                String mode,
                String bearerToken,
                RegisteredKeyAuthenticationService registeredKeyAuthentication) {
            this(mode, bearerToken, "", registeredKeyAuthentication);
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            var path = request.getRequestURI().substring(request.getContextPath().length());
            if (!path.startsWith("/v1/")) {
                filterChain.doFilter(request, response);
                return;
            }
            if (path.startsWith(REGISTRATION_PATH)) {
                if ("registered".equals(mode)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                if (!transportAuthenticated(request)) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
                    return;
                }
                if (!"none".equals(mode)) SigningClientContext.set(request, client);
                filterChain.doFilter(request, response);
                return;
            }
            if ("registered".equals(mode) && "/v1/capabilities".equals(path)) {
                filterChain.doFilter(request, response);
                return;
            }
            if ("registered".equals(mode)) {
                var body = request.getInputStream().readAllBytes();
                if (!registeredAuthenticated(request, path, body)) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
                    return;
                }
                // A named client states what it is doing; the purpose is part of its proof, so a
                // signing credential cannot be replayed against key management.
                var required = SigningPurpose.required(request.getMethod(), path);
                if (SigningClientContext.of(request) != null
                        && required != null
                        && !required.wireValue().equals(
                                request.getHeader("Signing-Authentication-Purpose"))) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Wrong purpose");
                    return;
                }
                filterChain.doFilter(new CachedBodyRequest(request, body), response);
                return;
            }
            boolean authenticated = transportAuthenticated(request);
            if (!authenticated) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
                return;
            }
            if (!"none".equals(mode)) SigningClientContext.set(request, client);
            filterChain.doFilter(request, response);
        }

        private boolean transportAuthenticated(HttpServletRequest request) {
            return switch (mode) {
                case "none" -> true;
                case "bearer" -> bearerAuthenticated(request);
                case "mtls" -> request.getAttribute("jakarta.servlet.request.X509Certificate")
                        instanceof java.security.cert.X509Certificate[] certificates
                        && certificates.length > 0;
                default -> false;
            };
        }

        private boolean bearerAuthenticated(HttpServletRequest request) {
            var header = request.getHeader("Authorization");
            if (bearerToken.length == 0 || header == null || !header.startsWith("Bearer ")) {
                return false;
            }
            var presented = header.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(bearerToken, presented);
        }

        private boolean registeredAuthenticated(HttpServletRequest request, String path, byte[] body) {
            var registrationId = request.getHeader("Signing-Authentication");
            var timestamp = request.getHeader("Signing-Authentication-Timestamp");
            var purpose = request.getHeader("Signing-Authentication-Purpose");
            var signature = request.getHeader("Signing-Authentication-Signature");
            var keyId = request.getHeader("Signing-Authentication-Key-Id");
            try {
                var combined = canonicalRequest(request, path, body);
                registeredKeyAuthentication.authenticate(
                        registrationId,
                        keyId,
                        timestamp,
                        purpose,
                        combined,
                        Base64.getDecoder().decode(signature));
                SigningClientContext.set(request, registeredKeyAuthentication.client(registrationId));
                return true;
            } catch (Exception exception) {
                return false;
            }
        }

        static byte[] canonicalRequest(HttpServletRequest request, String path, byte[] body) {
            var target = path;
            var query = request.getQueryString();
            if (query != null && !query.isEmpty()) {
                target += "?" + query;
            }
            var prefix = (request.getMethod() + "\n" + target + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            var combined = new byte[prefix.length + body.length];
            System.arraycopy(prefix, 0, combined, 0, prefix.length);
            System.arraycopy(body, 0, combined, prefix.length, body.length);
            return combined;
        }

        private static final class CachedBodyRequest extends HttpServletRequestWrapper {
            private final byte[] body;

            private CachedBodyRequest(HttpServletRequest request, byte[] body) {
                super(request);
                this.body = body.clone();
            }

            @Override
            public ServletInputStream getInputStream() {
                var input = new java.io.ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) {}
                };
            }

            @Override
            public java.io.BufferedReader getReader() {
                return new java.io.BufferedReader(
                        new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
        }
    }
}
