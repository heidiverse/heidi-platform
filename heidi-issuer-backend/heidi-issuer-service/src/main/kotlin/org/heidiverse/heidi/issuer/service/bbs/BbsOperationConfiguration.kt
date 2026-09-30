// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service.bbs

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.heidiverse.heidi.issuer.service.IssuerProperties

/** BBS profile fields decoded from the generic operation configuration. */
data class BbsOperationConfiguration(
    val issuerId: String?,
    val issuerKeyId: String?,
) {
    fun resolve(
        signing: IssuerProperties.IssuerSigning,
        issuerSlug: String,
    ): BbsOperationConfiguration {
        val resolvedIssuerId = issuerId?.takeIf(String::isNotBlank)
            ?: "did:heidi:$issuerSlug"
        val resolvedKeyId = issuerKeyId?.takeIf(String::isNotBlank)
            ?: "$resolvedIssuerId#${signing.keyId}"
        require(DID.matches(resolvedIssuerId)) {
            "BBS issuer ID must be a DID for issuer '$issuerSlug'"
        }
        require(DID_KEY.matches(resolvedKeyId)) {
            "BBS issuer key ID must be a DID URL with a fragment for issuer '$issuerSlug'"
        }
        return BbsOperationConfiguration(resolvedIssuerId, resolvedKeyId)
    }

    companion object Factory {
        private val DID = Regex("^did:[a-z0-9]+:[^\\s#]+$")
        private val DID_KEY = Regex("^did:[a-z0-9]+:[^\\s#]+#[^\\s#]+$")

        fun from(configuration: JsonObject): BbsOperationConfiguration = BbsOperationConfiguration(
            configuration["issuerId"]?.jsonPrimitive?.contentOrNull,
            configuration["issuerKeyId"]?.jsonPrimitive?.contentOrNull,
        )
    }
}
