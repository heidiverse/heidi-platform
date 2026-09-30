// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.heidiverse.heidi.issuer.model.TrustSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.http.HttpStatus
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient

class IssuerConfigurationClientTest {
    @Test
    fun `resolves issuance profile from credential schema`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val properties = IssuerProperties(platformInternalBaseUrl = "https://platform.example")
        server.expect {
            assertEquals(
                "https://platform.example/public/v2/schema/card/1.0/issuer",
                it.uri.toString(),
            )
        }.andRespond(
            withSuccess(
                "{\"issuerSlug\":\"issuer-a\",\"issuanceProfileId\":\"EUDI_ISSUANCE_2026_1\"}",
                MediaType.APPLICATION_JSON,
            ),
        )

        val profile = IssuerConfigurationClient(builder, properties)
            .profileFor("issuer-a", "card", "1.0")

        assertEquals("EUDI_ISSUANCE_2026_1", profile)
        server.verify()
    }

    @Test
    fun `resolves plain signing configuration bound to issuer and trust system`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val properties = IssuerProperties(platformInternalBaseUrl = "https://platform.example")
        val plaintext = """{
            "issuerClaim":"did:webvh:example.org:issuer",
            "keyId":"issuer-key",
            "keyUri":"software://issuer-key",
            "algorithm":"ES256",
            "providerEndpoint":"https://provider.example",
            "issuerJwk":"{}",
            "certificateChain":["leaf","root"],
            "supportedAlgorithms":["BBS"],
            "supportedOperations":["w3c.bbs-data-integrity-credential-issuance"]
        }""".trimIndent()
        server.expect {
            assertEquals(
                "https://platform.example/internal/platform/v1/identities/issuer-a/signing-configuration?trustSystem=EUDI",
                it.uri.toString(),
            )
        }.andRespond(
            withSuccess(
                plaintext,
                MediaType.APPLICATION_JSON,
            ),
        )

        val resolved = IssuerConfigurationClient(builder, properties)
            .resolve("issuer-a", TrustSystem.EUDI)

        assertEquals(TrustSystem.EUDI, resolved.trustSystem)
        assertEquals("did:webvh:example.org:issuer", resolved.issuerClaim)
        assertEquals("issuer-key", resolved.signing.keyId)
        assertEquals("software://issuer-key", resolved.signing.keyUri)
        assertEquals("https://provider.example", resolved.signing.providerEndpoint)
        assertEquals(listOf("leaf", "root"), resolved.signing.certificateChain)
        assertEquals(listOf("BBS"), resolved.signing.supportedAlgorithms)
        assertEquals(
            listOf("w3c.bbs-data-integrity-credential-issuance"),
            resolved.signing.supportedOperations,
        )
        server.verify()
    }

    @Test
    fun `resolves generic operation configuration`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val properties = IssuerProperties(platformInternalBaseUrl = "https://platform.example")
        server.expect {
            assertEquals(
                "https://platform.example/internal/platform/v1/identities/issuer-a/operations/"
                    + "w3c.bbs-data-integrity-credential-issuance/config?trustSystem=Default",
                it.uri.toString(),
            )
        }.andRespond(
            withSuccess(
                "{\"operation\":\"w3c.bbs-data-integrity-credential-issuance\","
                    + "\"schemaVersion\":1,\"configuration\":{"
                    + "\"issuerId\":\"did:example:issuer\","
                    + "\"issuerKeyId\":\"did:example:issuer#key-1\"}}",
                MediaType.APPLICATION_JSON,
            ),
        )

        val resolved = IssuerConfigurationClient(builder, properties)
            .resolveOperationConfiguration(
                "issuer-a",
                TrustSystem.Default,
                "w3c.bbs-data-integrity-credential-issuance",
            )

        assertEquals(1, resolved.schemaVersion)
        assertEquals("did:example:issuer", resolved.configuration["issuerId"]?.toString()?.trim('"'))
        server.verify()
    }

    @Test
    fun `returns null for absent optional operation configuration`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val properties = IssuerProperties(platformInternalBaseUrl = "https://platform.example")
        server.expect { request ->
            assertEquals(
                "https://platform.example/internal/platform/v1/identities/issuer-a/operations/"
                    + "w3c.bbs-data-integrity-credential-issuance/config?trustSystem=Default",
                request.uri.toString(),
            )
        }.andRespond(withStatus(HttpStatus.NOT_FOUND))

        val resolved = IssuerConfigurationClient(builder, properties)
            .resolveOperationConfigurationOrNull(
                "issuer-a",
                TrustSystem.Default,
                "w3c.bbs-data-integrity-credential-issuance",
            )

        assertEquals(null, resolved)
        server.verify()
    }

    @Test
    fun `resolves operation signing configuration from its dedicated endpoint`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val properties = IssuerProperties(platformInternalBaseUrl = "https://platform.example")
        val operation = "w3c.bbs-data-integrity-credential-issuance"
        val plaintext = """{
            "trustSystem":"Default",
            "keyId":"bbs-key",
            "keyUri":"next-gen://bbs-key",
            "algorithm":"BBS",
            "providerEndpoint":"https://provider.example",
            "issuerJwk":"{}",
            "supportedAlgorithms":["BBS"],
            "supportedOperations":["$operation"]
        }""".trimIndent()
        server.expect {
            assertEquals(
                "https://platform.example/internal/platform/v1/identities/issuer-a/operations/"
                    + "$operation/signing-configuration?trustSystem=Default",
                it.uri.toString(),
            )
        }.andRespond(
            withSuccess(
                plaintext,
                MediaType.APPLICATION_JSON,
            ),
        )

        val resolved = IssuerConfigurationClient(builder, properties)
            .resolveOperationSigningConfiguration("issuer-a", TrustSystem.Default, operation)

        assertEquals("bbs-key", resolved.signing.keyId)
        assertEquals("BBS", resolved.signing.algorithm)
        assertEquals(listOf(operation), resolved.signing.supportedOperations)
        server.verify()
    }

}
