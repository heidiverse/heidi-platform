// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("heidi.issuer")
data class IssuerProperties(
    var publicUrl: String = "",
    var apiKey: String = "",
    var platformInternalBaseUrl: String = "",
    var platformPublicBaseUrl: String = "",
    var platformBasicAuth: String = "",
    /** Whether Credential and Deferred Credential Requests must be encrypted. */
    var credentialRequestEncryptionRequired: Boolean = false,
    /** Whether wallets must request encrypted Credential Responses. */
    var credentialResponseEncryptionRequired: Boolean = false,
    var transactionCodeMasterKey: String = "",
    var deferredIntervalSeconds: Long = 5,
    var credentialLifetimeSeconds: Long = 1_209_600,
    var offerLifetimeSeconds: Long = 86_400,
    var accessTokenLifetimeSeconds: Long = 600,
    var refreshTokenLifetimeSeconds: Long = 2_592_000,
    var dpopProofMaxAgeSeconds: Long = 300,
    var dpopNonceLifetimeSeconds: Long = 300,
    /** Base64-encoded HMAC key (>= 32 bytes) authenticating DPoP nonces. Required for multi-instance deployments. */
    var dpopNonceKey: String = "",
    var signingProvider: SigningProvider = SigningProvider(),
) {
    data class SigningProvider(
        /** Heidi Signing Protocol endpoint used when a configuration carries keyUri. */
        var baseUrl: String = "",
        var authenticationMode: String = "",
        /** Base64-encoded 32-byte seed this issuer authenticates with, at every signing service. */
        var clientSeed: String = "",
    )

    @kotlinx.serialization.Serializable
    data class IssuerSigning(
        var keyId: String = "",
        var keyUri: String? = null,
        var algorithm: String = "ES256",
        var providerEndpoint: String? = null,
        var providerAuthenticationMode: String? = null,
        var issuerJwk: String = "{}",
        var certificateChain: List<String> = emptyList(),
        var previousIssuerJwks: List<String> = emptyList(),
        var supportedAlgorithms: List<String> = emptyList(),
        var supportedOperations: List<String> = emptyList(),
    ) {
        fun validate(issuerSlug: String): IssuerSigning {
            require(keyId.isNotBlank()) { "Signature key-id is required for issuer '$issuerSlug'" }
            return this
        }
    }
}
