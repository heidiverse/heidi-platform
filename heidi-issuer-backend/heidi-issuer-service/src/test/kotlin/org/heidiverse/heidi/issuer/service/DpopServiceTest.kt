// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.heidiverse.heidi.issuer.model.EnforcementMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

class DpopServiceTest {
    private val uri = "https://issuer.example/token"
    private val key: ECKey = ECKeyGenerator(com.nimbusds.jose.jwk.Curve.P_256).generate()

    @Test
    fun `optional mode keeps bearer clients compatible`() {
        val service = service(EnforcementMode.OPTIONAL)

        assertNull(service.verifyTokenEndpointProof(null, uri, "POST"))
    }

    @Test
    fun `required mode challenges a missing proof with a nonce`() {
        val service = service(EnforcementMode.REQUIRED)

        val exception = assertThrows(DpopException::class.java) {
            service.verifyTokenEndpointProof(null, uri, "POST")
        }

        assertEquals("invalid_dpop_proof", exception.errorCode)
        requireNotNull(exception.nonce)
    }

    @Test
    fun `valid nonce-bound proof is accepted once`() {
        val service = service(EnforcementMode.OPTIONAL)
        val proof = proof(uri, service.issueNonce())

        assertEquals(
            key.toPublicJWK().computeThumbprint().toString(),
            service.verifyTokenEndpointProof(proof, uri, "POST"),
        )
        assertThrows(DpopException::class.java) {
            service.verifyTokenEndpointProof(proof, uri, "POST")
        }
    }

    @Test
    fun `resource proof binds access token and proof key`() {
        val service = service(EnforcementMode.OPTIONAL)
        val accessToken = "encrypted-access-token"
        val expectedJkt = key.toPublicJWK().computeThumbprint().toString()
        val proof = proof("https://issuer.example/credential", service.issueNonce(), accessToken)

        assertEquals(
            expectedJkt,
            service.verifyResourceProof(
                proof, "https://issuer.example/credential", "POST", accessToken, expectedJkt,
            ),
        )
    }

    @Test
    fun `resource proof accepts padded access token hash`() {
        val service = service(EnforcementMode.OPTIONAL)
        val accessToken = "encrypted-access-token"
        val expectedJkt = key.toPublicJWK().computeThumbprint().toString()
        val proof = proof(
            "https://issuer.example/credential",
            service.issueNonce(),
            accessToken,
            athEncoder = Base64.getUrlEncoder(),
        )

        assertEquals(
            expectedJkt,
            service.verifyResourceProof(
                proof, "https://issuer.example/credential", "POST", accessToken, expectedJkt,
            ),
        )
    }

    @Test
    fun `proof with a forged signature is rejected`() {
        val service = service(EnforcementMode.OPTIONAL)
        val forged = tamper(proof(uri, service.issueNonce()))

        val exception = assertThrows(DpopException::class.java) {
            service.verifyTokenEndpointProof(forged, uri, "POST")
        }

        assertEquals("invalid_dpop_proof", exception.errorCode)
        assertEquals("Invalid DPoP proof signature", exception.message)
    }

    @Test
    fun `disabled mode rejects a supplied proof`() {
        val service = service(EnforcementMode.DISABLED)

        assertThrows(DpopException::class.java) {
            service.verifyTokenEndpointProof("unexpected", uri, "POST")
        }
    }

    @Test
    fun `proof for another method is rejected`() {
        val service = service(EnforcementMode.OPTIONAL)
        val proof = proof(uri, service.issueNonce())

        val exception = assertThrows(DpopException::class.java) {
            service.verifyTokenEndpointProof(proof, uri, "GET")
        }

        assertEquals("invalid_dpop_proof", exception.errorCode)
    }

    @Test
    fun `proof for another origin is rejected`() {
        val service = service(EnforcementMode.OPTIONAL)
        val proof = proof("https://other-issuer.example/token", service.issueNonce())

        val exception = assertThrows(DpopException::class.java) {
            service.verifyTokenEndpointProof(proof, uri, "POST")
        }

        assertEquals("invalid_dpop_proof", exception.errorCode)
    }

    @Test
    fun `htu comparison ignores the default port and the query string`() {
        val service = service(EnforcementMode.OPTIONAL)
        val proof = proof("https://Issuer.example:443/token?grant_type=x", service.issueNonce())

        assertEquals(
            key.toPublicJWK().computeThumbprint().toString(),
            service.verifyTokenEndpointProof(proof, uri, "POST"),
        )
    }

    @Test
    fun `forged nonce is rejected`() {
        val service = service(EnforcementMode.OPTIONAL)
        val forged = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(40))

        val exception = assertThrows(DpopException::class.java) {
            service.verifyTokenEndpointProof(proof(uri, forged), uri, "POST")
        }

        assertEquals("use_dpop_nonce", exception.errorCode)
        requireNotNull(exception.nonce)
    }

    @Test
    fun `expired nonce is rejected`() {
        val service = service(EnforcementMode.OPTIONAL, nonceLifetimeSeconds = 0)

        val exception = assertThrows(DpopException::class.java) {
            service.verifyTokenEndpointProof(proof(uri, service.issueNonce()), uri, "POST")
        }

        assertEquals("use_dpop_nonce", exception.errorCode)
    }

    @Test
    fun `a nonce is not accepted by an instance with a different key`() {
        val nonce = service(EnforcementMode.OPTIONAL).issueNonce()

        val exception = assertThrows(DpopException::class.java) {
            service(EnforcementMode.OPTIONAL).verifyTokenEndpointProof(proof(uri, nonce), uri, "POST")
        }

        assertEquals("use_dpop_nonce", exception.errorCode)
    }

    @Test
    fun `a nonce is accepted by another instance sharing the configured key`() {
        val sharedKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        val nonce = service(EnforcementMode.OPTIONAL, nonceKey = sharedKey).issueNonce()

        assertEquals(
            key.toPublicJWK().computeThumbprint().toString(),
            service(EnforcementMode.OPTIONAL, nonceKey = sharedKey)
                .verifyTokenEndpointProof(proof(uri, nonce), uri, "POST"),
        )
    }

    private fun service(
        mode: EnforcementMode,
        nonceLifetimeSeconds: Long = 300,
        nonceKey: String = "",
    ) = DpopService(
        IssuerProperties(
            dpopProofMaxAgeSeconds = 300,
            dpopNonceLifetimeSeconds = nonceLifetimeSeconds,
            dpopNonceKey = nonceKey,
        ),
        EnforcementProperties(dpop = mode),
    )

    private fun proof(
        htu: String,
        nonce: String,
        accessToken: String? = null,
        athEncoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding(),
    ): String {
        val claims = JWTClaimsSet.Builder()
            .jwtID(UUID.randomUUID().toString())
            .issueTime(Date.from(Instant.now()))
            .claim("htm", "POST")
            .claim("htu", htu)
            .claim("nonce", nonce)
            .apply {
                accessToken?.let {
                    claim(
                        "ath",
                        athEncoder.encodeToString(
                            MessageDigest.getInstance("SHA-256").digest(it.encodeToByteArray()),
                        ),
                    )
                }
            }
            .build()
        return SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(JOSEObjectType("dpop+jwt"))
                .jwk(key.toPublicJWK())
                .build(),
            claims,
        ).apply { sign(ECDSASigner(key)) }.serialize()
    }

    private fun tamper(jwt: String): String {
        val parts = jwt.split(".").toMutableList()
        val signature = parts[2].toCharArray()
        val index = signature.size / 2
        signature[index] = if (signature[index] == 'A') 'B' else 'A'
        parts[2] = String(signature)
        return parts.joinToString(".")
    }
}
