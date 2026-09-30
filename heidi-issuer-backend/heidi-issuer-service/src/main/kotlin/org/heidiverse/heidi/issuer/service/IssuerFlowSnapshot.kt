// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.issuer.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Public configuration pinned to an offer, including certificate and operation parameters. */
@Serializable
data class IssuerFlowSnapshot(
    /** Profile selection is pinned with the private material used by this offer. */
    val issuanceProfileId: String? = null,
    val issuanceProfileVersion: String? = null,
    val signing: IssuerProperties.IssuerSigning? = null,
    val issuerClaim: String? = null,
    val operationSigning: IssuerProperties.IssuerSigning? = null,
    val operationConfiguration: JsonObject = JsonObject(emptyMap()),
    val encryption: CredentialEncryptionPolicy = CredentialEncryptionPolicy(),
) {
    fun encode(): String = Json.encodeToString(serializer(), this)

    companion object {
        const val BBS_OPERATION = "w3c.bbs-data-integrity-credential-issuance"
        fun decode(value: String?): IssuerFlowSnapshot =
            Json.decodeFromString(requireNotNull(value) { "Issuance session has no signing snapshot" })
    }
}
