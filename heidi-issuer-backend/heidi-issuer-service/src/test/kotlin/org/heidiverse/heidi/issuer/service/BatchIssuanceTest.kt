// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.heidiverse.heidi.issuer.data.IssuanceSessionRepository
import org.heidiverse.heidi.issuer.model.EnforcementMode
import org.heidiverse.heidi.issuer.model.FlowVariant
import org.heidiverse.heidi.issuer.model.IssuanceStatus
import org.heidiverse.heidi.issuer.model.TrustSystem
import org.heidiverse.heidi.issuer.model.api.CredentialResponse
import org.heidiverse.heidi.issuer.model.api.CredentialOfferRequest
import org.heidiverse.heidi.issuer.model.api.IssuerMetadata
import org.heidiverse.heidi.issuer.model.entity.IssuanceSessionEntity
import org.heidiverse.heidi.shared.signing.SigningKeyException
import org.heidiverse.heidi.issuer.service.SchemaMetadataService.CredentialFormat
import org.heidiverse.heidi.issuer.service.SchemaMetadataService.SchemaMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.ArgumentMatchers.any
import org.springframework.web.client.RestClient
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.Optional
import java.util.UUID

class BatchIssuanceTest {
    private companion object {
        const val PROFILE = "EUDI_ISSUANCE_2026_1"
    }
    private val snapshot = IssuerFlowSnapshot()

    @Test
    fun `rejects an offer without an issuance profile`() {
        val encryption = CredentialEncryptionPolicy(
            responseEncValues = listOf("A128GCM", "A256GCM"),
            responseEncryptionRequired = true,
        )
        val fixture = fixture(encryptionPolicy = encryption, includeProfile = false)
        val error = assertThrows(IllegalArgumentException::class.java) {
            fixture.service.createOffer(
                "local", FlowVariant.C, CredentialOfferRequest(fixture.processToken, null),
            )
        }
        assertEquals("Issuance profile is required", error.message)
    }

    @Test
    fun `Swiss metadata advertises credential endpoint renewal`() {
        val fixture = fixture(serverBatchSize = 1, issuanceProfileId = "SWISS_ISSUANCE_2026_1")

        val metadata = fixture.service.credentialMetadata(
            "local", FlowVariant.C, "test", "1.0.0", "SWISS_ISSUANCE_2026_1",
        )

        assertEquals("swiss-profile-issuance:1.0.0", metadata.profileVersion)
        assertEquals(
            false,
            metadata.credentialConfigurationsSupported.getValue("test-1.0.0-sd-jwt")
                .credentialRefreshDisabled,
        )
    }

    @Test
    fun `non Swiss metadata omits credential endpoint renewal flag`() {
        val fixture = fixture(serverBatchSize = 1)

        val metadata = fixture.service.credentialMetadata(
            "local", FlowVariant.C, "test", "1.0.0", PROFILE,
        )

        assertNull(
            metadata.credentialConfigurationsSupported.getValue("test-1.0.0-sd-jwt")
                .credentialRefreshDisabled,
        )
        assertNull(metadata.profileVersion)
    }

    @Test
    fun `offer pins signing material before returning to wallet`() {
        val fixture = fixture()
        fixture.service.createOffer("local", FlowVariant.C, CredentialOfferRequest(fixture.processToken, null))
        val saved = org.mockito.ArgumentCaptor.forClass(IssuanceSessionEntity::class.java)
        verify(fixture.sessions, org.mockito.Mockito.atLeastOnce()).save(saved.capture())
        assertTrue(saved.value.signingSnapshot != null)
        assertTrue(saved.value.signingExpiresAt.isAfter(saved.value.offerExpiresAt))
        verify(fixture.signingFlows).retain(
            "local", saved.value.id, snapshot.copy(issuanceProfileId = PROFILE),
            saved.value.signingExpiresAt,
        )
    }

    @Test
    fun `issuance uses the offer certificate and issuer after renewal`() {
        val fixture = fixture()
        val pinned = IssuerFlowSnapshot(
            signing = IssuerProperties.IssuerSigning(keyUri = "software://kc/old/version", certificateChain = listOf("old")),
            issuerClaim = "did:example:original",
        )
        fixture.session.signingSnapshot = pinned.encode()
        val key = ECKeyGenerator(com.nimbusds.jose.jwk.Curve.P_256).generate()
        `when`(fixture.credentialIssuer.issueSdJwt(fixture.data, "local", "did:example:original",
            "urn:test", key.toPublicJWK().toJSONString(), pinned)).thenReturn("original-version-credential")

        val result = fixture.service.issue(fixture.accessToken,
            """{"credential_configuration_id":"test-1.0.0-sd-jwt","proof":{"jwt":"${proof(key)}"}}""",
            "Bearer", null, "POST")

        assertEquals("original-version-credential", result.body.credential)
        verify(fixture.signatureClient, never()).issuerClaim("local", TrustSystem.Default, "test", "1.0.0")
    }

