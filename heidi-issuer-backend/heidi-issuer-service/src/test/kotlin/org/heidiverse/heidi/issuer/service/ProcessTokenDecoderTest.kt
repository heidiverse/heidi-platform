// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Base64
import java.util.UUID

class ProcessTokenDecoderTest {
    private val decoder = ProcessTokenDecoder()

    @Test
    fun `decodes coordinator issuance token containing values`() {
        val payload = """
            {
              "data": {
                "issuance": {
                  "issuerSlug": "university",
                  "schemaIdentifier": {
                    "credentialIdentifier": "student-card",
                    "version": "1.0"
                  },
                  "values": {
                    "givenName": "Ada",
                    "roles": ["student", "researcher"],
                    "active": true
                  }
                }
              }
            }.coordinator-signature
        """.trimIndent()
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.encodeToByteArray())

        val result = decoder.decode(token)

        assertEquals("student-card", result.credentialIdentifier)
        assertEquals("1.0", result.credentialVersion)
        assertEquals("university", result.issuerSlug)
        assertEquals("Ada", result.attributes.getValue("givenName").value)
        assertEquals(listOf("student", "researcher"), result.attributes.getValue("roles").value)
        assertEquals(true, result.attributes.getValue("active").value)
    }

    @Test
    fun `decodes transaction code and deferred transaction metadata`() {
        val transactionId = UUID.randomUUID()
        val payload = """
            {
              "txCodeEncrypted": "encrypted-code",
              "data": {
                "action": "pre_auth_issuance",
                "deferredTransactionId": "$transactionId",
                "data": {
                  "attributeUrl": "https://claims.example/issuance",
                  "issuerSlug": "local",
                  "schemaIdentifier": {
                    "credentialIdentifier": "student-card",
                    "version": "1.0"
                  },
                  "values": {}
                }
              }
            }.coordinator-signature
        """.trimIndent()
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.encodeToByteArray())

        val result = decoder.decode(token)

        assertEquals(transactionId, result.deferredTransactionId)
        assertEquals("encrypted-code", result.encryptedTxCode)
        assertEquals("https://claims.example/issuance", result.attributeUrl)
    }
}
