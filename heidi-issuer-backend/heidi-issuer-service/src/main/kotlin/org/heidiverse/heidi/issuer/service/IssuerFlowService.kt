// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.issuer.service

import org.heidiverse.heidi.issuer.model.TrustSystem
import org.heidiverse.heidi.issuer.service.SchemaMetadataService.CredentialFormat
import org.heidiverse.heidi.shared.signing.SigningPurpose
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/** Captures the public configuration and retains only the private operations an offer needs. */
@Service
class IssuerFlowService(private val configurations: IssuerConfigurationClient) {
    fun capture(
        issuer: String, trust: TrustSystem, identifier: String, version: String,
        formats: Set<CredentialFormat>, encryption: CredentialEncryptionPolicy,
        issuanceProfileId: String,
    ): IssuerFlowSnapshot {
        val credential = if (formats.any { it != CredentialFormat.ZKP_VC }) {
            configurations.resolve(issuer, trust, identifier, version, issuanceProfileId)
        } else null
        val operation = if (CredentialFormat.ZKP_VC in formats) {
            configurations.resolveOperationSigningConfiguration(
                issuer, trust, IssuerFlowSnapshot.BBS_OPERATION, identifier, version,
                issuanceProfileId,
            )
        } else null
        val parameters = operation?.let {
            configurations.resolveOperationConfigurationOrNull(
                issuer, it.trustSystem, IssuerFlowSnapshot.BBS_OPERATION, issuanceProfileId,
            )
                ?.configuration
        }
        return IssuerFlowSnapshot(
            issuanceProfileId, IssuanceProfilePolicy.version(issuanceProfileId),
            credential?.signing, credential?.issuerClaim, operation?.signing,
            parameters ?: kotlinx.serialization.json.JsonObject(emptyMap()), encryption,
        )
    }

    fun retain(issuer: String, flow: UUID, snapshot: IssuerFlowSnapshot, expiry: Instant) {
        val keys = buildSet {
            snapshot.signing?.let { add(requireNotNull(it.keyUri) to SigningPurpose.SIGNING) }
            snapshot.operationSigning?.let { add(requireNotNull(it.keyUri) to SigningPurpose.OPERATIONS) }
            snapshot.encryption.requestKeys.forEach { add(it.keyUri to SigningPurpose.DECRYPT) }
        }
        keys.forEach { (uri, purpose) -> configurations.retainFlow(issuer, flow, uri, purpose, expiry) }
    }

    fun release(flow: UUID) = configurations.releaseFlow(flow)
}
