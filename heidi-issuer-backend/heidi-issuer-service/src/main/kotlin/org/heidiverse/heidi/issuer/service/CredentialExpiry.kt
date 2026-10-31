// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

enum class CredentialExpiryMode {
    DEFAULT,
    CUSTOM,
    NONE,
}

data class CredentialExpiry(
    val mode: CredentialExpiryMode,
    val lifetimeDays: Long? = null,
) {
    init {
        require(mode != CredentialExpiryMode.CUSTOM || lifetimeDays != null && lifetimeDays > 0) {
            "Custom credential lifetime must be at least one day"
        }
    }

    companion object {
        val DEFAULT = CredentialExpiry(CredentialExpiryMode.DEFAULT)
    }
}
