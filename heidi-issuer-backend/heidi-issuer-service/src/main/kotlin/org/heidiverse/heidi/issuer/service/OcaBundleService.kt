// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.heidiverse.heidi.shared.oca.OcaFormat
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class OcaBundleService(builder: RestClient.Builder, properties: IssuerProperties) {
    private val client = builder.baseUrl(properties.platformInternalBaseUrl).build()

    fun resolve(fileName: String): String = resolve(fileName, null, null)

    fun resolve(fileName: String, format: OcaFormat): String =
        resolve(fileName, format.value(), null)

    fun resolve(fileName: String, format: String?, userAgent: String?): String {
        require(fileName.isNotBlank()) { "OCA bundle filename must not be blank" }
        val bundle = client.get()
            .uri { builder ->
                builder.path("/public/v2/oca/{fileName}")
                if (format != null) {
                    builder.queryParam("format", format)
                } else if (
                    userAgent != null &&
                    OcaFormat.select(null, userAgent) == OcaFormat.SWIYU
                ) {
                    // Preserve the existing swiyu override while forwarding the header.
                    builder.queryParam("format", OcaFormat.SWIYU.value())
                }
                builder.build(fileName)
            }
            .headers { headers ->
                if (userAgent != null) headers.set(HttpHeaders.USER_AGENT, userAgent)
            }
            .retrieve()
            .body(String::class.java)
            ?: error("Entity service returned an empty OCA bundle")
        require(Json.parseToJsonElement(bundle) is JsonObject) {
            "Entity service returned an invalid OCA bundle"
        }
        return bundle
    }
}