    @Test
    fun `newly advertised decrypt keys are retained without renewing old references`() {
        val old = CredentialEncryptionKey("old", "software://kc/decrypt/v1", "ECDH-ES", "{}")
        val added = old.copy(keyId = "new", keyUri = "software://kc/decrypt/v2")
        val fixture = fixture(encryptionPolicy = CredentialEncryptionPolicy(requestKeys = listOf(added)))
        val original = snapshot.copy(encryption = CredentialEncryptionPolicy(requestKeys = listOf(old)))
        fixture.session.signingSnapshot = original.encode()
        val merged = original.copy(encryption = original.encryption.copy(requestKeys = listOf(old, added)))
        val key = ECKeyGenerator(com.nimbusds.jose.jwk.Curve.P_256).generate()
        `when`(fixture.credentialIssuer.issueSdJwt(fixture.data, "local",
            "https://issuer.example/local/c/test/1.0.0", "urn:test", key.toPublicJWK().toJSONString(), merged))
            .thenReturn("credential")

        fixture.service.issue(fixture.accessToken,
            """{"credential_configuration_id":"test-1.0.0-sd-jwt","proof":{"jwt":"${proof(key)}"}}""",
            "Bearer", null, "POST")

        verify(fixture.signingFlows).retain("local", fixture.session.id,
            snapshot.copy(encryption = CredentialEncryptionPolicy(requestKeys = listOf(added))),
            fixture.session.signingExpiresAt)
        assertEquals(merged, IssuerFlowSnapshot.decode(fixture.session.signingSnapshot))
    }

    @Test
    fun `proof with a forged signature is rejected`() {
        val fixture = fixture()
        val key = ECKeyGenerator(com.nimbusds.jose.jwk.Curve.P_256).generate()
        val forged = tamper(proof(key))

        val error = assertThrows(IllegalArgumentException::class.java) {
            fixture.service.issue(fixture.accessToken,
                """{"credential_configuration_id":"test-1.0.0-sd-jwt","proof":{"jwt":"$forged"}}""",
                "Bearer", null, "POST")
        }

        assertEquals("Invalid proof JWT signature", error.message)
    }

    @Test
    fun `expired offers cannot mint fresh tokens`() {
        val fixture = fixture()
        fixture.service.createOffer("local", FlowVariant.C, CredentialOfferRequest(fixture.processToken, null))
        val saved = org.mockito.ArgumentCaptor.forClass(IssuanceSessionEntity::class.java)
        verify(fixture.sessions, org.mockito.Mockito.atLeastOnce()).save(saved.capture())
        val offer = saved.value
        offer.offerExpiresAt = Instant.now().minusSeconds(1)
        `when`(fixture.sessions.findByPreAuthorizedCode(offer.preAuthorizedCode)).thenReturn(Optional.of(offer))

        val failure = assertThrows(Oid4vciProtocolException::class.java) {
            fixture.service.token(mapOf("grant_type" to "urn:ietf:params:oauth:grant-type:pre-authorized_code",
                "pre-authorized_code" to offer.preAuthorizedCode), null, "POST")
        }
        assertEquals("invalid_grant", failure.errorCode)
        assertEquals("The pre-authorized code is invalid, expired, or already used", failure.message)
    }

    @Test
    fun `failed flow release is retried before marking the session`() {
        val fixture = fixture()
        `when`(fixture.sessions.finishedSigningFlows(any())).thenReturn(listOf(fixture.session))
        org.mockito.Mockito.doThrow(IllegalStateException("provider unavailable")).doNothing()
            .`when`(fixture.signingFlows).release(fixture.session.id)

        fixture.service.releaseFinishedFlows()
        assertFalse(fixture.session.isSigningFlowReleased)
        fixture.service.releaseFinishedFlows()
        assertTrue(fixture.session.isSigningFlowReleased)
        verify(fixture.signingFlows, org.mockito.Mockito.times(2)).release(fixture.session.id)
    }

