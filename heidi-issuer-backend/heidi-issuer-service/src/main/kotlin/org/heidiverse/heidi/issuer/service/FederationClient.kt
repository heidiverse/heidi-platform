// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

/**
 * Obtains a credential issuer's OpenID Federation entity configuration.
 *
 * The issuer contributes its metadata; the platform checks the entity belongs to the identity and
 * signs it through the signing service with the identity's federation key.
 */
@Service
class FederationClient(builder: RestClient.Builder, private val properties: IssuerProperties) {
    private val client = builder.baseUrl(properties.platformInternalBaseUrl).let { clientBuilder ->
        if (properties.platformBasicAuth.isNotBlank()) {
            clientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic ${properties.platformBasicAuth}")
        }
        clientBuilder.build()
    }

    /** The signed entity configuration, or null when the identity is in no federation. */
    fun entityConfiguration(issuerSlug: String, entityId: String, metadata: Any): String? {
        val request = mapOf(
            "entityId" to entityId,
            "metadata" to mapOf("openid_credential_issuer" to metadata),
        )
        return try {
            client.post()
                .uri(ENTITY_CONFIGURATION_PATH, issuerSlug)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String::class.java)
        } catch (exception: HttpClientErrorException) {
            if (exception.statusCode != HttpStatus.NOT_FOUND) throw exception
            null
        }
    }

    private companion object {
        const val ENTITY_CONFIGURATION_PATH =
            "/internal/platform/v1/identities/{issuerSlug}/federation/entity-configuration"
    }
}
