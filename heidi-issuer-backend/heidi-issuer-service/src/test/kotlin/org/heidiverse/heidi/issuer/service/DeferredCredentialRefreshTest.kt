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
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.web.client.RestClient
import java.time.Instant
import java.util.Base64
import java.util.Optional
import java.util.UUID

class DeferredCredentialRefreshTest {
    @Test
    fun `new credential request uses encrypted deferred data after token refresh`() {
        val sessions = mock(IssuanceSessionRepository::class.java)
        val metadata = mock(SchemaMetadataService::class.java)
        val credentialIssuer = mock(KapunCredentialIssuer::class.java)
        val deferredCredentials = mock(DeferredCredentialClient::class.java)
        val decoder = ProcessTokenDecoder()
        val attributeCallbacks = AttributeCallbackClient(RestClient.builder(), decoder)
        val crypto = SessionCryptoService(
            SessionEncryptionProperties("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
        )
        val transactionId = UUID.randomUUID()
        val sessionKey = crypto.newSessionKey()
        val entity = IssuanceSessionEntity().apply {
            signingSnapshot = IssuerFlowSnapshot().encode()
            offerExpiresAt = Instant.now().plusSeconds(3600)
            id = UUID.randomUUID()
            connectionId = "connection"
            issuerSlug = "local"
            variant = FlowVariant.C
            issuanceProfileId = "CUSTOM_ISSUANCE_2026_1"
            credentialIdentifier = "test"
            credentialVersion = "1.0.0"
            status = IssuanceStatus.CREDENTIAL_ISSUED
            accessTokenExpiresAt = Instant.now().plusSeconds(60)
        }
        val accessToken = crypto.issueToken(TokenType.ACCESS, entity.id, sessionKey, entity.accessTokenExpiresAt)
        entity.accessTokenDigest = crypto.digest(accessToken)
        val encodedDeferredData = """
            {
              "schemaIdentifier":{"credentialIdentifier":"test","version":"1.0.0"},
              "attributes":{"name":{"value":"Refreshed Ada","attributeType":"STRING","isArray":false}}
            }
        """.trimIndent()
        crypto.writeSecrets(
            entity,
            SessionSecrets(
                processToken = processToken(transactionId),
                deferredCredentialData = encodedDeferredData,
            ),
            sessionKey,
        )
        `when`(sessions.findByAccessTokenDigest(crypto.digest(accessToken))).thenReturn(Optional.of(entity))
        `when`(metadata.resolve("test", "1.0.0")).thenReturn(
            SchemaMetadata("urn:test", "org.example.test", JsonNull, setOf(CredentialFormat.SD_JWT), null),
        )
        val decodedDeferredData = decoder.decodeIssuanceData(encodedDeferredData, "local")
        val holderJwk = """{"kty":"EC"}"""
        `when`(
            credentialIssuer.issueSdJwt(
                decodedDeferredData,
                "local",
                "https://issuer.example/local/c/test/1.0.0",
                "urn:test",
                holderJwk,
                IssuerFlowSnapshot(),
            ),
        ).thenReturn("freshly-signed-credential")
        val properties = IssuerProperties(
            publicUrl = "https://issuer.example",
            credentialRequestEncryptionRequired = false,
            credentialResponseEncryptionRequired = false,
        )
        val configurationClient = mock(IssuerConfigurationClient::class.java)
        `when`(configurationClient.resolveCredentialEncryption("local", "CUSTOM_ISSUANCE_2026_1"))
            .thenReturn(CredentialEncryptionPolicy())
        val enforcement = EnforcementProperties(dpop = EnforcementMode.OPTIONAL)
        val service = IssuanceService(
            sessions,
            decoder,
            metadata,
            credentialIssuer,
            mock(SignatureClient::class.java),
            mock(org.heidiverse.heidi.issuer.service.bbs.BbsCredentialIssuer::class.java),
            DpopService(IssuerProperties(), enforcement),
            enforcement,
            crypto,
            mock(TransactionCodeService::class.java),
            deferredCredentials,
            attributeCallbacks,
            properties,
            CredentialEncryptionService(properties, configurationClient),
            mock(IssuerFlowService::class.java),
        )

        val result = service.issue(
            accessToken,
            """{"credential_configuration_id":"test-1.0.0-sd-jwt","jwk":$holderJwk}""",
            "Bearer",
            null,
            "POST",
        )

        assertEquals(200, result.status)
        assertEquals("freshly-signed-credential", result.body.credential)
        assertEquals(IssuanceStatus.CREDENTIAL_ISSUED, entity.status)
        verifyNoInteractions(deferredCredentials)
    }

    private fun processToken(transactionId: UUID): String {
        val payload = """
            {
              "data": {
                "action":"pre_auth_issuance",
                "deferredTransactionId":"$transactionId",
                "data": {
                  "schemaIdentifier":{"credentialIdentifier":"test","version":"1.0.0"},
                  "issuerSlug":"local",
                  "attributes":{}
                }
              }
            }.signature
        """.trimIndent()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.encodeToByteArray())
    }
}
