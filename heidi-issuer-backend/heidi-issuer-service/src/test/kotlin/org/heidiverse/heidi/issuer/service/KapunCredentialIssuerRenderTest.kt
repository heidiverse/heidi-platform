// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import uniffi.kapun_credential_core_rust.SignatureCreator
import java.util.Base64

class KapunCredentialIssuerRenderTest {
    @Test
    fun `adds the type metadata URI to an SD-JWT`() {
        val signatureClient = mock(SignatureClient::class.java)
        val configuration = IssuerProperties.IssuerSigning(
            keyId = "test-key",
            issuerJwk = """{"kty":"EC","kid":"test-key"}""",
        )
        val signer = object : SignatureClient.KapunsSignaturCreator("issuer", configuration) {
            override fun alg() = "ES256"
            override fun sign(bytes: ByteArray) = ByteArray(64)
        }
        val snapshot = IssuerFlowSnapshot(signing = configuration)
        `when`(
            signatureClient.fromSnapshot("issuer", snapshot),
        ).thenReturn(signer)
        val issuer = KapunCredentialIssuer(signatureClient, IssuerProperties())

        val credential = issuer.issueSdJwt(
            CredentialData("card", "1.0.0", "issuer", emptyMap()),
            "issuer",
            "https://issuer.example",
            "urn:example:card",
            null,
            snapshot,
            vctMetadataUri = "https://platform.example/public/v2/schema/card/1.0.0",
        )

        val payload = credential.substringBefore('~').split('.')[1]
        val claims = Json.parseToJsonElement(
            Base64.getUrlDecoder().decode(payload).decodeToString(),
        ).jsonObject
        assertEquals(
            "https://platform.example/public/v2/schema/card/1.0.0",
            claims.getValue("vct_metadata_uri").jsonPrimitive.content,
        )
        assertFalse(claims.containsKey("vct_metadata_uri#integrity"))
    }

    @Test
    fun `adds the OCA render method to an SD-JWT`() {
        val signatureClient = mock(SignatureClient::class.java)
        val configuration = IssuerProperties.IssuerSigning(
            keyId = "test-key",
            issuerJwk = """{"kty":"EC","kid":"test-key"}""",
        )
        val signer = object : SignatureClient.KapunsSignaturCreator("issuer", configuration), SignatureCreator {
            override fun alg() = "ES256"
            override fun sign(bytes: ByteArray) = ByteArray(64)
        }
        `when`(
            signatureClient.fromSnapshot("issuer", IssuerFlowSnapshot(signing = configuration)),
        ).thenReturn(signer)
        val issuer = KapunCredentialIssuer(signatureClient, IssuerProperties())

        val credential = issuer.issueSdJwt(
            CredentialData("card", "1.0.0", "issuer", emptyMap()),
            "issuer",
            "https://issuer.example/issuer/c/card/1.0.0",
            "urn:example:card",
            null,
            IssuerFlowSnapshot(signing = configuration),
            ocaUrl = "https://issuer.example/oca/Eexample.json",
        )

        val payload = credential.substringBefore('~').split('.')[1]
        val claims = Json.parseToJsonElement(
            Base64.getUrlDecoder().decode(payload).decodeToString(),
        ).jsonObject
        assertFalse("render" in claims)

        val renderDisclosure = credential.split('~')
            .drop(1)
            .dropLast(1)
            .map { disclosure ->
                Json.parseToJsonElement(
                    Base64.getUrlDecoder().decode(disclosure).decodeToString(),
                ).jsonArray
            }
            .single { it[1].jsonPrimitive.content == "render" }
        val render = renderDisclosure[2].jsonObject
        assertEquals("OverlaysCaptureBundleV1", render.getValue("type").jsonPrimitive.content)
        assertEquals("https://issuer.example/oca/Eexample.json", render.getValue("oca").jsonPrimitive.content)
    }

    @Test
    fun `adds a non-disclosed status-list reference to an SD-JWT`() {
        val signatureClient = mock(SignatureClient::class.java)
        val configuration = IssuerProperties.IssuerSigning(
            keyId = "test-key",
            issuerJwk = """{"kty":"EC","kid":"test-key"}""",
        )
        val signer = object : SignatureClient.KapunsSignaturCreator("issuer", configuration) {
            override fun alg() = "ES256"
            override fun sign(bytes: ByteArray) = ByteArray(64)
        }
        `when`(
            signatureClient.fromSnapshot("issuer", IssuerFlowSnapshot(signing = configuration)),
        ).thenReturn(signer)
        val issuer = KapunCredentialIssuer(signatureClient, IssuerProperties())

        val credential = issuer.issueSdJwt(
            CredentialData("card", "1.0.0", "issuer", emptyMap()),
            "issuer",
            "https://issuer.example",
            "urn:example:card",
            null,
            IssuerFlowSnapshot(signing = configuration),
            statusList = CredentialData.StatusListReference("https://status.example/list", 42),
        )

        val payload = credential.substringBefore('~').split('.')[1]
        val statusList = Json.parseToJsonElement(
            Base64.getUrlDecoder().decode(payload).decodeToString(),
        ).jsonObject.getValue("status").jsonObject.getValue("status_list").jsonObject
        assertEquals(42, statusList.getValue("idx").jsonPrimitive.content.toInt())
        assertEquals("https://status.example/list", statusList.getValue("uri").jsonPrimitive.content)
    }
}
