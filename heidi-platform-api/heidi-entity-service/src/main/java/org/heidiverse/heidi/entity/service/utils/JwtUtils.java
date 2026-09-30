// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service.utils;

import org.heidiverse.heidi.entity.model.user.JwtUserProfile;
import org.heidiverse.heidi.entity.model.user.UserRole;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    private JwtUtils() {}

    private static final String CLAIM_SUB = "sub";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String CLAIM_TENANT_ID = "companyId";
    private static final String CLAIM_DISPLAY_NAME = "display_name";

    @NotNull
    static Jwt getJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            throw new BadCredentialsException("No authenticated JWT found");
        }
        return (Jwt) authentication.getPrincipal();
    }

    @NotNull
    public static String getUserId() {
        return Optional.ofNullable(getJwt().getClaimAsString(CLAIM_SUB))
                .orElseThrow(() -> new BadCredentialsException("Claim 'sub' not found in JWT"));
    }

    @NotNull
    public static JwtUserProfile getUserProfile() {
        var jwt = getJwt();
        var sub =
                Optional.ofNullable(jwt.getClaimAsString(CLAIM_SUB))
                        .orElseThrow(
                                () -> new BadCredentialsException("Claim 'sub' not found in JWT"));

        var permissions = jwt.getClaimAsStringList(CLAIM_PERMISSIONS);
        var userRoles = mapPermissionsToRoles(permissions);
        var username = jwt.getClaimAsString(CLAIM_USERNAME);
        if (username == null || username.isBlank()) {
            username = jwt.getClaimAsString(CLAIM_EMAIL);
        }

        return new JwtUserProfile(
                sub,
                username,
                userRoles,
                jwt.getClaimAsString(CLAIM_TENANT_ID),
                jwt.getClaimAsString(CLAIM_DISPLAY_NAME));
    }

    /**
     * Ensure super admins pass a tenant ID; for others take it form the JWT.
     *
     * @param tenantId passed via request
     * @return tenant ID
     */
    public static String validateAndResolveTenantId(String tenantId) {
        var userProfile = getUserProfile();

        // Super admins must provide a tenant ID
        if (userProfile.permissions().contains(UserRole.SUPER_ADMIN)) {
            if (tenantId == null) {
                throw new IllegalArgumentException("Super admins must provide a `tenantId`.");
            }
            return tenantId;
        }
        // For other users it must match the tenant ID in the JWT
        String jwtTenantId = userProfile.tenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = jwtTenantId;
        } else if (!tenantId.equals(jwtTenantId)) {
            throw new SecurityException("User cannot access tenant associated with `tenantId`");
        }
        return tenantId;
    }

    private static List<UserRole> mapPermissionsToRoles(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }
        return permissions.stream()
                .map(JwtUtils::parseUserRole)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static UserRole parseUserRole(String permission) {
        try {
            return UserRole.valueOf(permission);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid permission '{}' in JWT, skipping...", permission);
            return null;
        }
    }
}
