// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

/** Technical operations a provider may perform with one key. */
public enum SigningKeyUsage {
    SIGN,
    KEY_AGREEMENT,
    UNWRAP
}
