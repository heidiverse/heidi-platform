// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

//package org.heidiverse.heidi.issuer.service
//
//import com.nimbusds.jose.JOSEObjectType
//import com.nimbusds.jose.JWSAlgorithm
//import com.nimbusds.jose.JWSHeader
//import com.nimbusds.jose.crypto.ECDSASigner
//import com.nimbusds.jose.jwk.Curve
//import com.nimbusds.jose.jwk.ECKey
//import com.nimbusds.jose.jwk.gen.ECKeyGenerator
//import com.nimbusds.jwt.JWTClaimsSet
//import com.nimbusds.jwt.SignedJWT
//import kotlinx.serialization.json.JsonNull
//import org.heidiverse.heidi.issuer.data.AuthFlowRepository
//import org.heidiverse.heidi.issuer.data.IssuanceSessionRepository
//import org.heidiverse.heidi.issuer.model.EnforcementMode
//import org.heidiverse.heidi.issuer.model.FlowVariant
//import org.heidiverse.heidi.issuer.model.entity.IssuanceSessionEntity
//import org.heidiverse.heidi.issuer.service.SchemaMetadataService.CredentialFormat
//import org.heidiverse.heidi.issuer.service.SchemaMetadataService.SchemaMetadata
//import org.junit.jupiter.api.Assertions.assertEquals
//import org.junit.jupiter.api.Assertions.assertNull
//import org.junit.jupiter.api.Assertions.assertThrows
//import org.junit.jupiter.api.Test
//import org.mockito.Mockito.mock
//import org.mockito.Mockito.`when`
//import java.time.Instant
//import java.util.Base64
//import java.util.Date
//import java.util.Optional
//import java.util.UUID
//
///**
// * An authorization code issued for a 'dpop_jkt' must only be redeemable with that key
// * (RFC 9449 section 10.1), so that a stolen code cannot be bound to an attacker's key.
// */
//class DpopCodeBindingTest {
//    private val walletKey: ECKey = ECKeyGenerator(Curve.P_256).generate()
//    private val attackerKey: ECKey = ECKeyGenerator(Curve.P_256).generate()
//
//    @Test
//    fun `code bound to dpop_jkt cannot be redeemed with another key`() {
//        val fixture = fixture(requestedDpopJkt = thumbprint(walletKey))
//
//        val exception = assertThrows(DpopException::class.java) {
//            fixture.service.token(authorizationCodeGrant(), fixture.proof(attackerKey), "POST")
//        }
//
//        assertEquals("invalid_dpop_proof", exception.errorCode)
//        assertNull(fixture.entity.dpopJkt)
//    }
//
//    @Test
//    fun `code bound to dpop_jkt cannot be redeemed without a proof`() {
//        val fixture = fixture(requestedDpopJkt = thumbprint(walletKey))
//
//        val exception = assertThrows(DpopException::class.java) {
//            fixture.service.token(authorizationCodeGrant(), null, "POST")
//        }
//
//        assertEquals("invalid_dpop_proof", exception.errorCode)
//    }
//
//    @Test
//    fun `code bound to dpop_jkt is redeemed with the announced key`() {
//        val fixture = fixture(requestedDpopJkt = thumbprint(walletKey))
//
//        val response = fixture.service.token(authorizationCodeGrant(), fixture.proof(walletKey), "POST")
//
//        assertEquals("DPoP", response.tokenType)
//        assertEquals(thumbprint(walletKey), fixture.entity.dpopJkt)
//    }
//
//    @Test
//    fun `a proof for another endpoint is rejected`() {
//        val fixture = fixture(requestedDpopJkt = thumbprint(walletKey))
//
//        val exception = assertThrows(DpopException::class.java) {
//            fixture.service.token(
//                authorizationCodeGrant(),
//                fixture.proof(walletKey, htu = "https://issuer.example/local/c/test/1.0.0/credential"),
//                "POST",
//            )
//        }
//
//        assertEquals("invalid_dpop_proof", exception.errorCode)
//    }
//
//    private fun thumbprint(key: ECKey) = key.toPublicJWK().computeThumbprint().toString()
//
//    private fun authorizationCodeGrant() =
//        mapOf("grant_type" to "authorization_code", "code" to "authorization-code")
//
//    private fun fixture(requestedDpopJkt: String): Fixture {
//        val sessions = mock(IssuanceSessionRepository::class.java)
//        val crypto = SessionCryptoService(
//            SessionEncryptionProperties("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
//        )
//        val entity = IssuanceSessionEntity().apply {
//            id = UUID.randomUUID()
//            authorizationCode = "authorization-code"
//            issuerSlug = "local"
//            variant = FlowVariant.C
//            credentialIdentifier = "test"
//            credentialVersion = "1.0.0"
//        }
//        crypto.writeSecrets(
//            entity,
//            SessionSecrets(processToken = processToken(), requestedDpopJkt = requestedDpopJkt),
//            null,
//        )
//        `when`(sessions.findByAuthorizationCode("authorization-code")).thenReturn(Optional.of(entity))
//        val properties = IssuerProperties(publicUrl = "https://issuer.example")
//        val enforcement = EnforcementProperties(dpop = EnforcementMode.OPTIONAL)
//        val dpop = DpopService(properties, enforcement)
//        val metadata = mock(SchemaMetadataService::class.java)
//        `when`(metadata.resolve("test", "1.0.0")).thenReturn(
//            SchemaMetadata("urn:test", "org.example.test", JsonNull, setOf(CredentialFormat.SD_JWT), null, 20),
//        )
//        val service = IssuanceService(
//            sessions,
//            mock(AuthFlowRepository::class.java),
//            ProcessTokenDecoder(),
//            metadata,
//            mock(KapunCredentialIssuer::class.java),
//            mock(SignatureClient::class.java),
//            dpop,
//            enforcement,
//            crypto,
//            mock(TransactionCodeService::class.java),
//            mock(DeferredCredentialClient::class.java),
//            mock(AttributeCallbackClient::class.java),
//            properties,
//            CredentialEncryptionService(properties),
//        )
//        return Fixture(service, entity, dpop)
//    }
//
//    private fun processToken(): String {
//        val payload = """
//            {
//              "data": {
//                "action":"pre_auth_issuance",
//                "data": {
//                  "schemaIdentifier":{"credentialIdentifier":"test","version":"1.0.0"},
//                  "issuerSlug":"local",
//                  "attributes":{}
//                }
//              }
//            }.signature
//        """.trimIndent()
//        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.encodeToByteArray())
//    }
//
//    private data class Fixture(
//        val service: IssuanceService,
//        val entity: IssuanceSessionEntity,
//        val dpop: DpopService,
//    ) {
//        fun proof(
//            key: ECKey,
//            htu: String = "https://issuer.example/local/c/test/1.0.0/token",
//        ): String = SignedJWT(
//            JWSHeader.Builder(JWSAlgorithm.ES256)
//                .type(JOSEObjectType("dpop+jwt"))
//                .jwk(key.toPublicJWK())
//                .build(),
//            JWTClaimsSet.Builder()
//                .jwtID(UUID.randomUUID().toString())
//                .issueTime(Date.from(Instant.now()))
//                .claim("htm", "POST")
//                .claim("htu", htu)
//                .claim("nonce", dpop.issueNonce())
//                .build(),
//        ).apply { sign(ECDSASigner(key)) }.serialize()
//    }
//}
