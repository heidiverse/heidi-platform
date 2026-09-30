// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class TransactionCodeServiceTest {
    private val properties = IssuerProperties(
        transactionCodeMasterKey = "4afa8db01ec9fa40a852332b0f6bcbf709c749e102d71d789af3876e72d925d2",
    )
    private val service = TransactionCodeService(properties)

    @Test
    fun `verifies coordinator-compatible encrypted transaction code`() {
        val credentialIdentifier = "student-card"
        val encrypted = encrypt("482913", credentialIdentifier)

        assertDoesNotThrow { service.verify(encrypted, credentialIdentifier, "482913") }
        assertThrows(IllegalArgumentException::class.java) {
            service.verify(encrypted, credentialIdentifier, "482914")
        }
    }

    private fun encrypt(value: String, salt: String): String {
        val key = hkdf(
            BigInteger(properties.transactionCodeMasterKey, 16).toByteArray(),
            salt.encodeToByteArray(),
            "SSI_ISSUER_ENCRYPTION_KEY".encodeToByteArray(),
            32,
        )
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(iv + cipher.doFinal(value.encodeToByteArray()))
    }

    private fun hkdf(input: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val pseudoRandomKey = mac.doFinal(input)
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val count = minOf(previous.size, length - offset)
            previous.copyInto(output, offset, 0, count)
            offset += count
            counter++
        }
        return output
    }
}
