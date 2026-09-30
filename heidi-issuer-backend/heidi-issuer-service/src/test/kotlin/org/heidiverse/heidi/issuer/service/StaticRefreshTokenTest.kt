// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import kotlinx.serialization.json.JsonNull
import org.heidiverse.heidi.issuer.data.IssuanceSessionRepository
import org.heidiverse.heidi.issuer.model.EnforcementMode
import org.heidiverse.heidi.issuer.model.FlowVariant
import org.heidiverse.heidi.issuer.model.IssuanceStatus
import org.heidiverse.heidi.issuer.model.entity.IssuanceSessionEntity
import org.heidiverse.heidi.issuer.service.SchemaMetadataService.CredentialFormat
import org.heidiverse.heidi.issuer.service.SchemaMetadataService.SchemaMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Base64
import java.util.Optional
import java.util.UUID
import org.springframework.web.client.RestClient

class StaticRefreshTokenTest {
    @Test
    fun `used or unknown pre-authorized code returns invalid grant`() {
        val fixture = fixture(processToken(deferred = false))

        val error = assertThrows(Oid4vciProtocolException::class.java) {
            fixture.service.token(
                mapOf(
                    "grant_type" to IssuanceService.PRE_AUTH_GRANT,
                    "pre-authorized_code" to "already-used",
                ),
                null,
                HTTP_METHOD,
            )
        }

        assertEquals("invalid_grant", error.errorCode)
        assertEquals("The pre-authorized code is invalid, expired, or already used", error.message)
    }

    @Test
    fun `expired pre-authorized code returns invalid grant`() {
        val fixture = fixture(processToken(deferred = false))
        fixture.entity.offerExpiresAt = java.time.Instant.EPOCH

        val error = assertThrows(Oid4vciProtocolException::class.java) {
            fixture.service.token(preAuthorizedGrant(), null, HTTP_METHOD)
        }

        assertEquals("invalid_grant", error.errorCode)
        assertEquals("The pre-authorized code is invalid, expired, or already used", error.message)
    }

    @Test
    fun `deferred issuance returns its static refresh token only until credential delivery`() {
        val fixture = fixture(processToken(deferred = true), serverBatchSize = 1)
        val initial = fixture.service.token(preAuthorizedGrant(), null, HTTP_METHOD)
        val refreshToken = initial.refreshToken!!
        val preDeliveryRefresh = fixture.service.token(refreshGrant(refreshToken), null, HTTP_METHOD)
        fixture.entity.status = IssuanceStatus.CREDENTIAL_ISSUED

        val firstRefresh = fixture.service.token(refreshGrant(refreshToken), null, HTTP_METHOD)
        val secondRefresh = fixture.service.token(refreshGrant(refreshToken), null, HTTP_METHOD)

        assertEquals(refreshToken, preDeliveryRefresh.refreshToken)
        assertNull(firstRefresh.refreshToken)
        assertNull(secondRefresh.refreshToken)
        assertNotEquals(initial.accessToken, preDeliveryRefresh.accessToken)
        assertNotEquals(preDeliveryRefresh.accessToken, firstRefresh.accessToken)
        assertNotEquals(firstRefresh.accessToken, secondRefresh.accessToken)
        assertEquals(fixture.crypto.digest(refreshToken), fixture.entity.refreshTokenDigest)
        assertEquals(3, fixture.entity.refreshTokenUsageCount)
        assertEquals(IssuanceStatus.CREDENTIAL_ISSUED, fixture.entity.status)
    }

    @Test
    fun `immediate single credential issuance does not issue a refresh token`() {
        val fixture = fixture(processToken(deferred = false), serverBatchSize = 1)

        val response = fixture.service.token(preAuthorizedGrant(), null, HTTP_METHOD)

        assertNull(response.refreshToken)
        assertNull(fixture.entity.refreshTokenDigest)
        assertNull(fixture.entity.refreshTokenExpiresAt)
    }

    @Test
    fun `batch issuance receives a refresh token`() {
        val fixture = fixture(processToken(deferred = false))

        val response = fixture.service.token(preAuthorizedGrant(), null, HTTP_METHOD)

        response.refreshToken!!
    }

    private fun fixture(processToken: String, serverBatchSize: Int = 20): Fixture {
        val sessions = mock(IssuanceSessionRepository::class.java)
        val crypto = SessionCryptoService(
            SessionEncryptionProperties("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
        )
        val entity = IssuanceSessionEntity().apply {
            signingSnapshot = IssuerFlowSnapshot().encode()
            offerExpiresAt = java.time.Instant.now().plusSeconds(3600)
            id = UUID.randomUUID()
            preAuthorizedCode = "pre-authorized-code"
            issuerSlug = "local"
            variant = FlowVariant.C
            credentialIdentifier = "test"
            credentialVersion = "1.0.0"
        }
        crypto.writeSecrets(entity, SessionSecrets(processToken = processToken), null)
        `when`(sessions.findByPreAuthorizedCode("pre-authorized-code")).thenReturn(Optional.of(entity))
        `when`(sessions.findByRefreshTokenDigest(anyString())).thenReturn(Optional.of(entity))
        val properties = IssuerProperties()
        val enforcement = EnforcementProperties(dpop = EnforcementMode.OPTIONAL)
        val metadata = mock(SchemaMetadataService::class.java)
        `when`(metadata.resolve("test", "1.0.0")).thenReturn(
            SchemaMetadata("urn:test", "org.example.test", JsonNull, setOf(CredentialFormat.SD_JWT), null, serverBatchSize),
        )
        val service = IssuanceService(
            sessions,
            ProcessTokenDecoder(),
            metadata,
            mock(KapunCredentialIssuer::class.java),
            mock(SignatureClient::class.java),
            mock(org.heidiverse.heidi.issuer.service.bbs.BbsCredentialIssuer::class.java),
            DpopService(properties, enforcement),
            enforcement,
            crypto,
            mock(TransactionCodeService::class.java),
            mock(DeferredCredentialClient::class.java),
            mock(AttributeCallbackClient::class.java),
            properties,
            CredentialEncryptionService(properties, IssuerConfigurationClient(RestClient.builder(), properties)),
            mock(IssuerFlowService::class.java),
        )
        return Fixture(service, crypto, entity)
    }

    private fun processToken(deferred: Boolean): String {
        val transaction = if (deferred) "\"deferredTransactionId\":\"${UUID.randomUUID()}\"," else ""
        val payload = """
            {
              "data": {
                "action":"pre_auth_issuance",
                $transaction
                "data": {
                  "schemaIdentifier":{"credentialIdentifier":"test","version":"1.0.0"},
                  "issuerSlug":"local",
                  "values":{}
                }
              }
            }.signature
        """.trimIndent()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.encodeToByteArray())
    }

    private fun preAuthorizedGrant() = mapOf(
        "grant_type" to IssuanceService.PRE_AUTH_GRANT,
        "pre-authorized_code" to "pre-authorized-code",
    )

    private fun refreshGrant(refreshToken: String) =
        mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken)

    private data class Fixture(
        val service: IssuanceService,
        val crypto: SessionCryptoService,
        val entity: IssuanceSessionEntity,
    )

    private companion object {
        const val HTTP_METHOD = "POST"
    }
}
