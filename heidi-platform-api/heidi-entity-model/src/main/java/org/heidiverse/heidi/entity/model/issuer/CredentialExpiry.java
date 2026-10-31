// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

public record CredentialExpiry(Mode mode, Long lifetimeDays) {
    public enum Mode {
        DEFAULT,
        CUSTOM,
        NONE
    }

    public CredentialExpiry {
        mode = mode == null ? Mode.DEFAULT : mode;
        if (mode == Mode.CUSTOM) {
            if (lifetimeDays == null || lifetimeDays < 1) {
                throw new IllegalArgumentException(
                        "Custom credential lifetime must be at least one day");
            }
        } else {
            lifetimeDays = null;
        }
    }

    public static CredentialExpiry defaultExpiry() {
        return new CredentialExpiry(Mode.DEFAULT, null);
    }
}
