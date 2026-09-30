// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.springframework.stereotype.Service
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Decrypts transaction codes produced by the coordinator's TokenSignatureService. */
@Service
class TransactionCodeService(private val properties: IssuerProperties) {
    fun verify(encryptedCode: String, credentialIdentifier: String, suppliedCode: String) {
        val expected = decrypt(encryptedCode, credentialIdentifier)
        require(
            MessageDigest.isEqual(expected.encodeToByteArray(), suppliedCode.encodeToByteArray()),
        ) { "Invalid transaction code" }
    }

    private fun decrypt(value: String, salt: String): String {
        val key = hkdf(
            BigInteger(properties.transactionCodeMasterKey, 16).toByteArray(),
            salt.encodeToByteArray(),
            "SSI_ISSUER_ENCRYPTION_KEY".encodeToByteArray(),
            32,
        )
        val encrypted = Base64.getUrlDecoder().decode(value)
        require(encrypted.size > 28) { "Invalid encrypted transaction code" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, encrypted.copyOfRange(0, 12)),
        )
        return cipher.doFinal(encrypted.copyOfRange(12, encrypted.size)).toString(StandardCharsets.UTF_8)
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
