// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import com.nimbusds.jose.CompressionAlgorithm
import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDHEncrypter
import com.nimbusds.jose.crypto.RSADecrypter
import com.nimbusds.jose.crypto.RSAEncrypter
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.heidiverse.heidi.issuer.model.api.CredentialResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class CredentialEncryptionServiceTest {
    private val unrestricted = CredentialEncryptionPolicy(requestKeys = listOf(
        metadataKey("ec-key", JWEAlgorithm.ECDH_ES_A256KW),
        metadataKey("rsa-key", JWEAlgorithm.RSA_OAEP_256),
    ))
    private val service = encryptionService(requestRequired = false, responseRequired = false)
    private val objectMapper = ObjectMapper()

    @Test
    fun `fails closed when the platform has no profile encryption policy`() {
        val configurationClient = mock(IssuerConfigurationClient::class.java)
        `when`(configurationClient.resolveCredentialEncryption("issuer", "CUSTOM_ISSUANCE_2026_1"))
            .thenReturn(null)
        val service = CredentialEncryptionService(IssuerProperties(), configurationClient)

        val error = assertThrows(IllegalStateException::class.java) {
            service.policyFor("issuer", "CUSTOM_ISSUANCE_2026_1")
        }

        assertEquals(
            "Credential encryption policy is missing for issuance profile 'CUSTOM_ISSUANCE_2026_1'",
            error.message,
        )
    }

    private fun encryptionService(requestRequired: Boolean, responseRequired: Boolean) =
        IssuerProperties(
            credentialRequestEncryptionRequired = requestRequired,
            credentialResponseEncryptionRequired = responseRequired,
        ).let { CredentialEncryptionService(it, IssuerConfigurationClient(RestClient.builder(), it)) }

    @Test
    fun `metadata advertises optional Kapun-compatible algorithms`() {
        val request = service.requestEncryptionMetadata(unrestricted)!!
        val response = service.responseEncryptionMetadata(unrestricted)

        assertFalse(request.encryptionRequired())
        assertFalse(response.encryptionRequired())
        assertTrue(request.jwks().keys().any { it["alg"] == "ECDH-ES+A256KW" })
        assertTrue(request.jwks().keys().any { it["alg"] == "RSA-OAEP-256" })
        assertTrue(response.algValuesSupported().containsAll(listOf("ECDH-ES+A128KW", "RSA-OAEP-256")))
        assertTrue(response.encValuesSupported().containsAll(listOf("A128GCM", "A256CBC-HS512")))
        assertEquals(listOf("DEF"), request.zipValuesSupported())
        assertEquals(listOf("DEF"), response.zipValuesSupported())
    }

    @Test
    fun `required encryption metadata is enforced`() {
        val requiredService = encryptionService(requestRequired = true, responseRequired = true)

        assertTrue(requiredService.requestEncryptionMetadata(unrestricted)!!.encryptionRequired())
        assertTrue(requiredService.responseEncryptionMetadata(unrestricted).encryptionRequired())
        assertEquals(
            "invalid_encryption_parameters",
            assertThrows(Oid4vciProtocolException::class.java) {
                requiredService.validateRequirements(
                    unrestricted, encryptedRequest = false, responseEncryptionRequested = false,
                )
            }.errorCode,
        )
        assertEquals(
            "invalid_encryption_parameters",
            assertThrows(Oid4vciProtocolException::class.java) {
                requiredService.validateRequirements(
                    unrestricted, encryptedRequest = true, responseEncryptionRequested = false,
                )
            }.errorCode,
        )
        requiredService.validateRequirements(
            unrestricted, encryptedRequest = true, responseEncryptionRequested = true,
        )
    }

    @Test
    fun `requires the signing service for credential request decryption`() {
        val publicKey = metadataKey(JWEAlgorithm.ECDH_ES_A256KW) as ECKey
        val encrypted = JWEObject(
            JWEHeader.Builder(JWEAlgorithm.ECDH_ES_A256KW, EncryptionMethod.A256CBC_HS512)
                .keyID(publicKey.keyID)
                .compressionAlgorithm(CompressionAlgorithm.DEF)
                .build(),
            Payload("""{"credential_configuration_id":"example"}"""),
        ).apply { encrypt(ECDHEncrypter(publicKey)) }.serialize()

        assertThrows(Oid4vciProtocolException::class.java) {
            service.decryptRequest(unrestricted, encrypted, "local")
        }
    }

    @Test
    fun `decrypts a credential request through the signing protocol`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val contentKey = ByteArray(CONTENT_KEY_BYTES) { (it + 1).toByte() }
        val payload = """{"credential_configuration_id":"example"}"""
        val compactJwe = compactJwe(contentKey, payload)

        server.expect { request -> assertEquals("/v1/capabilities", request.uri.path) }
            .andRespond(withSuccess(
                """{"scheme":"software","supportedAlgorithms":[],"digestSigningAlgorithms":[],"supportedOperations":[],"keylessOperations":[],"canCreate":false,"canImport":false,"canDelete":false,"contentKeyAlgorithms":["RSA-OAEP-256"]}""",
                MediaType.APPLICATION_JSON,
            ))
        server.expect { request ->
            assertEquals("/v1/keys/content-key", request.uri.path)
            val body = (request as MockClientHttpRequest).getBodyAsString(StandardCharsets.UTF_8)
            assertTrue(body.contains("\"keyUri\":\"software://request-key\""))
            assertTrue(body.contains("\"algorithm\":\"RSA-OAEP-256\""))
            assertTrue(body.contains("\"enc\":\"A256GCM\""))
        }.andRespond(withSuccess(
            """{"contentKey":"${Base64.getEncoder().encodeToString(contentKey)}"}""",
            MediaType.APPLICATION_JSON,
        ))

        val properties = IssuerProperties()
        val encryption = CredentialEncryptionService(
            properties,
            IssuerConfigurationClient(RestClient.builder(), properties),
            SigningProviderClient(builder, properties),
        )
        val policy = CredentialEncryptionPolicy(requestKeys = listOf(CredentialEncryptionKey(
            "request-key",
            "software://request-key",
            "RSA-OAEP-256",
            """{"kty":"RSA","kid":"request-key"}""",
            "http://provider.example",
            keyAlgorithm = "RS256",
        )))

        assertEquals(payload, encryption.decryptRequest(policy, compactJwe, "issuer"))
        server.verify()
    }

    @Test
    fun `decrypts RSA credential request and encrypts RSA credential response`() {
        val issuerPublicKey = metadataKey(JWEAlgorithm.RSA_OAEP_256) as RSAKey
        val request = JWEObject(
            JWEHeader.Builder(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A128GCM)
                .keyID(issuerPublicKey.keyID)
                .build(),
            Payload("""{"transaction_id":"3fwe98js"}"""),
        ).apply { encrypt(RSAEncrypter(issuerPublicKey)) }.serialize()
        val walletKey = RSAKeyGenerator(2048)
            .keyUse(KeyUse.ENCRYPTION)
            .algorithm(JWEAlgorithm.RSA_OAEP_256)
            .keyID("wallet-key")
            .generate()
        val parameters = CredentialResponseEncryptionParameters(
            Json.parseToJsonElement(walletKey.toPublicJWK().toJSONString()).jsonObject,
            "A192GCM",
            "DEF",
        )
        val encryptedResponse = service.encryptResponse(
            unrestricted,
            CredentialResponse.single("nonce", "credential"),
            parameters,
        )
        val response = JWEObject.parse(encryptedResponse).apply { decrypt(RSADecrypter(walletKey)) }

        assertEquals(JWEAlgorithm.RSA_OAEP_256, response.header.algorithm)
        assertEquals(EncryptionMethod.A192GCM, response.header.encryptionMethod)
        assertEquals("wallet-key", response.header.keyID)
        assertEquals(CompressionAlgorithm.DEF, response.header.compressionAlgorithm)
        assertTrue(response.payload.toString().contains("\"credential\":\"credential\""))
    }

    @Test
    fun `an issuer policy narrows the advertised metadata`() {
        val policy = CredentialEncryptionPolicy(
            requestAlgValues = listOf("ECDH-ES+A256KW"),
            requestEncValues = listOf("A256GCM"),
            requestZipValues = emptyList(),
            responseAlgValues = listOf("ECDH-ES+A256KW"),
            responseEncValues = listOf("A256GCM", "A128GCM"),
            responseZipValues = emptyList(),
            requestEncryptionRequired = true,
            responseEncryptionRequired = true,
            requestKeys = unrestricted.requestKeys,
        )

        val request = service.requestEncryptionMetadata(policy)!!
        val response = service.responseEncryptionMetadata(policy)

        assertEquals(listOf("ECDH-ES+A256KW"), request.jwks().keys().map { it["alg"] })
        assertEquals(listOf("A256GCM"), request.encValuesSupported())
        assertEquals(listOf("ECDH-ES+A256KW"), response.algValuesSupported())
        assertEquals(listOf("A256GCM", "A128GCM"), response.encValuesSupported())
        // The required flags override the issuer backend's own defaults, which are false here.
        assertTrue(request.encryptionRequired())
        assertTrue(response.encryptionRequired())
    }

    @Test
    fun `a request using an alg the issuer excluded is rejected`() {
        val publicKey = metadataKey(JWEAlgorithm.ECDH_ES_A256KW) as ECKey
        val encrypted = JWEObject(
            JWEHeader.Builder(JWEAlgorithm.ECDH_ES_A256KW, EncryptionMethod.A256GCM)
                .keyID(publicKey.keyID)
                .build(),
            Payload("""{"credential_configuration_id":"example"}"""),
        ).apply { encrypt(ECDHEncrypter(publicKey)) }.serialize()
        val policy = CredentialEncryptionPolicy(
            requestAlgValues = listOf("RSA-OAEP-256"),
            requestKeys = unrestricted.requestKeys,
        )

        assertEquals(
            "invalid_encryption_parameters",
            assertThrows(Oid4vciProtocolException::class.java) {
                service.decryptRequest(policy, encrypted)
            }.errorCode,
        )
    }

    @Test
    fun `a response using an enc the issuer excluded is rejected`() {
        val walletKey = RSAKeyGenerator(2048)
            .keyUse(KeyUse.ENCRYPTION)
            .algorithm(JWEAlgorithm.RSA_OAEP_256)
            .keyID("wallet-key")
            .generate()
        val parameters = CredentialResponseEncryptionParameters(
            Json.parseToJsonElement(walletKey.toPublicJWK().toJSONString()).jsonObject,
            "A192GCM",
            null,
        )
        val policy = CredentialEncryptionPolicy(responseEncValues = listOf("A256GCM"))

        assertEquals(
            "invalid_encryption_parameters",
            assertThrows(Oid4vciProtocolException::class.java) {
                service.encryptResponse(policy, CredentialResponse.single("nonce", "credential"), parameters)
            }.errorCode,
        )
    }

    @Test
    fun `an alg selection with no matching decryption key is a configuration error`() {
        val policy = CredentialEncryptionPolicy(
            requestAlgValues = listOf("ECDH-ES+A256KW", "unknown"),
            requestKeys = unrestricted.requestKeys,
        )
        val impossible = policy.copy(requestAlgValues = listOf("unknown"))

        assertEquals(1, service.requestEncryptionMetadata(policy)!!.jwks().keys().size)
        assertNull(service.requestEncryptionMetadata(impossible))
    }

    private fun metadataKey(algorithm: JWEAlgorithm): JWK = service.requestEncryptionMetadata(unrestricted)!!.jwks().keys()
        .single { it["alg"] == algorithm.name }
        .let { JWK.parse(objectMapper.writeValueAsString(it)) }

    private fun metadataKey(keyId: String, algorithm: JWEAlgorithm): CredentialEncryptionKey {
        val key = when {
            algorithm.name.startsWith("ECDH") -> ECKeyGenerator(Curve.P_256)
                .keyID(keyId)
                .algorithm(algorithm)
                .generate()
            else -> RSAKeyGenerator(2048)
                .keyID(keyId)
                .algorithm(algorithm)
                .generate()
        }
        return CredentialEncryptionKey(
            keyId,
            "software://local/$keyId",
            algorithm.name,
            key.toPublicJWK().toJSONString(),
        )
    }

    private fun compactJwe(contentKey: ByteArray, payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString(
            """{"alg":"RSA-OAEP-256","enc":"A256GCM","kid":"request-key"}"""
                .toByteArray(StandardCharsets.UTF_8),
        )
        val iv = ByteArray(GCM_IV_BYTES) { it.toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(contentKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        cipher.updateAAD(header.toByteArray(StandardCharsets.US_ASCII))
        val encrypted = cipher.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        val ciphertext = encrypted.copyOfRange(0, encrypted.size - GCM_TAG_BYTES)
        val tag = encrypted.copyOfRange(encrypted.size - GCM_TAG_BYTES, encrypted.size)

        return listOf(
            header,
            encoder.encodeToString(byteArrayOf(1)),
            encoder.encodeToString(iv),
            encoder.encodeToString(ciphertext),
            encoder.encodeToString(tag),
        ).joinToString(".")
    }

    private companion object {
        const val CONTENT_KEY_BYTES = 32
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / Byte.SIZE_BITS
    }
}
