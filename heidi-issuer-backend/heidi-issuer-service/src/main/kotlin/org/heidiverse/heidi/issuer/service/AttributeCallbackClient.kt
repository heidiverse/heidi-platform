// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/** Resolves claims referenced by the SSI process token at credential-issuance time. */
@Service
class AttributeCallbackClient(
    builder: RestClient.Builder,
    private val tokenDecoder: ProcessTokenDecoder,
) {
    private val client = builder.build()

    fun resolve(data: CredentialData): CredentialData {
        val callbackUrl = data.attributeUrl?.takeIf { it.isNotBlank() } ?: return data
        val body = client.get()
            .uri(callbackUrl)
            .retrieve()
            .body(String::class.java)
            ?: throw AttributeCallbackException("Attribute callback returned an empty response")
        val callbackData = runCatching { tokenDecoder.decodeIssuanceData(body, data.issuerSlug) }
            .getOrElse { throw AttributeCallbackException("Invalid attribute callback response", it) }
        if (callbackData.credentialIdentifier != data.credentialIdentifier ||
            callbackData.credentialVersion != data.credentialVersion
        ) {
            throw AttributeCallbackException(
                "Attribute callback credential does not match ${data.credentialIdentifier} ${data.credentialVersion}",
            )
        }
        return data.copy(attributes = callbackData.attributes)
    }
}

class AttributeCallbackException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
