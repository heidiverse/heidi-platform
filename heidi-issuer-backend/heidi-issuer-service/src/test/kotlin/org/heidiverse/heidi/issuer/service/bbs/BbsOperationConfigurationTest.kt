// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service.bbs

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.heidiverse.heidi.issuer.service.IssuerProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BbsOperationConfigurationTest {
    private val signing = IssuerProperties.IssuerSigning(keyId = "bbs-key")

    @Test
    fun `derives did heidi identifiers`() {
        val resolved = BbsOperationConfiguration.from(JsonObject(emptyMap()))
            .resolve(signing, "acme")

        assertEquals("did:heidi:acme", resolved.issuerId)
        assertEquals("did:heidi:acme#bbs-key", resolved.issuerKeyId)
    }

    @Test
    fun `rejects non did overrides`() {
        val configuration = JsonObject(
            mapOf(
                "issuerId" to JsonPrimitive("https://issuer.example"),
                "issuerKeyId" to JsonPrimitive("key-1"),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            BbsOperationConfiguration.from(configuration).resolve(signing, "acme")
        }
    }
}
