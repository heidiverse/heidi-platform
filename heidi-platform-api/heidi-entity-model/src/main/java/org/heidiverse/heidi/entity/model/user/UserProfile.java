// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.user;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UserProfile(
        @NotNull String userId,
        @NotNull String email,
        @NotNull String tenantId,
        @NotNull List<UserRole> role,
        String displayName,
        String image) {}
