// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.springframework.stereotype.Service
import org.heidiverse.heidi.issuer.model.TrustSystem
import org.slf4j.LoggerFactory
import uniffi.heidi_signing.SigningException
import uniffi.kapun_credential_core_rust.SignatureCreator
import java.util.concurrent.ConcurrentHashMap

/** Remote signing transport, independent of the signing backend implementation. */
@Service
class SignatureClient(
    private val providerClient: SigningProviderClient,
    private val configurationClient: IssuerConfigurationClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val signers = ConcurrentHashMap<SignerCacheKey, KapunsSignaturCreator>()
    private val logger = LoggerFactory.getLogger(SignatureClient::class.java)

    fun forIssuer(
        issuerSlug: String,
        trustSystem: TrustSystem = TrustSystem.Default,
        credentialIdentifier: String? = null,
        credentialVersion: String? = null,
        issuanceProfileId: String? = null,
    ): KapunsSignaturCreator {
        val resolved = configurationClient.resolve(
            issuerSlug, trustSystem, credentialIdentifier, credentialVersion, issuanceProfileId,
        )
        return signer(issuerSlug, trustSystem, resolved, SignerUse.CREDENTIAL)
    }

    fun fromSnapshot(issuer: String, snapshot: IssuerFlowSnapshot): KapunsSignaturCreator =
        ProviderSignatureCreator(issuer, requireNotNull(snapshot.signing) {
            "Offer has no credential signing configuration"
        }.validate(issuer))

    fun forTrust(
        issuerSlug: String,
        trustSystem: TrustSystem,
        credentialIdentifier: String,
        credentialVersion: String,
        issuanceProfileId: String? = null,
    ): KapunsSignaturCreator {
        val resolved = configurationClient.resolveTrustSigningConfiguration(
            issuerSlug, trustSystem, credentialIdentifier, credentialVersion, issuanceProfileId,
        )
        return signer(issuerSlug, trustSystem, resolved, SignerUse.TRUST)
    }

    private fun signer(
        issuerSlug: String,
        trustSystem: TrustSystem,
        resolved: IssuerConfigurationClient.RuntimeSigningConfiguration,
        use: SignerUse,
    ): KapunsSignaturCreator {
        val cacheKey = SignerCacheKey(
            issuerSlug, trustSystem, resolved.signing.keyId, resolved.signing.algorithm, use,
        )
        return requireNotNull(signers.compute(cacheKey) { _, existing ->
            if (existing is ProviderSignatureCreator && existing.configuration == resolved.signing) {
                existing
            } else {
                ProviderSignatureCreator(issuerSlug, resolved.signing.validate(issuerSlug))
            }
        })
    }

    fun configurationFor(
        issuerSlug: String,
        trustSystem: TrustSystem = TrustSystem.Default,
    ): IssuerProperties.IssuerSigning = configurationClient.resolve(issuerSlug, trustSystem).signing

    fun profileFor(
        issuerSlug: String,
        credentialIdentifier: String,
        credentialVersion: String,
    ): String = configurationClient.profileFor(issuerSlug, credentialIdentifier, credentialVersion)

    /** Resolve the configured key so a credential offer can fail before wallet use. */
    fun validateKeyAccess(
        issuerSlug: String,
        trustSystem: TrustSystem = TrustSystem.Default,
        credentialIdentifier: String? = null,
        credentialVersion: String? = null,
        issuanceProfileId: String? = null,
    ): IssuerConfigurationClient.RuntimeSigningConfiguration {
        val resolved = configurationClient.resolve(
            issuerSlug, trustSystem, credentialIdentifier, credentialVersion, issuanceProfileId,
        )
        signer(issuerSlug, trustSystem, resolved, SignerUse.CREDENTIAL)
        return resolved
    }

    fun configurationsFor(
        issuerSlug: String,
        credentialIdentifier: String? = null,
        credentialVersion: String? = null,
        issuanceProfileId: String? = null,
    ): List<IssuerProperties.IssuerSigning> {
        if (issuanceProfileId != null) {
            // The profile route selects its one compatible trust system.
            return runCatching {
                configurationClient.resolve(
                    issuerSlug, TrustSystem.Default, credentialIdentifier, credentialVersion,
                    issuanceProfileId,
                ).signing
            }.getOrNull()?.let(::listOf).orEmpty()
        }

        return TrustSystem.entries.mapNotNull { trustSystem ->
            runCatching {
                configurationClient.resolve(
                    issuerSlug, trustSystem, credentialIdentifier, credentialVersion,
                    issuanceProfileId,
                ).signing
            }.getOrNull()
        }.distinctBy { configuration ->
            listOf(configuration.keyId, configuration.algorithm, configuration.issuerJwk)
        }
    }

    fun publicJwksFor(issuerSlug: String): List<String> = configurationClient.publicKeys(issuerSlug)

    fun swissTrustConfiguration(issuerSlug: String): IssuerConfigurationClient.SwissTrustConfiguration? =
        configurationClient.resolveSwissTrustConfiguration(issuerSlug)

    fun effectiveTrustSystem(
        issuerSlug: String,
        requested: TrustSystem,
        credentialIdentifier: String,
        credentialVersion: String,
        issuanceProfileId: String? = null,
    ): TrustSystem = configurationClient.resolve(
        issuerSlug, requested, credentialIdentifier, credentialVersion, issuanceProfileId,
    ).trustSystem

    fun issuerClaim(
        issuerSlug: String,
        trustSystem: TrustSystem,
        credentialIdentifier: String,
        credentialVersion: String,
    ): String? = configurationClient.resolve(
        issuerSlug, trustSystem, credentialIdentifier, credentialVersion,
    ).issuerClaim

    fun allocateStatusList(
        issuerSlug: String,
        credentialIdentifier: String,
        credentialVersion: String,
        allocationId: String,
        count: Int,
    ) = configurationClient.allocateStatusList(
        issuerSlug,
        credentialIdentifier,
        credentialVersion,
        allocationId,
        count,
    )

    abstract class KapunsSignaturCreator(protected val issuerSlug: String, val configuration: IssuerProperties.IssuerSigning) : SignatureCreator {
        open fun publicJwk(): String = configuration.issuerJwk
    }

    /** Signs through the protocol client; no private key is present in the issuer configuration. */
    inner class ProviderSignatureCreator internal constructor(
        issuerSlug: String,
        configuration: IssuerProperties.IssuerSigning,
    ) : KapunsSignaturCreator(issuerSlug, configuration) {
        private val providerEndpoint = providerClient.endpoint(configuration)
        private val resolved = providerClient.resolve(
            configuration,
            issuerSlug,
            configuration.keyUri,
        )
        private val provider = resolved.provider
        private val ref = resolved.ref

        init {
            require(ref.algorithm() == configuration.algorithm) {
                "Signing provider key '${ref.uri()}' uses algorithm '${ref.algorithm()}', " +
                    "but issuer configuration requires '${configuration.algorithm}'"
            }
        }

        override fun alg(): String = configuration.algorithm

        override fun publicJwk(): String {
            // The provider is the source of truth for the key material. Keep only publication
            // metadata from the platform configuration (not stale x/y or n/e values).
            val actual = json.parseToJsonElement(ref.publicJwk()).jsonObject.toMutableMap()
            val configured = json.parseToJsonElement(configuration.issuerJwk).jsonObject
            listOf("kid", "alg", "use", "key_ops", "x5u", "x5c", "x5t", "x5t#S256").forEach { name ->
                configured[name]?.let { actual[name] = it }
            }
            return JsonObject(actual).toString()
        }

        @Throws(SigningException::class)
        override fun sign(bytes: ByteArray): ByteArray = try {
            provider.sign(ref, bytes)
        } catch (exception: Exception) {
            logger.error(
                "Could not sign with signing key '{}' for issuer '{}' using provider '{}'",
                ref.uri(),
                issuerSlug,
                providerEndpoint,
                exception,
            )
            throw exception
        }
    }

    private data class SignerCacheKey(
        val issuerSlug: String,
        val trustSystem: TrustSystem,
        val keyId: String,
        val algorithm: String,
        val use: SignerUse,
    )

    private enum class SignerUse { CREDENTIAL, TRUST }

}
