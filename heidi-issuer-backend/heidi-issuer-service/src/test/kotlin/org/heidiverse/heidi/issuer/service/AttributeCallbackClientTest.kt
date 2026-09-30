// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class AttributeCallbackClientTest {
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = AttributeCallbackClient(builder, ProcessTokenDecoder())

    @Test
    fun `replaces embedded attributes with callback claims`() {
        server.expect(requestTo("https://claims.example/issuance"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """
                    {
                      "schemaIdentifier": {
                        "credentialIdentifier": "student-card",
                        "version": "1.0"
                      },
                      "attributes": {
                        "name": {
                          "value": "Callback Ada",
                          "attributeType": "STRING",
                          "isArray": false,
                          "attributeNameOverrides": {}
                        }
                      }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )
        val original = credentialData("https://claims.example/issuance")

        val resolved = client.resolve(original)

        assertEquals("Callback Ada", resolved.attributes.getValue("name").value)
        assertEquals("https://claims.example/issuance", resolved.attributeUrl)
        server.verify()
    }

    @Test
    fun `rejects callback data for a different credential schema`() {
        server.expect(requestTo("https://claims.example/wrong"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "schemaIdentifier": {
                        "credentialIdentifier": "other-card",
                        "version": "1.0"
                      },
                      "attributes": {}
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertThrows(AttributeCallbackException::class.java) {
            client.resolve(credentialData("https://claims.example/wrong"))
        }
        server.verify()
    }

    private fun credentialData(url: String) = CredentialData(
        credentialIdentifier = "student-card",
        credentialVersion = "1.0",
        issuerSlug = "local",
        attributes = mapOf(
            "name" to CredentialData.Attribute("Embedded Ada", "STRING", false, emptyMap()),
        ),
        attributeUrl = url,
    )
}
