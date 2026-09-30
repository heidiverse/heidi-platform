// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import com.nimbusds.jose.jwk.JWK
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.heidiverse.heidi.issuer.model.TrustSystem
import org.heidiverse.heidi.issuer.service.bbs.BbsCredentialIssuer
import org.heidiverse.heidi.shared.signing.SigningKeyException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.MockHttpOutputMessage
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import uniffi.heidi_signing.JoseKeyPair
import java.util.function.Supplier

class SignatureClientBeanTest {
    private companion object {
        const val BBS_OPERATION = "w3c.bbs-data-integrity-credential-issuance"
    }

    @Test
    fun `resolves metadata signing through the profile route`() {
        val configurationClient = mock(IssuerConfigurationClient::class.java)
        val signing = IssuerProperties.IssuerSigning(keyId = "custom-key", algorithm = "ES256")
        `when`(configurationClient.resolve(
            "issuer", TrustSystem.Default, "pid", "1.0", "CUSTOM_ISSUANCE_2026_1",
        )).thenReturn(
            IssuerConfigurationClient.RuntimeSigningConfiguration(
                TrustSystem.Custom, null, signing, "CUSTOM_ISSUANCE_2026_1",
            ),
        )

        val configurations = SignatureClient(
            mock(SigningProviderClient::class.java), configurationClient,
        ).configurationsFor("issuer", "pid", "1.0", "CUSTOM_ISSUANCE_2026_1")

        assertEquals(listOf(signing), configurations)
        verify(configurationClient).resolve(
            "issuer", TrustSystem.Default, "pid", "1.0", "CUSTOM_ISSUANCE_2026_1",
        )
        verify(configurationClient, never()).resolve(
            "issuer", TrustSystem.EUDI, "pid", "1.0", "CUSTOM_ISSUANCE_2026_1",
        )
    }

    @Test
    fun `spring instantiates signature client through constructor injection`() {
        AnnotationConfigApplicationContext().use { context ->
            context.registerBean(RestClient.Builder::class.java, Supplier { RestClient.builder() })
            context.registerBean(IssuerProperties::class.java, Supplier { IssuerProperties() })
            context.register(
                IssuerConfigurationClient::class.java,
                SigningProviderClient::class.java,
                SignatureClient::class.java,
            )

            context.refresh()

            assertNotNull(context.getBean(SignatureClient::class.java))
        }
    }

    @Test
    fun `requires the provider endpoint for a provider-backed key`() {
        val configurationClient = mock(IssuerConfigurationClient::class.java)
        val configuration = IssuerProperties.IssuerSigning(
            keyId = "issuer-key",
            keyUri = "software://issuer-key",
            algorithm = "ES256",
            issuerJwk = "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"x\",\"y\":\"y\"}",
        )
        `when`(configurationClient.resolve("issuer", TrustSystem.Default, null, null))
            .thenReturn(IssuerConfigurationClient.RuntimeSigningConfiguration(TrustSystem.Default, null, configuration))

        val error = assertThrows(SigningKeyException::class.java) {
            val properties = IssuerProperties()
            SignatureClient(
                SigningProviderClient(RestClient.builder(), properties),
                configurationClient,
            ).forIssuer("issuer")
        }

        assertEquals(
            "Issuer 'issuer' uses provider-backed signing key 'software://issuer-key', but "
                + "heidi.issuer.signing-provider.base-url is not configured",
            error.message,
        )
    }

