// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.entity.model.issuer;

/** Selects who controls the key lifecycle. */
public enum KeyRotationMode {
    MANUAL,
    AUTOMATIC,
    EXTERNAL
}