    @Test
    fun `fails before creating an offer when the issuer key cannot be resolved`() {
        val fixture = fixture()
        val failure = SigningKeyException(
            "Could not resolve signing key 'software://issuer-key' for issuer 'local' "
                + "from provider 'http://provider.example'",
        )
        doThrow(failure).`when`(fixture.signatureClient).validateKeyAccess(
            "local",
            TrustSystem.Default,
            "test",
            "1.0.0",
            PROFILE,
        )

        val error = assertThrows(SigningKeyException::class.java) {
            fixture.service.createOffer(
                "local",
                FlowVariant.C,
                CredentialOfferRequest(fixture.processToken, null),
            )
        }

        assertEquals(failure, error)
        verify(fixture.sessions, never()).save(any(IssuanceSessionEntity::class.java))
    }

    @Test
    fun `fails before creating a BBS offer when BBS signing is unavailable`() {
        val fixture = fixture(supportedFormats = setOf(CredentialFormat.ZKP_VC))
        `when`(fixture.bbsCredentialIssuer.isAvailable(
            "local", "test", "1.0.0", TrustSystem.Default, PROFILE,
        ))
            .thenReturn(false)

        val error = assertThrows(IllegalArgumentException::class.java) {
            fixture.service.createOffer(
                "local",
                FlowVariant.C,
                CredentialOfferRequest(fixture.processToken, null),
            )
        }

        assertEquals("BBS credential issuance is not configured for 'test' version '1.0.0'", error.message)
        verify(fixture.sessions, never()).save(any(IssuanceSessionEntity::class.java))
    }

    @Test
    fun `issues bbs without resolving an ordinary signer`() {
        val fixture = fixture(supportedFormats = setOf(CredentialFormat.ZKP_VC))
        val key = ECKeyGenerator(com.nimbusds.jose.jwk.Curve.P_256).generate()
        val holderJwk = key.toPublicJWK().toJSONString()
        `when`(
            fixture.bbsCredentialIssuer.issue(
                fixture.data,
                "local",
                "urn:test",
                holderJwk,
                snapshot,
                TrustSystem.Default,
            ),
        ).thenReturn("bbs-credential")

        val result = fixture.service.issue(
            fixture.accessToken,
            """{"credential_configuration_id":"test-1.0.0-zkp-vc","proof":{"jwt":"${proof(key)}"}}""",
            "Bearer",
            null,
            "POST",
        )

        assertEquals("bbs-credential", result.body.credential)
        verify(fixture.signatureClient, never()).issuerClaim(
            "local",
            TrustSystem.Default,
            "test",
            "1.0.0",
        )
    }

    @Test
    fun `publishes bbs metadata without resolving an ordinary signer`() {
        val fixture = fixture(supportedFormats = setOf(CredentialFormat.ZKP_VC))
        `when`(fixture.bbsCredentialIssuer.isAvailable(
            "local", "test", "1.0.0", issuanceProfileId = PROFILE,
        ))
            .thenReturn(true)

        val metadata = fixture.service.credentialMetadata(
            "local",
            FlowVariant.C,
            "test",
            "1.0.0",
            PROFILE,
        )

        assert(metadata.credentialConfigurationsSupported.containsKey("test-1.0.0-zkp-vc"))
        verify(fixture.signatureClient, never()).configurationsFor("local", "test", "1.0.0")
        verify(fixture.signatureClient, never()).configurationFor("local")
    }

    @Test
    fun `uses the configured namespace when issuing an mdoc`() {
        val fixture = fixture(
            supportedFormats = setOf(CredentialFormat.MSO_MDOC),
            docType = "org.example.doctype",
            namespace = "org.example.namespace",
        )
        val key = ECKeyGenerator(com.nimbusds.jose.jwk.Curve.P_256).generate()
        val holderJwk = key.toPublicJWK().toJSONString()
        `when`(
            fixture.credentialIssuer.issueMdoc(
                fixture.data,
                "local",
                "org.example.doctype",
                "org.example.namespace",
                holderJwk,
                snapshot,
                TrustSystem.Default,
            ),
        ).thenReturn("mdoc-credential")

        val result = fixture.service.issue(
            fixture.accessToken,
            """{"credential_configuration_id":"test-1.0.0-mso-mdoc","proof":{"jwt":"${proof(key)}"}}""",
            "Bearer",
            null,
            "POST",
        )

        assertEquals("mdoc-credential", result.body.credential)
    }

