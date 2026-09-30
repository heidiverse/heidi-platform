// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.heidiverse.heidi.shared.signing.SigningKeyException
import org.heidiverse.heidi.shared.signing.SigningKeyRef
import org.heidiverse.heidi.signing.adapters.RemoteSigningKeyProvider
import org.heidiverse.heidi.signing.adapters.SigningClientKeys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/** Shared provider transport for ordinary signatures and profile operations. */
@Service
class SigningProviderClient(
    builder: RestClient.Builder,
    private val properties: IssuerProperties,
) {
    private val clientBuilder = builder
    private val clientKeys = SigningClientKeys(SIGNING_CLIENT, properties.signingProvider.clientSeed)
    private val providers = ConcurrentHashMap<ProviderCacheKey, RemoteSigningKeyProvider>()
    private val logger = LoggerFactory.getLogger(SigningProviderClient::class.java)

    fun provider(
        configuration: IssuerProperties.IssuerSigning,
        issuerSlug: String,
    ): RemoteSigningKeyProvider {
        val keyUri = configuration.keyUri
            ?: throw SigningKeyException("Issuer '$issuerSlug' has no provider key URI")
        val endpoint = configuration.providerEndpoint
            ?.takeIf { it.isNotBlank() }
            ?: properties.signingProvider.baseUrl
        if (endpoint.isBlank()) {
            throw SigningKeyException(
                "Issuer '$issuerSlug' uses provider-backed signing key '$keyUri', but "
                    + "heidi.issuer.signing-provider.base-url is not configured",
            )
        }
        val authenticationMode = configuration.providerAuthenticationMode
            ?.takeIf { it.isNotBlank() }
            ?: properties.signingProvider.authenticationMode
        val cacheKey = ProviderCacheKey(endpoint, authenticationMode)
        return providers.computeIfAbsent(cacheKey) {
            RemoteSigningKeyProvider(
                clientBuilder,
                URI.create(endpoint),
                if (clientKeys.isConfigured) {
                    clientKeys.authentication()
                } else {
                    RemoteSigningKeyProvider.Authentication.from(authenticationMode, "")
                },
            )
        }
    }

    fun resolve(
        configuration: IssuerProperties.IssuerSigning,
        issuerSlug: String,
        keyUri: String?,
    ): ResolvedKey {
        val resolvedKeyUri = keyUri
            ?: throw SigningKeyException("Issuer '$issuerSlug' has no provider key URI")
        val provider = provider(configuration, issuerSlug)
        val ref = try {
            provider.resolve(resolvedKeyUri)
        } catch (exception: RuntimeException) {
            logger.error(
                "Could not load signing key '{}' for issuer '{}' from provider '{}'",
                resolvedKeyUri,
                issuerSlug,
                endpoint(configuration),
                exception,
            )
            throw SigningKeyException(
                "Could not resolve signing key '$resolvedKeyUri' for issuer '$issuerSlug' "
                    + "from provider '${endpoint(configuration)}'",
                exception,
            )
        }
        return ResolvedKey(provider, ref)
    }

    fun endpoint(configuration: IssuerProperties.IssuerSigning): String =
        configuration.providerEndpoint
            ?.takeIf { it.isNotBlank() }
            ?: properties.signingProvider.baseUrl

    data class ResolvedKey(
        val provider: RemoteSigningKeyProvider,
        val ref: SigningKeyRef,
    )

    private data class ProviderCacheKey(
        val endpoint: String,
        val authenticationMode: String,
    )

    /** The public key an operator adds to a signing service, or the platform hands over. */
    fun clientPublicKey(providerScheme: String): ClientKey? =
        if (clientKeys.isConfigured) {
            ClientKey(clientKeys.client(), clientKeys.publicKey(providerScheme))
        } else {
            null
        }

    data class ClientKey(val client: String, val publicKey: String)

    companion object {
        const val SIGNING_CLIENT = "issuer"
    }
}
