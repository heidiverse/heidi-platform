// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.kapunsdk.credentials.W3C
import org.kapunsdk.credentials.toClaimsPointer
import org.kapunsdk.util.extensions.toCbor
import org.kapunsdk.util.extensions.toValue
import uniffi.kapun_credential_core_rust.SignatureCreator

class KapunW3cTest {
    private val signer = object : SignatureCreator {
        override fun alg() = "ES256"
        override fun sign(bytes: ByteArray) = ByteArray(64)
    }

    @Test
    fun `creates a W3C credential with selectively disclosed subject claims`() {
        val claims = hashMapOf<String, Any?>(
            "@context" to listOf("https://www.w3.org/ns/credentials/v2"),
            "type" to listOf("VerifiableCredential", "ExampleCredential"),
            "issuer" to "https://issuer.example",
            "validFrom" to "2026-08-07T12:00:00Z",
            "credentialSubject" to hashMapOf("givenName" to "Ada"),
        )

        val claimsValue = claims.toCbor().toUnorderedValue()
        val disclosures = listOfNotNull(listOf("credentialSubject", "givenName").toClaimsPointer())
        val credential = W3C.SdJwt.create(
            claimsValue,
            disclosures,
            "test-key",
            signer,
            null,
            "sha-256",
        )

        assertNotNull(credential)
    }
}
