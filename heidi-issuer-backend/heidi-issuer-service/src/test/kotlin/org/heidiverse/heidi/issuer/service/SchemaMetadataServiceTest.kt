// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.heidiverse.heidi.issuer.service.SchemaMetadataService.CredentialFormat
import org.heidiverse.heidi.shared.oca.OcaFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class SchemaMetadataServiceTest {
    @Test
    fun `builds a public type metadata URI`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val properties = IssuerProperties(
            platformInternalBaseUrl = "http://entity.test",
            platformPublicBaseUrl = "https://public.example",
        )
        val service = SchemaMetadataService(builder, properties)

        server.expect(requestTo("http://entity.test/public/v2/schema/test/1.0.0"))
            .andRespond(withSuccess("""{"vct":"urn:test:vct"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://entity.test/public/v2/schema/test/1.0.0/detail"))
            .andRespond(
                withSuccess(
                    """{"issuerSettings":{"supportedCredentialTypes":["SD_JWT"]}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val metadata = service.resolve("test", "1.0.0")

        assertEquals(
            "https://public.example/public/v2/schema/test/1.0.0",
            metadata.vctMetadataUri,
        )
        server.verify()
    }

    @Test
    fun `uses only credential formats registered by the entity service`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val properties = IssuerProperties(platformInternalBaseUrl = "http://entity.test")
        val service = SchemaMetadataService(builder, properties)

        server.expect(requestTo("http://entity.test/public/v2/schema/test/1.0.0"))
            .andRespond(withSuccess("""{"vct":"urn:test:vct","display":[{"rendering":{"oca":{"uri":"https://issuer.example/oca/Eexample.json"}}}]}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://entity.test/public/v2/schema/test/1.0.0/detail"))
            .andRespond(
                withSuccess(
                    """{"displayName":"Example Card","maxBatchSize":8,"attributes":[{"name":"givenName","type":"STRING","isArray":false,"attributeNameOverrides":{"SD_JWT":"given_name"}},{"name":"roles","type":"STRING","isArray":true,"attributeNameOverrides":{}}],"issuerSettings":{"doctype":"org.example.test","namespace":"org.example.namespace","issClaimOverride":"did:webvh:override.example","supportedCredentialTypes":["SD_JWT"]},"credentialSchemeStyleDetails":[{"ocaBundleFilename":"Eexample","style":{"language":"de-CH","cardColor":4279312947,"textColor":"light","backgroundCard":"data:image/png;base64,AAAA"}}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val metadata = service.resolve("test", "1.0.0")

        assertEquals(setOf(CredentialFormat.SD_JWT), metadata.supportedFormats)
        assertEquals("urn:test:vct", metadata.vct)
        assertEquals("org.example.test", metadata.docType)
        assertEquals("org.example.namespace", metadata.namespace)
        assertEquals(8, metadata.maxBatchSize)
        assertEquals("Eexample", metadata.ocaBundleFileName)
        assertEquals("did:webvh:override.example", metadata.issuerClaimOverride)
        assertEquals(
            SchemaMetadataService.AttributeMetadata(
                type = "STRING",
                array = false,
                nameOverrides = mapOf("SD_JWT" to "given_name"),
            ),
            metadata.attributeMetadata["givenName"],
        )
        assertEquals(
            SchemaMetadataService.AttributeMetadata(
                type = "STRING",
                array = true,
                nameOverrides = emptyMap(),
            ),
            metadata.attributeMetadata["roles"],
        )
        assertEquals(
            SchemaMetadataService.CredentialDisplay(
                name = "Example Card",
                locale = "de-CH",
                backgroundColor = "#112233",
                backgroundImageUri = "data:image/png;base64,AAAA",
                textColor = "#FFFFFF",
            ),
            metadata.credentialDisplay,
        )
        server.verify()
    }

    @Test
    fun `recognizes W3C and BBS credential formats`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val properties = IssuerProperties(platformInternalBaseUrl = "http://entity.test")
        val service = SchemaMetadataService(builder, properties)

        server.expect(requestTo("http://entity.test/public/v2/schema/test/1.0.0"))
            .andRespond(withSuccess("""{"vct":"urn:test:vct"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://entity.test/public/v2/schema/test/1.0.0/detail"))
            .andRespond(
                withSuccess(
                    """{"issuerSettings":{"supportedCredentialTypes":["W3C_VCDM","ZKP_VC"]}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val metadata = service.resolve("test", "1.0.0")

        assertEquals(setOf(CredentialFormat.W3C_VCDM, CredentialFormat.ZKP_VC), metadata.supportedFormats)
        assertEquals(1, metadata.maxBatchSize)
        server.verify()
    }

    @Test
    fun `recognizes swiyu metadata and keeps legacy filename`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val service = SchemaMetadataService(builder, IssuerProperties(platformInternalBaseUrl = "http://entity.test"))

        server.expect(requestTo("http://entity.test/public/v2/schema/test/1.0.0"))
            .andRespond(withSuccess("""{"display":[{"rendering":{"oca":{"uri":"https://issuer.example/oca/Sswiyu.json"}}}]}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://entity.test/public/v2/schema/test/1.0.0/detail"))
            .andRespond(withSuccess("""{"issuerSettings":{"supportedCredentialTypes":["SD_JWT"]},"credentialSchemeStyleDetails":[{"ocaBundleFilename":"Elegacy","ocaVersion":"SWIYU"}]}""", MediaType.APPLICATION_JSON))

        val metadata = service.resolve("test", "1.0.0")

        assertEquals(OcaFormat.SWIYU, metadata.ocaFormat)
        assertEquals("Sswiyu", metadata.ocaBundleFileName)
        assertEquals("Elegacy", metadata.legacyOcaBundleFileName)
        server.verify()
    }
}
