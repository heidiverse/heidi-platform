// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import uniffi.heidi_signing.authenticate
import uniffi.heidi_signing.generateSeed
import uniffi.heidi_signing.publicKey

class SignerInterfaceTest {
    @Test
    fun `creates Ed25519ph authentication material with the shared signer module`() {
        val provider = "jwkSignerService"
        val seed = generateSeed()
        val publicKey = publicKey(seed, null, provider)
        val signature = authenticate(
            seed,
            null,
            ByteArray(32) { it.toByte() },
            provider,
            "issuer-key",
            "timestamp:hash".encodeToByteArray(),
        )

        assertEquals(32, seed.size)
        assertEquals(32, publicKey.size)
        assertEquals(64, signature.size)
    }
}