    @Test
    fun `issues one credential per proof up to the schema batch size`() {
        val fixture = fixture(serverBatchSize = 2)
        val keys = List(2) { ECKeyGenerator(com.nimbusds.jose.jwk.Curve.P_256).generate() }
        keys.forEachIndexed { index, key ->
            `when`(
                fixture.credentialIssuer.issueSdJwt(
                    fixture.data,
                    "local",
                    "https://issuer.example/local/c/test/1.0.0",
                    "urn:test",
                    key.toPublicJWK().toJSONString(),
                    snapshot,
                ),
            ).thenReturn("credential-${index + 1}")
        }

        val result = fixture.service.issue(
            fixture.accessToken,
            batchRequest(keys),
            "Bearer",
            null,
            "POST",
        )

        assertEquals(200, result.status)
        assertEquals(
            listOf(
                CredentialResponse.IssuedCredential("credential-1"),
                CredentialResponse.IssuedCredential("credential-2"),
            ),
            result.body.credentials,
        )
        assertEquals(IssuanceStatus.CREDENTIAL_ISSUED, fixture.session.status)
    }

    @Test
    fun `rejects proof batches larger than the schema limit`() {
        val fixture = fixture(serverBatchSize = 2)
        val keys = List(3) { ECKeyGenerator(com.nimbusds.jose.jwk.Curve.P_256).generate() }

        val error = assertThrows(IllegalArgumentException::class.java) {
            fixture.service.issue(
                fixture.accessToken,
                batchRequest(keys),
                "Bearer",
                null,
                "POST",
            )
        }

        assertEquals("Requested batch size 3 exceeds the credential schema maximum 2", error.message)
    }

    @Test
    fun `announces the server batch ceiling in issuer metadata`() {
        val fixture = fixture()

        val metadata = fixture.service.credentialMetadata(
            "local", FlowVariant.C, "test", "1.0.0", PROFILE,
        )

        assertEquals(IssuerMetadata.BatchCredentialIssuance(20), metadata.batchCredentialIssuance)
    }

    @Test
    fun `omits batch metadata when the server ceiling is one`() {
        val fixture = fixture(serverBatchSize = 1)

        val metadata = fixture.service.credentialMetadata(
            "local", FlowVariant.C, "test", "1.0.0", PROFILE,
        )

        assertEquals(null, metadata.batchCredentialIssuance)
    }

    @Test
    fun `omits response encryption when request encryption is unavailable`() {
        val fixture = fixture()

        val metadata = fixture.service.credentialMetadata(
            "local", FlowVariant.C, "test", "1.0.0", PROFILE,
        )

        assertEquals(null, metadata.credentialRequestEncryption)
        assertEquals(null, metadata.credentialResponseEncryption)
    }

    @Test
    fun `publishes simple style as OID4VCI credential metadata`() {
        val display = SchemaMetadataService.CredentialDisplay(
            name = "Example Card",
            locale = "de-CH",
            backgroundColor = "#112233",
            backgroundImageUri = "data:image/png;base64,AAAA",
            textColor = "#FFFFFF",
        )
        val fixture = fixture(credentialDisplay = display)

        val metadata = fixture.service.credentialMetadata(
            "local", FlowVariant.C, "test", "1.0.0", PROFILE,
        )
        val configuration = metadata.credentialConfigurationsSupported.getValue("test-1.0.0-sd-jwt")

        assertEquals(
            IssuerMetadata.CredentialMetadata(listOf(display.toOid4vci())),
            configuration.credentialMetadata,
        )
    }

    @Test
    fun `publishes configured jwt vc issuer jwks unchanged`() {
        val fixture = fixture()

        val metadata = fixture.service.jwtVcIssuerMetadata("local", FlowVariant.C, "test", "1.0.0")
        val key = metadata.jwks.keys.single()

        assertFalse(key.containsKey("kid"))
        assertFalse(key.containsKey("use"))
        assertEquals("x-value", key["x"])
    }

