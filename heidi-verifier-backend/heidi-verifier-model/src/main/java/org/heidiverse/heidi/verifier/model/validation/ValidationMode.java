// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.validation;

public enum ValidationMode {
    DISABLED,
    ENABLED,
    ENFORCED;

    public boolean isEnabled() {
        return this != DISABLED;
    }

    public boolean isEnforced() {
        return this == ENFORCED;
    }
}
