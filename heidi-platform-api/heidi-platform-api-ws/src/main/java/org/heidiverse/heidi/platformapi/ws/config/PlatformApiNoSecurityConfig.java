// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.config;

import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.tenant.TenantRequest;
import org.heidiverse.heidi.entity.model.user.UserRole;
import org.heidiverse.heidi.entity.service.IdentityFederationService;
import org.heidiverse.heidi.entity.service.IssuerService;
import org.heidiverse.heidi.entity.service.TenantService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

@Profile("no-security")
@Configuration
@EnableWebSecurity
public class PlatformApiNoSecurityConfig {
    private static final String TENANT_ID = "acme";
    private static final String CANONICAL_TENANT_CLAIM = "companyId";

    @Bean
    SecurityFilterChain filterChain(final HttpSecurity http, NoSecurityAuthenticationFilter filter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.authorizeHttpRequests(authorize -> authorize.requestMatchers("/**").permitAll());
        http.addFilterBefore(filter, AnonymousAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(0)
    ApplicationRunner defaultTenant(
            TenantService tenantService,
            IssuerService issuerService,
            IdentityFederationService federationService,
            @Value("${heidi.platform.local.tenant-id:" + TENANT_ID + "}") String tenantId,
            @Value("${heidi.platform.local.tenant-display-name:Acme}") String tenantName,
            @Value("${heidi.platform.local.issuer-slug:acme}") String issuerSlug,
            @Value("${heidi.platform.local.issuer-display-name:Acme Digital Identity}")
                    String issuerName) {
        return args -> {
            if (tenantService.getTenant(tenantId).isEmpty()) {
                var request = new TenantRequest();
                request.setDisplayName(tenantName);
                request.setIssuerIds(List.of());
                tenantService.upsertTenant(tenantId, request);
            }

            var localIssuer = issuerService.ensureLocalDevelopmentIssuer(
                    tenantId, issuerSlug, issuerName);
            var request = new TenantRequest();
            request.setIssuerIds(List.of(localIssuer.getId()));
            tenantService.upsertTenant(tenantId, request);

            federationService.ensureLocalDevelopmentFederation(localIssuer.getSlug());
        };
    }

    static Set<IssuerTrustSystem> parseTrustSystems(String configured) {
        if (configured == null || configured.isBlank()) return Set.of();
        var trustSystems = new LinkedHashSet<IssuerTrustSystem>();
        Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(value -> {
                    final IssuerTrustSystem trustSystem;
                    try {
                        trustSystem = IssuerTrustSystem.valueOf(value);
                    } catch (IllegalArgumentException exception) {
                        throw new IllegalArgumentException(
                                "Unknown local trust system: " + value, exception);
                    }
                    if (trustSystem == IssuerTrustSystem.Default) {
                        throw new IllegalArgumentException(
                                "Default is routing configuration, not an assignable trust system");
                    }
                    trustSystems.add(trustSystem);
                });
        return Set.copyOf(trustSystems);
    }

    @Bean
    NoSecurityAuthenticationFilter noSecurityAuthenticationFilter(
            @Value("${heidi.platform.local.tenant-id:" + TENANT_ID + "}") String tenantId,
            @Value("${heidi.platform.local.user-id:acme-admin}") String userId,
            @Value("${heidi.platform.local.username:klara@acme.example}") String username,
            @Value("${heidi.platform.local.display-name:Klara Admin}") String displayName,
            @Value("${heidi.platform.security.tenant-claim:companyId}") String tenantClaim,
            @Value("${heidi.platform.local.roles:SUPER_ADMIN,ADMIN,MANAGER,EDITOR,OPERATOR,DEVELOPER}")
                    List<String> roles) {
        return new NoSecurityAuthenticationFilter(
                tenantId, userId, username, displayName, tenantClaim, roles);
    }

    /**
     * Resolves the roles granted to the local user. A deployment running without an
     * identity provider has one user who may do everything, so the default grants every
     * role; narrow it to exercise role gating locally. Fails fast on an unknown name.
     */
    private static List<String> parseRoles(List<String> configured) {
        return configured.stream()
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(UserRole::valueOf)
                .map(UserRole::name)
                .toList();
    }

    static class NoSecurityAuthenticationFilter extends OncePerRequestFilter {
        private final String tenantId;
        private final String userId;
        private final String username;
        private final String displayName;
        private final String tenantClaim;
        private final List<String> roles;

        NoSecurityAuthenticationFilter(
                String tenantId,
                String userId,
                String username,
                String displayName,
                String tenantClaim,
                List<String> roles) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.username = username;
            this.displayName = displayName;
            if (tenantClaim == null || tenantClaim.isBlank()) {
                throw new IllegalArgumentException("tenant claim must not be blank");
            }
            this.tenantClaim = tenantClaim.trim();
            this.roles = parseRoles(roles);
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                var now = Instant.now();
                var jwtBuilder =
                        Jwt.withTokenValue("no-security")
                                .header("alg", "none")
                                .subject(userId)
                                .issuedAt(now)
                                .expiresAt(now.plusSeconds(3600))
                                .claim("username", username)
                                .claim("display_name", displayName)
                                .claim(CANONICAL_TENANT_CLAIM, tenantId)
                                .claim("permissions", roles);
                if (!CANONICAL_TENANT_CLAIM.equals(tenantClaim)) {
                    jwtBuilder.claim(tenantClaim, tenantId);
                }
                var jwt = jwtBuilder.build();
                var authorities =
                        roles.stream().map(SimpleGrantedAuthority::new).toList();
                SecurityContextHolder.getContext()
                        .setAuthentication(new JwtAuthenticationToken(jwt, authorities, userId));
            }

            filterChain.doFilter(withDefaultAuthorizationHeader(request), response);
        }

        private HttpServletRequest withDefaultAuthorizationHeader(HttpServletRequest request) {
            if (request.getHeader("Authorization") != null) {
                return request;
            }

            return new HttpServletRequestWrapper(request) {
                @Override
                public String getHeader(String name) {
                    if ("Authorization".equalsIgnoreCase(name)) {
                        return "Bearer no-security";
                    }
                    return super.getHeader(name);
                }

                @Override
                public Enumeration<String> getHeaders(String name) {
                    if ("Authorization".equalsIgnoreCase(name)) {
                        return new Vector<>(List.of("Bearer no-security")).elements();
                    }
                    return super.getHeaders(name);
                }

                @Override
                public Enumeration<String> getHeaderNames() {
                    var headerNames = new Vector<String>();
                    var existingHeaderNames = super.getHeaderNames();
                    while (existingHeaderNames.hasMoreElements()) {
                        headerNames.add(existingHeaderNames.nextElement());
                    }
                    headerNames.add("Authorization");
                    return headerNames.elements();
                }
            };
        }
    }
}