    @Test
    fun `signed Swiss metadata contains its issued-at claim`() {
        val fixture = fixture()
        val configuration = IssuerProperties.IssuerSigning(
            keyId = "local",
            issuerJwk = """{"kty":"EC","kid":"did:webvh:example.org#local"}""",
        )
        val signer = object : SignatureClient.KapunsSignaturCreator("local", configuration) {
            override fun alg() = "ES256"
            override fun sign(bytes: ByteArray) = ByteArray(64)
        }
        `when`(
            fixture.signatureClient.effectiveTrustSystem(
                "local", TrustSystem.Switzerland, "test", "1.0.0",
                PROFILE,
            ),
        ).thenReturn(TrustSystem.Switzerland)
        `when`(fixture.signatureClient.swissTrustConfiguration("local")).thenReturn(
            IssuerConfigurationClient.SwissTrustConfiguration(
                "did:webvh:example.org", null, emptyMap(),
            ),
        )
        `when`(
            fixture.signatureClient.forTrust(
                "local", TrustSystem.Switzerland, "test", "1.0.0",
                PROFILE,
            ),
        ).thenReturn(signer)

        val before = Instant.now().epochSecond
        val payload = fixture.service.signedCredentialMetadata(
            "local", FlowVariant.C, "test", "1.0.0", TrustSystem.Switzerland,
            PROFILE,
        ).split('.')[1]
        val iat = Json.parseToJsonElement(
            Base64.getUrlDecoder().decode(payload).decodeToString(),
        ).jsonObject.getValue("iat").jsonPrimitive.content.toLong()

        assertTrue(iat in before..Instant.now().epochSecond)
    }

    @Test
    fun `signed Swiss metadata qualifies its header kid with the Swiss DID`() {
        val fixture = fixture()
        val configuration = IssuerProperties.IssuerSigning(
            keyId = "assert-key-01",
            issuerJwk = """{"kty":"EC","kid":"assert-key-01"}""",
        )
        val signer = object : SignatureClient.KapunsSignaturCreator("local", configuration) {
            override fun alg() = "ES256"
            override fun sign(bytes: ByteArray) = ByteArray(64)
        }
        `when`(
            fixture.signatureClient.effectiveTrustSystem(
                "local", TrustSystem.Default, "test", "1.0.0",
                PROFILE,
            ),
        ).thenReturn(TrustSystem.Switzerland)
        `when`(fixture.signatureClient.swissTrustConfiguration("local")).thenReturn(
            IssuerConfigurationClient.SwissTrustConfiguration(
                "did:webvh:example.org", null, emptyMap(),
            ),
        )
        `when`(
            fixture.signatureClient.forTrust(
                "local", TrustSystem.Default, "test", "1.0.0",
                PROFILE,
            ),
        ).thenReturn(signer)

        val header = fixture.service.signedCredentialMetadata(
            "local", FlowVariant.C, "test", "1.0.0", TrustSystem.Default,
            PROFILE,
        ).split('.')[0]
        val kid = Json.parseToJsonElement(
            Base64.getUrlDecoder().decode(header).decodeToString(),
        ).jsonObject.getValue("kid").jsonPrimitive.content

        assertEquals("did:webvh:example.org#assert-key-01", kid)
    }

    @Test
    fun `signed issuer metadata subjects the credential issuer from the offer`() {
        val fixture = fixture()
        val configuration = IssuerProperties.IssuerSigning(
            keyId = "local",
            issuerJwk = """{"kty":"EC","kid":"did:webvh:example.org#local"}""",
        )
        val signer = object : SignatureClient.KapunsSignaturCreator("local", configuration) {
            override fun alg() = "ES256"
            override fun sign(bytes: ByteArray) = ByteArray(64)
        }
        `when`(
            fixture.signatureClient.effectiveTrustSystem(
                "local", TrustSystem.Default, "test", "1.0.0",
                PROFILE,
            ),
        ).thenReturn(TrustSystem.Default)
        `when`(
            fixture.signatureClient.forTrust(
                "local", TrustSystem.Default, "test", "1.0.0",
                PROFILE,
            ),
        ).thenReturn(signer)

        val offer = fixture.service.createOffer(
            "local",
            FlowVariant.C,
            CredentialOfferRequest(fixture.processToken, null),
        )
        val payload = fixture.service.signedCredentialMetadata(
            "local", FlowVariant.C, "test", "1.0.0", TrustSystem.Default, PROFILE,
        ).split('.')[1]
        val subject = Json.parseToJsonElement(
            Base64.getUrlDecoder().decode(payload).decodeToString(),
        ).jsonObject.getValue("sub").jsonPrimitive.content

        assertEquals(offer.credentialIssuer, subject)
    }

