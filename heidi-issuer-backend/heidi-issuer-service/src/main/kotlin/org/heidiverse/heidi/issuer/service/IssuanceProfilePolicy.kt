// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

/** Profile version data pinned to the issuer profile catalogue. */
object IssuanceProfilePolicy {
    private const val EUDI = "EUDI_ISSUANCE_2026_1"
    private const val SWISS = "SWISS_ISSUANCE_2026_1"
    private const val OIDF = "OIDF_ISSUANCE_2026_1"
    private const val CUSTOM = "CUSTOM_ISSUANCE_2026_1"
    private const val SWISS_VERSION = "swiss-profile-issuance:1.0.0"
    private const val PLATFORM_VERSION = "2026.1"
    fun version(profileId: String): String = when (profileId) {
        EUDI, OIDF, CUSTOM -> PLATFORM_VERSION
        SWISS -> SWISS_VERSION
        else -> error("Unsupported issuance profile: $profileId")
    }

    fun metadataVersion(profileId: String): String? = when (profileId) {
        EUDI, OIDF, CUSTOM -> null
        SWISS -> SWISS_VERSION
        else -> error("Unsupported issuance profile: $profileId")
    }
}
