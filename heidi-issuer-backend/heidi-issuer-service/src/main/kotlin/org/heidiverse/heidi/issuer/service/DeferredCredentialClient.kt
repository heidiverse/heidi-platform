// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.util.UUID

@Service
class DeferredCredentialClient(
    builder: RestClient.Builder,
    private val tokenDecoder: ProcessTokenDecoder,
    properties: IssuerProperties,
) {
    private val client = builder
        .baseUrl(properties.platformInternalBaseUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic ${properties.platformBasicAuth}")
        .build()

    fun status(transactionId: UUID): DeferredCredentialStatus {
        val body = client.get()
            .uri("/v1/deferred-credential/status/{transactionId}", transactionId)
            .retrieve()
            .body(String::class.java)
            ?: error("Coordinator returned an empty deferred status")
        return DeferredCredentialStatus.valueOf(body.trim().removeSurrounding("\""))
    }

    fun credentialPayload(transactionId: UUID, issuerSlug: String?): DeferredCredentialPayload {
        val body = client.get()
            .uri("/v1/deferred-credential/attribute/{transactionId}", transactionId)
            .retrieve()
            .body(String::class.java)
            ?: error("Coordinator returned empty deferred issuance data")
        return DeferredCredentialPayload(body, tokenDecoder.decodeIssuanceData(body, issuerSlug))
    }

    fun invalidate(transactionId: UUID) {
        client.post()
            .uri("/v1/deferred-credential/{transactionId}/invalidate", transactionId)
            .retrieve()
            .toBodilessEntity()
    }
}

data class DeferredCredentialPayload(
    val encodedData: String,
    val credentialData: CredentialData,
)

enum class DeferredCredentialStatus {
    PENDING,
    READY,
    ERROR,
    INVALIDATED,
}
