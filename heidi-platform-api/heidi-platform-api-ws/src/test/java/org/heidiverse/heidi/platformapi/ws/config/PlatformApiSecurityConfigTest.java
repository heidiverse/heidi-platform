// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class PlatformApiSecurityConfigTest {
    @Test
    void mapsConfiguredTenantClaimToCanonicalCompanyId() {
        var jwt = jwtBuilder()
                .claim("organization", "tenant-from-provider")
                .claim("companyId", "tenant-from-unconfigured-claim")
                .build();

        var normalized = PlatformApiSecurityConfig.normalizeTenantClaim(jwt, "organization");

        assertThat(normalized.getClaimAsString("organization"))
                .isEqualTo("tenant-from-provider");
        assertThat(normalized.getClaimAsString("companyId"))
                .isEqualTo("tenant-from-provider");
    }

    @Test
    void removesCanonicalTenantClaimWhenConfiguredClaimIsMissing() {
        var jwt = jwtBuilder().claim("companyId", "tenant-from-unconfigured-claim").build();

        var normalized = PlatformApiSecurityConfig.normalizeTenantClaim(jwt, "organization");

        assertThat(normalized.getClaims()).doesNotContainKey("companyId");
    }

    @Test
    void leavesDefaultTenantClaimUntouched() {
        var jwt = jwtBuilder().claim("companyId", "tenant-from-company-id").build();

        assertThat(PlatformApiSecurityConfig.normalizeTenantClaim(jwt, "companyId"))
                .isSameAs(jwt);
    }

    private static Jwt.Builder jwtBuilder() {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600));
    }
}
