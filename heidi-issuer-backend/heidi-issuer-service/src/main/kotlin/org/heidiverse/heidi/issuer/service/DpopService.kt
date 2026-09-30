// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.JWK
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kapunsdk.crypto.jwt.Jwt
import org.kapunsdk.crypto.jwt.JwtValidator
import org.heidiverse.heidi.issuer.model.EnforcementMode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.LinkedHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC 9449 proof validation and server-provided nonce management. */
@Service
class DpopService(
    private val properties: IssuerProperties,
    private val enforcement: EnforcementProperties,
) {
    /**
     * Seen 'jkt:jti' pairs, bounded so that an unauthenticated flood cannot exhaust the heap.
     * Entries are evicted oldest-first, which only ever drops proofs that are closest to falling
     * out of the 'iat' acceptance window anyway.
     */
    private val replayCache = object : LinkedHashMap<String, Instant>(256, 0.75f, false) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Instant>): Boolean =
            size > MAX_REPLAY_ENTRIES
    }

    @Volatile
    private var lastSweep: Instant = Instant.EPOCH

    /**
     * Nonces are stateless: an authenticated expiry stamp rather than a server-side set. This keeps
     * the unauthenticated nonce endpoint from growing an unbounded map, and lets every instance
     * validate a nonce minted by any other instance as long as they share 'dpop-nonce-key'.
     */
    private val nonceKey: SecretKeySpec = SecretKeySpec(resolveNonceKey(), HMAC_ALGORITHM)

    fun issueNonce(): String {
        val payload = ByteBuffer.allocate(NONCE_PAYLOAD_BYTES)
            .putLong(Instant.now().epochSecond + properties.dpopNonceLifetimeSeconds)
            .put(ByteArray(NONCE_SALT_BYTES).also(secureRandom::nextBytes))
            .array()
        return base64Url(payload + mac(payload))
    }

    fun isEnabled(): Boolean = enforcement.dpop != EnforcementMode.DISABLED

    /** Returns the proof's public JWK thumbprint, or null for an allowed Bearer request. */
    fun verifyTokenEndpointProof(
        proof: String?,
        htu: String,
        htm: String,
        expectedJkt: String? = null,
    ): String? = verifyWithPolicy(proof, htu, htm, accessToken = null, expectedJkt, resourceRequest = false)

    fun verifyResourceProof(
        proof: String?,
        htu: String,
        htm: String,
        accessToken: String,
        expectedJkt: String?,
    ): String? = verifyWithPolicy(proof, htu, htm, accessToken, expectedJkt, resourceRequest = true)

    private fun verifyWithPolicy(
        proof: String?,
        htu: String,
        htm: String,
        accessToken: String?,
        expectedJkt: String?,
        resourceRequest: Boolean,
    ): String? {
        if (enforcement.dpop == EnforcementMode.DISABLED) {
            if (!proof.isNullOrBlank()) {
                throw DpopException("invalid_dpop_proof", "DPoP is disabled", unauthorized = resourceRequest)
            }
            return null
        }
        val required = enforcement.dpop == EnforcementMode.REQUIRED || expectedJkt != null
        if (proof.isNullOrBlank()) {
            if (!required) return null
            throw DpopException(
                "invalid_dpop_proof",
                "DPoP proof header is required",
                issueNonce(),
                unauthorized = resourceRequest,
            )
        }
        val actualJkt = try {
            verifyProof(proof, htu, htm, accessToken)
        } catch (exception: MissingDpopNonceException) {
            throw DpopException(
                "use_dpop_nonce",
                exception.message ?: "A valid DPoP nonce is required",
                issueNonce(),
                unauthorized = resourceRequest,
            )
        } catch (exception: IllegalArgumentException) {
            throw DpopException(
                "invalid_dpop_proof",
                exception.message ?: "Invalid DPoP proof",
                issueNonce(),
                unauthorized = resourceRequest,
            )
        }
        if (expectedJkt != null &&
            !MessageDigest.isEqual(expectedJkt.encodeToByteArray(), actualJkt.encodeToByteArray())
        ) {
            throw DpopException(
                "invalid_dpop_proof",
                "DPoP proof key does not match the token binding",
                issueNonce(),
                unauthorized = resourceRequest,
            )
        }
        return actualJkt
    }

    private fun verifyProof(proof: String, htu: String, htm: String, accessToken: String?): String {
        val jwt = Jwt(proof, JwtValidator(), System.currentTimeMillis())
        val header = runCatching { jwt.getHeader().jsonObject }
            .getOrElse { throw IllegalArgumentException("Invalid DPoP proof JWT", it) }
        require(header.string("alg") == "ES256") { "Unsupported DPoP signing algorithm" }
        val jwk = runCatching {
            header["jwk"]?.let { JWK.parse(it.toString()) }
        }.getOrNull() ?: throw IllegalArgumentException("DPoP proof header has no jwk")
        require(!jwk.isPrivate) { "DPoP proof jwk must be public" }
        val ecKey = runCatching { jwk.toECKey() }
            .getOrElse { throw IllegalArgumentException("DPoP proof requires a P-256 EC key", it) }
        require(ecKey.curve == Curve.P_256) { "DPoP proof requires a P-256 EC key" }
        require(proof.verifyWith(ecKey, "dpop+jwt")) { "Invalid DPoP proof signature" }

        // The payload is read only after Kapun has validated the signature and type.
        val claims = jwt.insecureGetPayload().jsonObject
        require(claims.string("htm") == htm) { "DPoP proof htm does not match the request" }
        require(normalizeHtu(claims.string("htu")) == normalizeHtu(htu)) {
            "DPoP proof htu does not match the request"
        }
        val now = Instant.now()
        val issuedAt = claims.long("iat")?.let(Instant::ofEpochSecond)
            ?: throw IllegalArgumentException("DPoP proof has no iat")
        require(!issuedAt.isAfter(now.plusSeconds(IAT_LEEWAY_SECONDS))) { "DPoP proof iat is in the future" }
        require(!issuedAt.isBefore(now.minusSeconds(properties.dpopProofMaxAgeSeconds))) { "DPoP proof has expired" }
        val jti = claims.string("jti")?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("DPoP proof has no jti")

        val nonce = claims.string("nonce")
            ?: throw MissingDpopNonceException("DPoP proof has no nonce")
        if (!isValidNonce(nonce, now)) {
            throw MissingDpopNonceException("Invalid or expired DPoP nonce")
        }
        if (accessToken != null) {
            val expectedAth = sha256Base64Url(accessToken)
            // Accept padded Base64URL from clients; the access-token digest is unchanged.
            val actualAth = claims.string("ath")?.trimEnd('=')
            require(actualAth != null && MessageDigest.isEqual(expectedAth.encodeToByteArray(), actualAth.encodeToByteArray())) {
                "DPoP proof ath does not match the access token"
            }
        } else {
            require(claims["ath"] == null || claims["ath"] is JsonNull) {
                "Token endpoint DPoP proof must not contain ath"
            }
        }

        val jkt = jwk.computeThumbprint().toString()
        sweepIfDue(now)
        require(!wasSeenBefore("$jkt:$jti", now)) { "DPoP proof has already been used" }
        return jkt
    }

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.long(name: String): Long? =
        this[name]?.jsonPrimitive?.content?.toLongOrNull()

    /** Records the proof identifier and reports whether it was already recorded and still fresh. */
    private fun wasSeenBefore(proofId: String, now: Instant): Boolean = synchronized(replayCache) {
        val seenUntil = replayCache[proofId]
        if (seenUntil != null && seenUntil.isAfter(now)) {
            true
        } else {
            replayCache[proofId] = now.plusSeconds(properties.dpopProofMaxAgeSeconds)
            false
        }
    }

    private fun isValidNonce(nonce: String, now: Instant): Boolean {
        val decoded = runCatching { Base64.getUrlDecoder().decode(nonce) }.getOrNull() ?: return false
        if (decoded.size != NONCE_PAYLOAD_BYTES + MAC_BYTES) return false
        val payload = decoded.copyOfRange(0, NONCE_PAYLOAD_BYTES)
        if (!MessageDigest.isEqual(mac(payload), decoded.copyOfRange(NONCE_PAYLOAD_BYTES, decoded.size))) {
            return false
        }
        return ByteBuffer.wrap(payload).long > now.epochSecond
    }

    private fun mac(payload: ByteArray): ByteArray = Mac.getInstance(HMAC_ALGORITHM)
        .apply { init(nonceKey) }
        .doFinal(payload)
        .copyOfRange(0, MAC_BYTES)

    private fun resolveNonceKey(): ByteArray {
        val configured = properties.dpopNonceKey.takeIf { it.isNotBlank() } ?: run {
            logger.warn(
                "heidi.issuer.dpop-nonce-key is not configured; DPoP nonces are signed with a key " +
                    "generated for this process only. Clients will be challenged again after a restart, " +
                    "and nonces are not accepted across instances until the key is configured.",
            )
            return ByteArray(NONCE_KEY_BYTES).also(secureRandom::nextBytes)
        }
        val decoded = runCatching { Base64.getDecoder().decode(configured) }.getOrElse {
            throw IllegalStateException("heidi.issuer.dpop-nonce-key must be base64-encoded", it)
        }
        check(decoded.size >= NONCE_KEY_BYTES) {
            "heidi.issuer.dpop-nonce-key must decode to at least $NONCE_KEY_BYTES bytes"
        }
        return decoded
    }

    /**
     * Canonicalises an HTTP URI for comparison: scheme and host are case-insensitive, the default
     * port for the scheme is implicit, and query and fragment are excluded per RFC 9449 section 4.3.
     */
    private fun normalizeHtu(value: String?): String {
        val uri = runCatching { URI(value ?: "") }
            .getOrElse { throw IllegalArgumentException("DPoP proof contains an invalid htu", it) }
        require(uri.isAbsolute && uri.host != null) { "DPoP proof htu must be an absolute HTTP URI" }
        val scheme = uri.scheme.lowercase()
        require(scheme == "http" || scheme == "https") { "DPoP proof htu must be an HTTP URI" }
        require(uri.rawUserInfo == null) { "DPoP proof htu must not contain user information" }
        val defaultPort = if (scheme == "https") 443 else 80
        val port = if (uri.port == -1 || uri.port == defaultPort) "" else ":${uri.port}"
        return "$scheme://${uri.host.lowercase()}$port${uri.rawPath.ifEmpty { "/" }}"
    }

    private fun sweepIfDue(now: Instant) {
        if (now.isBefore(lastSweep.plusSeconds(SWEEP_INTERVAL_SECONDS))) return
        lastSweep = now
        synchronized(replayCache) {
            replayCache.entries.removeIf { !it.value.isAfter(now) }
        }
    }

    private fun sha256Base64Url(value: String): String = base64Url(
        MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray()),
    )

    private fun base64Url(value: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private companion object {
        private val logger = LoggerFactory.getLogger(DpopService::class.java)
        private val secureRandom = SecureRandom()
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val NONCE_KEY_BYTES = 32
        const val NONCE_SALT_BYTES = 16
        const val NONCE_PAYLOAD_BYTES = Long.SIZE_BYTES + NONCE_SALT_BYTES
        const val MAC_BYTES = 16
        const val MAX_REPLAY_ENTRIES = 100_000
        const val SWEEP_INTERVAL_SECONDS = 30L
        const val IAT_LEEWAY_SECONDS = 5L
    }
}

class DpopException(
    val errorCode: String,
    override val message: String,
    val nonce: String? = null,
    val unauthorized: Boolean = false,
) : IllegalArgumentException(message)

private class MissingDpopNonceException(message: String) : IllegalArgumentException(message)