    @Test
    fun `signed authorization metadata uses the issuer metadata signing format`() {
        val fixture = fixture()
        val configuration = IssuerProperties.IssuerSigning(
            keyId = "local",
            issuerJwk = """{"kty":"EC","kid":"did:webvh:example.org#local"}""",
        )
        val signer = object : SignatureClient.KapunsSignaturCreator("local", configuration) {
            override fun alg() = "ES256"
            override fun sign(bytes: ByteArray) = ByteArray(64)
        }
        `when`(
            fixture.signatureClient.effectiveTrustSystem(
                "local", TrustSystem.Default, "test", "1.0.0",
                PROFILE,
            ),
        ).thenReturn(TrustSystem.Default)
        `when`(
            fixture.signatureClient.forTrust(
                "local", TrustSystem.Default, "test", "1.0.0",
                PROFILE,
            ),
        ).thenReturn(signer)

        val metadata = fixture.service.authorizationMetadata(
            "local", FlowVariant.C, "test", "1.0.0",
        )
        assertEquals(listOf("openid_credential"), metadata.authorizationDetailsTypesSupported)
        val parts = fixture.service.signedAuthorizationMetadata(
            "local", "test", "1.0.0", metadata,
            PROFILE,
        ).split('.')
        val header = Json.parseToJsonElement(
            Base64.getUrlDecoder().decode(parts[0]).decodeToString(),
        ).jsonObject
        val payload = Json.parseToJsonElement(
            Base64.getUrlDecoder().decode(parts[1]).decodeToString(),
        ).jsonObject

        assertEquals("openidvci-issuer-metadata+jwt", header.getValue("typ").jsonPrimitive.content)
        assertEquals("ES256", header.getValue("alg").jsonPrimitive.content)
        assertEquals(metadata.issuer, payload.getValue("issuer").jsonPrimitive.content)
        assertEquals(metadata.issuer, payload.getValue("sub").jsonPrimitive.content)
        assertTrue(payload.getValue("iat").jsonPrimitive.content.toLong() > 0)
    }