    @Test
    fun `publishes the public key resolved from the provider`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val configurationClient = mock(IssuerConfigurationClient::class.java)
        JoseKeyPair.generate("ES256").use { providerKey ->
            JoseKeyPair.generate("ES256").use { stalePublishedKey ->
                val configuration = IssuerProperties.IssuerSigning(
                    keyId = "issuer-key",
                    keyUri = "software://issuer-key",
                    algorithm = "ES256",
                    providerEndpoint = "http://provider.example",
                    issuerJwk = JWK.parse(stalePublishedKey.toPublicJwk()).toECKey().toJSONString(),
                )
                `when`(configurationClient.resolve("issuer", TrustSystem.Default, null, null))
                    .thenReturn(IssuerConfigurationClient.RuntimeSigningConfiguration(TrustSystem.Default, null, configuration))
                server.expect { request ->
                    assertEquals("/v1/capabilities", request.uri.path)
                }.andRespond(
                    withSuccess(
                        "{\"scheme\":\"software\",\"supportedAlgorithms\":[\"ES256\"],"
                            + "\"digestSigningAlgorithms\":[],\"canCreate\":false,"
                            + "\"canImport\":false,\"canDelete\":false}",
                        MediaType.APPLICATION_JSON,
                    ),
                )
                server.expect { request ->
                    assertEquals("/v1/keys", request.uri.path)
                }.andRespond(
                    withSuccess(
                        "{\"uri\":\"software://issuer-key\",\"publicKeyDocument\":${providerKey.toPublicJwk()},\"algorithm\":\"ES256\"}",
                        MediaType.APPLICATION_JSON,
                    ),
                )

                val published = SignatureClient(
                    SigningProviderClient(builder, IssuerProperties()),
                    configurationClient,
                ).forIssuer("issuer").publicJwk()
                val publishedJwk = JWK.parse(published).toECKey()
                val providerJwk = JWK.parse(providerKey.toPublicJwk()).toECKey()
                val staleJwk = JWK.parse(stalePublishedKey.toPublicJwk()).toECKey()

                assertEquals(providerJwk.x, publishedJwk.x)
                assertEquals(providerJwk.y, publishedJwk.y)
                assertNotEquals(staleJwk.x, publishedJwk.x)
                server.verify()
            }
        }
    }

    @Test
    fun `issues bbs credentials through the provider protocol`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val configurationClient = mock(IssuerConfigurationClient::class.java)
        val configuration = IssuerProperties.IssuerSigning(
            keyId = "issuer-key",
            keyUri = "next-gen://bbs-key",
            providerEndpoint = "http://provider.example",
        )
        `when`(configurationClient.resolveOperationConfigurationOrNull(
            "issuer", TrustSystem.Default, BBS_OPERATION,
            "EUDI_ISSUANCE_2026_1",
        ))
            .thenReturn(
                IssuerConfigurationClient.RuntimeOperationConfiguration(
                    BBS_OPERATION,
                    1,
                    Json.parseToJsonElement(
                        "{\"issuerId\":\"did:example:issuer\","
                            + "\"issuerKeyId\":\"did:example:issuer#key-1\"}",
                    ).jsonObject,
                ),
            )
        `when`(configurationClient.resolveOperationSigningConfiguration(
            "issuer", TrustSystem.Default, BBS_OPERATION, "card", "1.0.0",
            "EUDI_ISSUANCE_2026_1",
        ))
            .thenReturn(
                IssuerConfigurationClient.RuntimeSigningConfiguration(
                    TrustSystem.Default,
                    null,
                    configuration,
                ),
            )
        server.expect { request ->
            assertEquals("/v1/capabilities", request.uri.path)
        }.andRespond(
            withSuccess(
                "{\"scheme\":\"next-gen\",\"supportedAlgorithms\":[\"BBS\"],"
                    + "\"digestSigningAlgorithms\":[],\"supportedOperations\":[\""
                    + BBS_OPERATION + "\"],\"canCreate\":true,"
                    + "\"canImport\":false,\"canDelete\":true}",
                MediaType.APPLICATION_JSON,
            ),
        )
        server.expect { request ->
            assertEquals("/v1/keys", request.uri.path)
        }.andRespond(
            withSuccess(
                "{\"uri\":\"next-gen://bbs-key\",\"publicKeyDocument\":"
                    + "{\"kty\":\"BBS\",\"crv\":\"BLS12-381-G2\",\"x\":\"public\"},"
                    + "\"algorithm\":\"BBS\"}",
                MediaType.APPLICATION_JSON,
            ),
        )
        server.expect { request ->
            assertEquals("/v1/operations", request.uri.path)
            val body = (request as MockHttpOutputMessage).getBodyAsString()
            assert(body.contains("\"operation\":\"$BBS_OPERATION\""))
            assert(body.contains("\"keyUri\":\"next-gen://bbs-key\""))
            assert(!body.contains("issuer_sk"))
            assert(!body.contains("secretKey"))
        }.andRespond(
            withSuccess(
                "{\"operation\":\"$BBS_OPERATION\","
                    + "\"status\":\"COMPLETED\","
                    + "\"result\":{\"credential\":\"encoded-bbs-credential\"}}",
                MediaType.APPLICATION_JSON,
            ),
        )

        val data = CredentialData(
            "card",
            "1.0.0",
            "issuer",
            mapOf(
                "name" to CredentialData.Attribute(
                    "Alice",
                    "string",
                    false,
                    mapOf("BBS" to "http://schema.org/name"),
                ),
            ),
        )
        val properties = IssuerProperties(
            signingProvider = IssuerProperties.SigningProvider(
                authenticationMode = "none",
            ),
        )
        val credential = BbsCredentialIssuer(
            configurationClient,
            SigningProviderClient(builder, properties),
        ).issue(data, "issuer", "ExampleCredential", null,
            IssuerFlowService(configurationClient).capture("issuer", TrustSystem.Default, "card", "1.0.0",
                setOf(SchemaMetadataService.CredentialFormat.ZKP_VC), CredentialEncryptionPolicy(),
                "EUDI_ISSUANCE_2026_1"))

        assertEquals("encoded-bbs-credential", credential)
        server.verify()
    }

    @Test
    fun `derives bbs issuer identifiers when operation configuration is absent`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val configurationClient = mock(IssuerConfigurationClient::class.java)
        val configuration = IssuerProperties.IssuerSigning(
            keyId = "issuer-key",
            keyUri = "next-gen://bbs-key",
            algorithm = "BBS",
            providerEndpoint = "http://provider.example",
        )
        `when`(configurationClient.resolveOperationConfigurationOrNull(
            "issuer", TrustSystem.Default, BBS_OPERATION,
            "EUDI_ISSUANCE_2026_1",
        )).thenReturn(null)
        `when`(configurationClient.resolveOperationSigningConfiguration(
            "issuer", TrustSystem.Default, BBS_OPERATION, "card", "1.0.0",
            "EUDI_ISSUANCE_2026_1",
        )).thenReturn(
            IssuerConfigurationClient.RuntimeSigningConfiguration(
                TrustSystem.Default,
                null,
                configuration,
            ),
        )
        server.expect { request -> assertEquals("/v1/capabilities", request.uri.path) }
            .andRespond(
                withSuccess(
                    "{\"scheme\":\"next-gen\",\"supportedAlgorithms\":[\"BBS\"],"
                        + "\"digestSigningAlgorithms\":[],\"supportedOperations\":[\""
                        + BBS_OPERATION + "\"],\"canCreate\":true,"
                        + "\"canImport\":false,\"canDelete\":true}",
                    MediaType.APPLICATION_JSON,
                ),
            )
        server.expect { request -> assertEquals("/v1/keys", request.uri.path) }
            .andRespond(
                withSuccess(
                    "{\"uri\":\"next-gen://bbs-key\",\"publicKeyDocument\":"
                        + "{\"kty\":\"BBS\",\"crv\":\"BLS12-381-G2\",\"x\":\"public\"},"
                        + "\"algorithm\":\"BBS\"}",
                    MediaType.APPLICATION_JSON,
                ),
            )
        server.expect { request ->
            val body = (request as MockHttpOutputMessage).getBodyAsString()
            assert(body.contains("\"issuerId\":\"did:heidi:issuer\""))
            assert(body.contains("\"issuerKeyId\":\"did:heidi:issuer#issuer-key\""))
            assert(body.contains("\"http://schema.org/name\":{\"@value\":\"Alice\"}"))
        }.andRespond(
            withSuccess(
                "{\"operation\":\"$BBS_OPERATION\",\"status\":\"COMPLETED\","
                    + "\"result\":{\"credential\":\"encoded-bbs-credential\"}}",
                MediaType.APPLICATION_JSON,
            ),
        )
        val data = CredentialData(
            "card",
            "1.0.0",
            "issuer",
            mapOf("name" to CredentialData.Attribute("Alice", "string", false, emptyMap())),
        )

        val credential = BbsCredentialIssuer(
            configurationClient,
            SigningProviderClient(builder, IssuerProperties()),
        ).issue(data, "issuer", "ExampleCredential", null,
            IssuerFlowService(configurationClient).capture("issuer", TrustSystem.Default, "card", "1.0.0",
                setOf(SchemaMetadataService.CredentialFormat.ZKP_VC), CredentialEncryptionPolicy(),
                "EUDI_ISSUANCE_2026_1"))

        assertEquals("encoded-bbs-credential", credential)
        server.verify()
    }

    @Test
    fun `uses capabilities published in the signing configuration`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val configurationClient = mock(IssuerConfigurationClient::class.java)
        val configuration = IssuerProperties.IssuerSigning(
            keyId = "issuer-key",
            keyUri = "next-gen://bbs-key",
            algorithm = "BBS",
            supportedAlgorithms = listOf("BBS"),
            supportedOperations = listOf(BBS_OPERATION),
        )
        `when`(configurationClient.resolveOperationSigningConfiguration(
            "issuer", TrustSystem.Default, BBS_OPERATION, "card", "1.0.0",
            "EUDI_ISSUANCE_2026_1",
        ))
            .thenReturn(
                IssuerConfigurationClient.RuntimeSigningConfiguration(
                    TrustSystem.Default,
                    null,
                    configuration,
                ),
            )
        val available = BbsCredentialIssuer(
            configurationClient,
            SigningProviderClient(builder, IssuerProperties()),
        ).isAvailable("issuer", "card", "1.0.0", issuanceProfileId = "EUDI_ISSUANCE_2026_1")

        assertEquals(true, available)
        server.verify()
    }

    @Test
    fun `publishes retained keys without an active slot`() {
        val configurationClient = mock(IssuerConfigurationClient::class.java)
        val configuration = IssuerProperties.IssuerSigning(
            keyId = "credential-key",
            algorithm = "ES256",
            issuerJwk = "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"x\",\"y\":\"y\"}",
        )
        `when`(configurationClient.publicKeys("issuer")).thenReturn(listOf(configuration.issuerJwk))

        val published = SignatureClient(
            SigningProviderClient(RestClient.builder(), IssuerProperties()),
            configurationClient,
        ).publicJwksFor("issuer")

        assertEquals(listOf(configuration.issuerJwk), published)
    }

    @Test
    fun `reports issuer and key when the provider cannot resolve the configured key`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val configurationClient = mock(IssuerConfigurationClient::class.java)
        val configuration = IssuerProperties.IssuerSigning(
            keyId = "issuer-key",
            keyUri = "software://issuer-key",
            algorithm = "ES256",
            providerEndpoint = "http://provider.example",
            issuerJwk = "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"x\",\"y\":\"y\"}",
        )
        `when`(configurationClient.resolve("issuer", TrustSystem.Default, null, null))
            .thenReturn(
                IssuerConfigurationClient.RuntimeSigningConfiguration(
                    TrustSystem.Default,
                    null,
                    configuration,
                ),
            )
        server.expect { request ->
            assertEquals("/v1/capabilities", request.uri.path)
        }.andRespond(
            withSuccess(
                "{\"scheme\":\"software\",\"supportedAlgorithms\":[\"ES256\"],"
                    + "\"digestSigningAlgorithms\":[],\"canCreate\":false,"
                    + "\"canImport\":false,\"canDelete\":false}",
                MediaType.APPLICATION_JSON,
            ),
        )
        server.expect { request ->
            assertEquals("/v1/keys", request.uri.path)
        }.andRespond(
            withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"detail\":\"Unknown signing key\"}"),
        )

        val error = assertThrows(RuntimeException::class.java) {
            SignatureClient(
                SigningProviderClient(builder, IssuerProperties()),
                configurationClient,
            ).forIssuer("issuer")
        }

        assertEquals(
            "Could not resolve signing key 'software://issuer-key' for issuer 'issuer' "
                + "from provider 'http://provider.example'",
            error.message,
        )
        server.verify()
    }
}
