// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.model;

public enum TrustSystem {
    Switzerland,
    EUDI,
    Custom,
    OIDF,
    /** Global routing fallback; it is not a selectable trust framework. */
    Default
}
