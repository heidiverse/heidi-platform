// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.heidiverse.heidi.issuer.model.entity.IssuanceSessionEntity
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.HexFormat
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Envelope encryption for sensitive session state and opaque access/refresh tokens. */
@Service
class SessionCryptoService(properties: SessionEncryptionProperties) {
    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()
    private val masterKey = runCatching { HexFormat.of().parseHex(properties.encryptionMasterKey) }
        .getOrElse { throw IllegalArgumentException("heidi.issuer.session.encryption-master-key must be hex", it) }
        .also { require(it.size == 32) { "heidi.issuer.session.encryption-master-key must contain 32 bytes" } }

    fun newSessionKey(): ByteArray = ByteArray(32).also(random::nextBytes)

    fun writeSecrets(entity: IssuanceSessionEntity, secrets: SessionSecrets, sessionKey: ByteArray?) {
        val plaintext = buildJsonObject {
            secrets.processToken?.let { put("processToken", it) }
            secrets.encryptedTxCode?.let { put("encryptedTxCode", it) }
            secrets.credentialRequest?.let { put("credentialRequest", it) }
            secrets.deferredCredentialData?.let { put("deferredCredentialData", it) }
        }.toString()
        val aad = sessionAad(entity.id)
        entity.encryptedSession = if (sessionKey == null) {
            encrypt(plaintext, masterKey, aad)
        } else {
            encrypt(plaintext, sessionKey, aad)
        }
        entity.isEncryptedWithSessionKey = sessionKey != null
        // Remove legacy plaintext once it has been captured in the encrypted envelope.
        entity.processToken = null
    }

    fun readSecrets(entity: IssuanceSessionEntity, sessionKey: ByteArray? = null): SessionSecrets {
        val encrypted = entity.encryptedSession
        if (encrypted == null) {
            return SessionSecrets(
                entity.processToken,
            )
        }
        val key = if (entity.isEncryptedWithSessionKey) {
            sessionKey ?: throw IllegalArgumentException("A valid access or refresh token is required for session data")
        } else {
            masterKey
        }
        return decodeSecrets(decrypt(encrypted, key, sessionAad(entity.id)))
    }

    fun issueToken(type: TokenType, sessionId: UUID, sessionKey: ByteArray, expiresAt: Instant): String {
        val payload = buildJsonObject {
            put("v", 1)
            put("typ", type.value)
            put("sid", sessionId.toString())
            put("sk", Base64.getUrlEncoder().withoutPadding().encodeToString(sessionKey))
            put("exp", expiresAt.epochSecond)
            put("jti", UUID.randomUUID().toString())
        }.toString()
        return encrypt(payload, masterKey, tokenAad(type))
    }

    fun decodeToken(token: String, expectedType: TokenType): TokenEnvelope {
        val root = runCatching {
            json.parseToJsonElement(decrypt(token, masterKey, tokenAad(expectedType))).jsonObject
        }.getOrElse { throw IllegalArgumentException("Invalid ${expectedType.value} token", it) }
        require(root.stringOrNull("typ") == expectedType.value) { "Invalid token type" }
        val expiresAt = Instant.ofEpochSecond(root.getValue("exp").jsonPrimitive.content.toLong())
        require(expiresAt.isAfter(Instant.now())) { "${expectedType.value.replaceFirstChar(Char::uppercase)} token has expired" }
        return TokenEnvelope(
            sessionId = UUID.fromString(root.getValue("sid").jsonPrimitive.content),
            sessionKey = Base64.getUrlDecoder().decode(root.getValue("sk").jsonPrimitive.content),
            expiresAt = expiresAt,
        )
    }

    fun digest(token: String): String = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(token.encodeToByteArray()),
    )

    private fun encrypt(plaintext: String, keyBytes: ByteArray, aad: ByteArray): String {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext.encodeToByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(byteArrayOf(1) + iv + ciphertext)
    }

    private fun decrypt(value: String, keyBytes: ByteArray, aad: ByteArray): String {
        val bytes = Base64.getUrlDecoder().decode(value)
        require(bytes.size > 29 && bytes[0] == 1.toByte()) { "Invalid encrypted value" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(128, bytes.copyOfRange(1, 13)),
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(bytes.copyOfRange(13, bytes.size)).decodeToString()
    }

    private fun decodeSecrets(plaintext: String): SessionSecrets {
        val root = json.parseToJsonElement(plaintext).jsonObject
        return SessionSecrets(
            processToken = root.stringOrNull("processToken"),
            encryptedTxCode = root.stringOrNull("encryptedTxCode"),
            credentialRequest = root.stringOrNull("credentialRequest"),
            deferredCredentialData = root.stringOrNull("deferredCredentialData"),
        )
    }

    private fun sessionAad(sessionId: UUID) = "heidi-session:$sessionId:v1".encodeToByteArray()
    private fun tokenAad(type: TokenType) = "heidi-${type.value}-token:v1".encodeToByteArray()

    private fun kotlinx.serialization.json.JsonObject.stringOrNull(name: String): String? =
        this[name]?.jsonPrimitive?.content

}

data class SessionSecrets(
    var processToken: String? = null,
    var encryptedTxCode: String? = null,
    var credentialRequest: String? = null,
    var deferredCredentialData: String? = null,
)

enum class TokenType(val value: String) {
    ACCESS("access"),
    REFRESH("refresh"),
}

data class TokenEnvelope(
    val sessionId: UUID,
    val sessionKey: ByteArray,
    val expiresAt: Instant,
)
