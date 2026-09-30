// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.profile;

import java.util.Objects;

/** Immutable catalogue entry and its version-pinned policy manifest. */
public record EcosystemProfile(
        String id,
        EcosystemProfileRole role,
        EcosystemProfileFamily family,
        String version,
        String displayName,
        EcosystemProfilePolicy policy) {
    public EcosystemProfile {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Profile ID is required");
        Objects.requireNonNull(role, "Profile role is required");
        Objects.requireNonNull(family, "Profile family is required");
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Profile version is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Profile display name is required");
        }
        Objects.requireNonNull(policy, "Profile policy is required");
    }
}
