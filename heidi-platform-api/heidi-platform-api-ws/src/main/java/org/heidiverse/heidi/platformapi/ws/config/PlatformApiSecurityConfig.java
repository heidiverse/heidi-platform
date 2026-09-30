// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.config;

import jakarta.annotation.PostConstruct;

import org.heidiverse.heidi.platformapi.extensions.security.PublicPathExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

/**
 * Authenticates against an OpenID Connect provider. Active unless the no-security
 * profile is, and requires {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.
 */
@Profile("!no-security")
@Configuration
@EnableWebSecurity
public class PlatformApiSecurityConfig {

    private static final String CANONICAL_TENANT_CLAIM = "companyId";

    private static final RequestMatcher INTERACTION_PATH =
            PathPatternRequestMatcher.pathPattern("/interaction/v1/**");

    /**
     * Reachable without a platform JWT: public protocol data and integration routes
     * that perform their own API-key or capability authentication. Everything else
     * needs one, so an endpoint added later is closed until it is listed here.
     */
    private static final String[] DEFAULT_PUBLIC_PATHS = {
        "/public/**",
        "/federation/**",
        "/interaction/v1/**",
        "/integration/v1/credentials",
        "/integration/v1/processes",
        "/integration/v1/processes/*/start",
        "/integration/v1/processes/*/result",
        "/api-docs/**",
    };

    private final String[] publicPaths;
    private final String permissionsClaim;
    private final String tenantClaim;

    public PlatformApiSecurityConfig(
            @Value("${heidi.platform.security.public-paths:}") String[] publicPaths,
            @Value("${heidi.platform.security.permissions-claim:permissions}") String permissionsClaim,
            @Value("${heidi.platform.security.tenant-claim:companyId}") String tenantClaim,
            List<PublicPathExtension> extensions) {
        var configuredPaths = publicPaths.length > 0 ? publicPaths : DEFAULT_PUBLIC_PATHS;
        var extensionPaths = extensions.stream().flatMap(extension -> extension.publicPaths().stream());
        this.publicPaths = Stream.concat(Arrays.stream(configuredPaths), extensionPaths)
                .distinct()
                .toArray(String[]::new);
        this.permissionsClaim = permissionsClaim;
        this.tenantClaim = requireClaimName(tenantClaim, "tenant claim");
    }

    @PostConstruct
    void logPublicPaths() {
        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("Endpoints reachable without a token: {}", String.join(", ", publicPaths));
    }

    @Bean
    SecurityFilterChain filterChain(
            final HttpSecurity http,
            final Converter<Jwt, ? extends AbstractAuthenticationToken> converter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.authorizeHttpRequests(
                authorize ->
                                authorize.requestMatchers(
                                        "/health",
                                        "/healthz",
                                        "/actuator/health/**",
                                        "/actuator/info",
                                        "/actuator/prometheus")
                                .permitAll()
                                .requestMatchers(publicPaths)
                                .permitAll()
                                .anyRequest()
                                .authenticated());
        http.oauth2ResourceServer(
                oauth2 ->
                        oauth2
                                .bearerTokenResolver(bearerTokenResolver())
                                .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));

        return http.build();
    }

    /**
     * The interaction endpoint authenticates the short-lived {@code cit_} capability itself.
     * Returning no bearer token here keeps the resource-server filter from attempting to decode
     * that opaque value as a platform JWT; all other routes retain the default resolver.
     */
    private BearerTokenResolver bearerTokenResolver() {
        var delegate = new DefaultBearerTokenResolver();
        return request -> {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (INTERACTION_PATH.matches(request)
                    && authorization != null
                    && authorization.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
                return null;
            }
            return delegate.resolve(request);
        };
    }

    /**
     * Maps the provider's permissions claim onto authorities. Spring reads scopes by
     * default and prefixes them, which would leave every {@code hasAuthority} check on
     * a role name failing.
     *
     * <p>The configured external tenant claim is normalized to Heidi's canonical
     * {@code companyId} claim here. Keeping that translation at the resource-server
     * boundary allows the rest of the platform to use one tenant-scoping contract.
     */
    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(permissionsClaim);
        authorities.setAuthorityPrefix("");

        var delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(authorities);
        return jwt -> delegate.convert(normalizeTenantClaim(jwt, tenantClaim));
    }

    static Jwt normalizeTenantClaim(Jwt jwt, String tenantClaim) {
        var configuredTenantClaim = requireClaimName(tenantClaim, "tenant claim");
        if (CANONICAL_TENANT_CLAIM.equals(configuredTenantClaim)) {
            return jwt;
        }

        var claims = new LinkedHashMap<>(jwt.getClaims());
        claims.remove(CANONICAL_TENANT_CLAIM);
        var configuredTenantId = claims.get(configuredTenantClaim);
        if (configuredTenantId instanceof String tenantId && !tenantId.isBlank()) {
            claims.put(CANONICAL_TENANT_CLAIM, tenantId);
        }

        var builder = Jwt.withTokenValue(jwt.getTokenValue())
                .headers(headers -> headers.putAll(jwt.getHeaders()))
                .claims(normalizedClaims -> normalizedClaims.putAll(claims));
        if (jwt.getIssuedAt() != null) {
            builder.issuedAt(jwt.getIssuedAt());
        }
        if (jwt.getExpiresAt() != null) {
            builder.expiresAt(jwt.getExpiresAt());
        }
        return builder.build();
    }

    private static String requireClaimName(String claimName, String description) {
        if (claimName == null || claimName.isBlank()) {
            throw new IllegalArgumentException(description + " must not be blank");
        }
        return claimName.trim();
    }
}
