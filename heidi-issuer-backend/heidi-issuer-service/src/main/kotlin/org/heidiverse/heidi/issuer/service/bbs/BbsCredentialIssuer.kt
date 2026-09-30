// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service.bbs

import com.nimbusds.jose.jwk.JWK
import kotlinx.serialization.json.JsonObject
import org.heidiverse.heidi.issuer.model.TrustSystem
import org.heidiverse.heidi.issuer.service.IssuerFlowSnapshot
import org.heidiverse.heidi.issuer.service.CredentialData
import org.heidiverse.heidi.issuer.service.IssuerConfigurationClient
import org.heidiverse.heidi.issuer.service.SigningProviderClient
import org.heidiverse.heidi.shared.signing.SigningOperationRequest
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.util.Base64

/** BBS credential construction for the experimental ZKP_VC profile. */
@Service
class BbsCredentialIssuer(
    private val configurationClient: IssuerConfigurationClient,
    private val providerClient: SigningProviderClient,
) {
    private companion object {
        const val OPERATION = "w3c.bbs-data-integrity-credential-issuance"
        const val ALGORITHM = "BBS"
        const val COMPLETED = "COMPLETED"
        const val DEFAULT_CLAIM_NAMESPACE = "http://schema.org/"
    }

    private val objectMapper = ObjectMapper()

    /** Enables the profile from platform-published configuration and capability state. */
    fun isAvailable(
        issuerSlug: String,
        credentialIdentifier: String,
        credentialVersion: String,
        trustSystem: TrustSystem? = null,
        issuanceProfileId: String,
    ): Boolean = (trustSystem?.let(::listOf) ?: TrustSystem.entries).any { selectedTrustSystem ->
        runCatching {
            val signingConfiguration = configurationClient.resolveOperationSigningConfiguration(
                issuerSlug, selectedTrustSystem, OPERATION, credentialIdentifier, credentialVersion,
                issuanceProfileId,
            )
            val signing = signingConfiguration.signing
            if (signing.algorithm != ALGORITHM
                || !signing.supportedAlgorithms.contains(ALGORITHM)
                || !signing.supportedOperations.contains(OPERATION)) {
                return@runCatching false
            }
            true
        }.getOrDefault(false)
    }

    fun issue(
        data: CredentialData,
        issuerSlug: String,
        credentialType: String,
        holderJwk: String?,
        snapshot: IssuerFlowSnapshot,
        trustSystem: TrustSystem = TrustSystem.Default,
    ): String {
        val signing = requireNotNull(snapshot.operationSigning) { "Offer has no BBS signing configuration" }
        val operationConfiguration = snapshot.operationConfiguration
        val bbs = BbsOperationConfiguration.from(operationConfiguration)
            .resolve(signing, issuerSlug)
        val resolved = providerClient.resolve(signing, issuerSlug, signing.keyUri)
        val provider = resolved.provider
        val ref = resolved.ref
        require(ref.algorithm() == ALGORITHM) {
            "BBS signing key '${ref.uri()}' uses algorithm '${ref.algorithm()}'"
        }
        val claims = linkedMapOf<String, Any?>(
            "@id" to "did:${data.credentialIdentifier}:${data.credentialVersion}",
        )
        data.attributes.forEach { (name, attribute) ->
            val claimName = attribute.nameOverrides[ALGORITHM]
                ?: attribute.nameOverrides["ZKP_VC"]
                ?: "$DEFAULT_CLAIM_NAMESPACE$name"
            claims[claimName] = mapOf("@value" to attribute.value)
        }
        val holder = holderJwk?.let { JWK.parse(it).toECKey() }
        val input = linkedMapOf<String, Any?>(
            "algorithm" to ALGORITHM,
            "claims" to claims,
            "issuerId" to requireNotNull(bbs.issuerId),
            "issuerKeyId" to requireNotNull(bbs.issuerKeyId),
            "credentialType" to credentialType,
            "deviceBinding" to holder?.let {
                mapOf(
                    "x" to Base64.getEncoder().encodeToString(it.x.decode()),
                    "y" to Base64.getEncoder().encodeToString(it.y.decode()),
                )
            },
        )
        val operation = provider.execute(
            ref,
            SigningOperationRequest(
                OPERATION,
                null,
                objectMapper.writeValueAsString(input),
            ),
        )
        require(operation.status == COMPLETED) {
            "BBS credential operation did not complete: ${operation.status}"
        }
        val result = objectMapper.readTree(
            requireNotNull(operation.resultJson) { "BBS credential operation returned no result" },
        )
        return requireNotNull(result.path("credential").asString(null)) {
            "BBS credential operation returned no credential"
        }
    }
}
