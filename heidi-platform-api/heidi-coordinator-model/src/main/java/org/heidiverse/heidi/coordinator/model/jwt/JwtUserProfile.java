// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.jwt;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record JwtUserProfile(
        @NotNull String sub,
        String username,
        List<UserRole> permissions,
        String tenantId,
        String displayName) {}
