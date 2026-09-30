// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.model;

public enum FlowVariant {
    C("c");

    private final String path;

    FlowVariant(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }

    public static FlowVariant fromPath(String value) {
        for (FlowVariant variant : values()) {
            if (variant.path.equals(value)) {
                return variant;
            }
        }
        throw new IllegalArgumentException("Unsupported issuance variant: " + value);
    }
}