    private fun fixture(
        serverBatchSize: Int = 20,
        credentialDisplay: SchemaMetadataService.CredentialDisplay? = null,
        supportedFormats: Set<CredentialFormat> = setOf(CredentialFormat.SD_JWT),
        docType: String = "org.example.test",
        namespace: String = docType,
        encryptionPolicy: CredentialEncryptionPolicy = CredentialEncryptionPolicy(),
        issuanceProfileId: String = PROFILE,
        includeProfile: Boolean = true,
    ): Fixture {
        val sessions = mock(IssuanceSessionRepository::class.java)
        val metadata = mock(SchemaMetadataService::class.java)
        val credentialIssuer = mock(KapunCredentialIssuer::class.java)
        val signatureClient = mock(SignatureClient::class.java)
        val decoder = ProcessTokenDecoder()
        val crypto = SessionCryptoService(
            SessionEncryptionProperties("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
        )
        val sessionKey = crypto.newSessionKey()
        val session = IssuanceSessionEntity().apply {
            signingSnapshot = snapshot.encode()
            offerExpiresAt = Instant.now().plusSeconds(3600)
            id = UUID.randomUUID()
            connectionId = "connection"
            issuerSlug = "local"
            variant = FlowVariant.C
            credentialIdentifier = "test"
            credentialVersion = "1.0.0"
            status = IssuanceStatus.ACCESS_TOKEN_ISSUED
            this.issuanceProfileId = issuanceProfileId
            accessTokenExpiresAt = Instant.now().plusSeconds(60)
            signingExpiresAt = Instant.now().plusSeconds(3600)
        }
        val accessToken = crypto.issueToken(TokenType.ACCESS, session.id, sessionKey, session.accessTokenExpiresAt)
        session.accessTokenDigest = crypto.digest(accessToken)
        val processToken = processToken(includeProfile)
        val data = decoder.decode(processToken)
        crypto.writeSecrets(session, SessionSecrets(processToken = processToken), sessionKey)
        `when`(sessions.findByAccessTokenDigest(crypto.digest(accessToken))).thenReturn(Optional.of(session))
        `when`(sessions.save(any())).thenAnswer { invocation ->
            invocation.getArgument<IssuanceSessionEntity>(0).apply {
                if (id == null) id = UUID.randomUUID()
            }
        }
        `when`(metadata.resolve("test", "1.0.0")).thenReturn(
            SchemaMetadata(
                vct = "urn:test",
                docType = docType,
                typeMetadata = JsonNull,
                supportedFormats = supportedFormats,
                bbsCredentialType = null,
                maxBatchSize = serverBatchSize,
                credentialDisplay = credentialDisplay,
                namespace = namespace,
            ),
        )
        val signing = IssuerProperties.IssuerSigning(
            keyId = "issuer-key",
            algorithm = "ES256",
            issuerJwk = """{"kty":"EC","crv":"P-256","x":"x-value","y":"y-value"}""",
        )
        `when`(signatureClient.configurationFor("local")).thenReturn(signing)
        `when`(signatureClient.configurationsFor(
            "local", "test", "1.0.0", issuanceProfileId,
        )).thenReturn(listOf(signing))
        `when`(signatureClient.publicJwksFor("local"))
            .thenReturn(listOf(signing.issuerJwk))
        val bbsCredentialIssuer = mock(org.heidiverse.heidi.issuer.service.bbs.BbsCredentialIssuer::class.java)
        val properties = IssuerProperties(
            publicUrl = "https://issuer.example",
            credentialRequestEncryptionRequired = false,
            credentialResponseEncryptionRequired = false,
        )
        val enforcement = EnforcementProperties(dpop = EnforcementMode.OPTIONAL)
        val signingFlows = mock(IssuerFlowService::class.java)
        `when`(signingFlows.capture("local", TrustSystem.Default, "test", "1.0.0",
            supportedFormats, encryptionPolicy, issuanceProfileId))
            .thenReturn(snapshot.copy(issuanceProfileId = issuanceProfileId, encryption = encryptionPolicy))
        val configurationClient = mock(IssuerConfigurationClient::class.java)
        `when`(configurationClient.resolveCredentialEncryption("local", issuanceProfileId))
            .thenReturn(encryptionPolicy)
        val service = IssuanceService(
            sessions,
            decoder,
            metadata,
            credentialIssuer,
            signatureClient,
            bbsCredentialIssuer,
            DpopService(properties, enforcement),
            enforcement,
            crypto,
            mock(TransactionCodeService::class.java),
            mock(DeferredCredentialClient::class.java),
            AttributeCallbackClient(RestClient.builder(), decoder),
            properties,
            CredentialEncryptionService(properties, configurationClient),
            signingFlows,
        )
        return Fixture(
            service, sessions, credentialIssuer, signatureClient, bbsCredentialIssuer, signingFlows,
            session, accessToken, data, processToken,
        )
    }

    private fun processToken(includeProfile: Boolean = true): String {
        val payload = """
            {
              "data": {
                "action":"pre_auth_issuance",
                "data": {
                  "schemaIdentifier":{"credentialIdentifier":"test","version":"1.0.0"},
                  "issuerSlug":"local",
                  ${if (includeProfile) "\"issuanceProfileId\":\"$PROFILE\"," else ""}
                  "values":{"name":"Ada"}
                }
              }
            }.signature
        """.trimIndent()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.encodeToByteArray())
    }

    private fun batchRequest(keys: List<ECKey>): String {
        val proofs = keys.joinToString(",") { key -> "\"${proof(key)}\"" }
        return """{"credential_configuration_id":"test-1.0.0-sd-jwt","proofs":{"jwt":[$proofs]}}"""
    }

    private fun tamper(jwt: String): String {
        val parts = jwt.split(".").toMutableList()
        val signature = parts[2].toCharArray()
        val index = signature.size / 2
        signature[index] = if (signature[index] == 'A') 'B' else 'A'
        parts[2] = String(signature)
        return parts.joinToString(".")
    }

    private fun proof(key: ECKey): String = SignedJWT(
        JWSHeader.Builder(JWSAlgorithm.ES256).jwk(key.toPublicJWK()).build(),
        JWTClaimsSet.Builder()
            .jwtID(UUID.randomUUID().toString())
            .issueTime(Date.from(Instant.now()))
            .build(),
    ).apply { sign(ECDSASigner(key)) }.serialize()

    private data class Fixture(
        val service: IssuanceService,
        val sessions: IssuanceSessionRepository,
        val credentialIssuer: KapunCredentialIssuer,
        val signatureClient: SignatureClient,
        val bbsCredentialIssuer: org.heidiverse.heidi.issuer.service.bbs.BbsCredentialIssuer,
        val signingFlows: IssuerFlowService,
        val session: IssuanceSessionEntity,
        val accessToken: String,
        val data: CredentialData,
        val processToken: String,
    )
}
