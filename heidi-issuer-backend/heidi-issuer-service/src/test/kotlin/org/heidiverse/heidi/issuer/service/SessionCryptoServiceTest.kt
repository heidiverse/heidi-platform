// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.heidiverse.heidi.issuer.model.entity.IssuanceSessionEntity
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SessionCryptoServiceTest {
    private val crypto = SessionCryptoService(
        SessionEncryptionProperties("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
    )

    @Test
    fun `moves sensitive session state out of plaintext columns`() {
        val entity = IssuanceSessionEntity().apply {
            id = UUID.randomUUID()
            processToken = "process-token"
        }

        crypto.writeSecrets(
            entity,
            SessionSecrets(processToken = entity.processToken),
            null,
        )

        assertNull(entity.processToken)
        assertFalse(entity.isEncryptedWithSessionKey)
        assertFalse(entity.encryptedSession.contains("process-token"))
        assertEquals("process-token", crypto.readSecrets(entity).processToken)
    }

    @Test
    fun `token carries the per-session key inside a master-key envelope`() {
        val sessionId = UUID.randomUUID()
        val sessionKey = crypto.newSessionKey()
        val token = crypto.issueToken(TokenType.REFRESH, sessionId, sessionKey, Instant.now().plusSeconds(60))

        val decoded = crypto.decodeToken(token, TokenType.REFRESH)

        assertEquals(sessionId, decoded.sessionId)
        assertArrayEquals(sessionKey, decoded.sessionKey)
        assertEquals(64, crypto.digest(token).length)
        assertFalse(token.contains(sessionId.toString()))
        assertThrows(IllegalArgumentException::class.java) {
            crypto.decodeToken(token, TokenType.ACCESS)
        }
    }

    @Test
    fun `session data can be re-encrypted with the rotating session key`() {
        val entity = IssuanceSessionEntity().apply { id = UUID.randomUUID() }
        val firstKey = crypto.newSessionKey()
        val secondKey = crypto.newSessionKey()
        crypto.writeSecrets(
            entity,
            SessionSecrets(
                processToken = "process-token",
                deferredCredentialData = """{"schemaIdentifier":{"credentialIdentifier":"test"}}""",
            ),
            firstKey,
        )

        val secrets = crypto.readSecrets(entity, firstKey)
        crypto.writeSecrets(entity, secrets, secondKey)

        assertTrue(entity.isEncryptedWithSessionKey)
        assertEquals("process-token", crypto.readSecrets(entity, secondKey).processToken)
        assertEquals(
            """{"schemaIdentifier":{"credentialIdentifier":"test"}}""",
            crypto.readSecrets(entity, secondKey).deferredCredentialData,
        )
        assertThrows(Exception::class.java) { crypto.readSecrets(entity, firstKey) }
    }

}
