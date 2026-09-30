// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import java.util.UUID
import org.heidiverse.heidi.issuer.model.TrustSystem

data class CredentialData(
    val credentialIdentifier: String,
    val credentialVersion: String,
    val issuerSlug: String?,
    val attributes: Map<String, Attribute>,
    val deferredTransactionId: UUID? = null,
    val encryptedTxCode: String? = null,
    val attributeUrl: String? = null,
    val trustSystem: TrustSystem? = null,
    val issuanceProfileId: String? = null,
) {
    data class Attribute(
        val value: Any?,
        val type: String,
        val array: Boolean,
        val nameOverrides: Map<String, String>,
    )

    data class StatusListReference(val uri: String, val index: Int)

    fun withSchemaMetadata(metadata: SchemaMetadataService.SchemaMetadata): CredentialData = copy(
        attributes = attributes.mapValues { (name, attribute) ->
            metadata.attributeMetadata[name]?.let { schemaAttribute ->
                attribute.copy(
                    type = schemaAttribute.type,
                    array = schemaAttribute.array,
                    nameOverrides = schemaAttribute.nameOverrides,
                )
            } ?: attribute
        },
    )
}
