// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.heidiverse.heidi.issuer.service.bbs.BbsOperationConfiguration

class IssuerPropertiesTest {
    @Test
    fun `provider-backed signing requires a key id`() {
        val configuration = IssuerProperties.IssuerSigning(
            keyUri = "software://issuer-key",
        )

        assertThrows(IllegalArgumentException::class.java) {
            configuration.validate("issuer")
        }
    }

    @Test
    fun `bbs profile validates its operation configuration`() {
        val configuration = BbsOperationConfiguration(
            "did:heidi:issuer",
            "did:heidi:issuer#bbs-key",
        )
        val signing = IssuerProperties.IssuerSigning(keyId = "bbs-key")

        assertEquals(configuration, configuration.resolve(signing, "issuer"))
    }
}
