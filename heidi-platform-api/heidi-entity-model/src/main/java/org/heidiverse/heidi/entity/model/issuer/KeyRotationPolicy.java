// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.entity.model.issuer;

/** Rotation ownership and the grace retained after a platform-observed change. */
public record KeyRotationPolicy(KeyRotationMode mode, Long intervalSeconds, long gracePeriodSeconds) {
    public KeyRotationPolicy {
        if (mode == null) throw new IllegalArgumentException("Rotation mode is required");
        if (mode == KeyRotationMode.AUTOMATIC && (intervalSeconds == null || intervalSeconds <= 0)) {
            throw new IllegalArgumentException("Automatic rotation interval must be positive");
        }
        if (mode != KeyRotationMode.AUTOMATIC && intervalSeconds != null) {
            throw new IllegalArgumentException("Rotation interval is only valid for automatic rotation");
        }
        if (gracePeriodSeconds < 0) throw new IllegalArgumentException("Rotation grace must not be negative");
    }
}
