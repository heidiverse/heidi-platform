// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.crypto.impl.AAD
import com.nimbusds.jose.crypto.impl.ContentCryptoProvider
import com.nimbusds.jose.jca.JWEJCAContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.heidiverse.heidi.issuer.model.api.CredentialResponse
import org.heidiverse.heidi.issuer.model.api.IssuerMetadata
import org.kapunsdk.crypto.jwe.JweCompression
import org.kapunsdk.crypto.jwe.JweContentEncryptionAlgorithm
import org.kapunsdk.crypto.jwe.JweEncryptionOptions
import org.kapunsdk.crypto.jwe.JweKeyManagementAlgorithm
import org.kapunsdk.crypto.jwe.encryptJwe
import org.kapunsdk.crypto.jwe.parseJweHeader
import org.kapunsdk.crypto.jwe.publicJweJwk
import org.heidiverse.heidi.shared.signing.SigningContentKeyRequest
import org.heidiverse.heidi.shared.signing.SigningKeyRef
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

/**
 * Application-layer encryption for OID4VCI credential and deferred credential messages.
 *
 * <p>What an issuer advertises and what it accepts come from the same
 * [CredentialEncryptionPolicy], resolved per issuer from the platform, so the two can never drift.
 * An empty list in the policy means the issuer does not restrict that parameter and everything
 * this service supports is offered.
 */
@Service
class CredentialEncryptionService @Autowired constructor(
    properties: IssuerProperties,
    private val configurationClient: IssuerConfigurationClient,
    private val signingProviderClient: SigningProviderClient? = null,
) {
    private val objectMapper = ObjectMapper()
    private val json = Json
    private val requestEncryptionRequired = properties.credentialRequestEncryptionRequired
    private val responseEncryptionRequired = properties.credentialResponseEncryptionRequired

    /** Resolve once per request and pass it down; each call is a round trip to the platform. */
    fun policyFor(
        issuerSlug: String,
        issuanceProfileId: String,
    ): CredentialEncryptionPolicy =
        configurationClient.resolveCredentialEncryption(issuerSlug, issuanceProfileId)
            ?: error("Credential encryption policy is missing for issuance profile '$issuanceProfileId'")

    fun requestEncryptionMetadata(policy: CredentialEncryptionPolicy): IssuerMetadata.CredentialRequestEncryption? {
        val keys = requestKeys(policy)
        if (keys.isEmpty()) return null
        return IssuerMetadata.CredentialRequestEncryption(
            IssuerMetadata.JwkSet(keys.map { it.publicJwk.asMetadataJwk() }),
            requestEnc(policy).map { it.identifier },
            requestZip(policy).map { it.identifier },
            policy.requestEncryptionRequired ?: requestEncryptionRequired,
        )
    }

    fun responseEncryptionMetadata(policy: CredentialEncryptionPolicy) = IssuerMetadata.CredentialResponseEncryption(
        responseAlg(policy).map { it.identifier },
        responseEnc(policy).map { it.identifier },
        responseZip(policy).map { it.identifier },
        policy.responseEncryptionRequired ?: responseEncryptionRequired,
    )

    fun validateRequirements(
        policy: CredentialEncryptionPolicy,
        encryptedRequest: Boolean,
        responseEncryptionRequested: Boolean,
    ) {
        validateRequestEncryption(policy, encryptedRequest)
        validateResponseEncryption(policy, encryptedRequest, responseEncryptionRequested)
    }

    fun validateRequestEncryption(policy: CredentialEncryptionPolicy, encryptedRequest: Boolean) {
        if ((policy.requestEncryptionRequired ?: requestEncryptionRequired) && !encryptedRequest) {
            invalidEncryption("Credential Request encryption is required by the Credential Issuer")
        }
    }

    fun validateResponseEncryption(
        policy: CredentialEncryptionPolicy,
        encryptedRequest: Boolean,
        responseEncryptionRequested: Boolean,
    ) {
        if (!encryptedRequest && responseEncryptionRequested) {
            invalidEncryption("credential_response_encryption is only accepted in an encrypted Credential Request")
        }
        if ((policy.responseEncryptionRequired ?: responseEncryptionRequired) && !responseEncryptionRequested) {
            invalidEncryption("Credential Response encryption is required by the Credential Issuer")
        }
    }

    fun decryptRequest(
        policy: CredentialEncryptionPolicy,
        compactJwe: String,
        issuerSlug: String? = null,
    ): String {
        val header = runCatching { parseJweHeader(compactJwe) }
            .getOrElse { invalidEncryption("Credential Request is not a valid compact JWE", it) }
        // Resolve the exact key version advertised in the issuer metadata.
        val key = decryptionKeys(policy).singleOrNull { it.keyId == header.keyId }
            ?: invalidEncryption("Credential Request uses an unknown kid")
        val compression = header.compression
        if (header.algorithm != key.algorithm.identifier ||
            header.contentEncryption.toContentEncryptionAlgorithm() !in requestEnc(policy) ||
            (compression != null && compression.toCompression() !in requestZip(policy))
        ) {
            invalidEncryption("Credential Request uses unsupported encryption parameters")
        }

        val providerClient = signingProviderClient
            ?: invalidEncryption("Credential Request decryption provider is not configured")
        val slug = issuerSlug
            ?: invalidEncryption("Credential Request issuer is not known")
        return decryptWithProvider(providerClient, key, compactJwe, slug)
    }

    private fun decryptWithProvider(
        providerClient: SigningProviderClient,
        key: RequestDecryptionKey,
        compactJwe: String,
        issuerSlug: String,
    ): String {
        val jwe = runCatching { JWEObject.parse(compactJwe) }
            .getOrElse { invalidEncryption("Credential Request is not a valid compact JWE", it) }
        val configuration = IssuerProperties.IssuerSigning(
            keyId = key.keyId,
            keyUri = key.keyUri,
            algorithm = key.keyAlgorithm,
            providerEndpoint = key.providerEndpoint,
            providerAuthenticationMode = key.providerAuthenticationMode,
            issuerJwk = key.publicJwk.toString(),
        )
        val keyUri = key.keyUri
            ?: invalidEncryption("Credential Request decryption key has no provider URI")
        val ref = SigningKeyRef(keyUri, key.publicJwk.toString(), key.keyAlgorithm)
        val contentKey = providerClient.provider(configuration, issuerSlug).contentKey(
            ref,
            SigningContentKeyRequest(
                jwe.header.algorithm.name,
                jwe.header.encryptionMethod.name,
                jwe.header.ephemeralPublicKey?.toJSONString(),
                jwe.header.agreementPartyUInfo?.toString(),
                jwe.header.agreementPartyVInfo?.toString(),
                jwe.encryptedKey?.decode(),
            ),
        )
        val plain = runCatching {
            ContentCryptoProvider.decrypt(
                jwe.header,
                AAD.compute(jwe.header),
                jwe.encryptedKey,
                jwe.iv,
                jwe.cipherText,
                jwe.authTag,
                SecretKeySpec(contentKey, "AES"),
                JWEJCAContext(),
            )
        }.getOrElse { invalidEncryption("Credential Request could not be decrypted", it) }
        return plain.toString(Charsets.UTF_8)
    }

    fun encryptResponse(
        policy: CredentialEncryptionPolicy,
        response: CredentialResponse,
        parameters: CredentialResponseEncryptionParameters,
    ): String {
        val recipient = parseResponseKey(policy, parameters)
        val contentEncryption = parameters.enc.toContentEncryptionAlgorithm()
            ?: invalidEncryption("Credential Response uses an unsupported enc parameter")
        val compression = parameters.zip?.toCompression()
        if (parameters.zip != null && compression == null) {
            invalidEncryption("Credential Response uses an unsupported zip parameter")
        }
        val options = JweEncryptionOptions(
            contentEncryption = contentEncryption,
            compression = compression,
            tokenType = "JWT",
        )
        return runCatching {
            encryptJwe(
                recipient,
                objectMapper.writeValueAsString(response).asJsonObject(),
                options,
            )
        }.getOrElse { invalidEncryption("Credential Response could not be encrypted", it) }
    }

    // An empty selection means the configured profile does not restrict that parameter.
    private fun requestKeys(policy: CredentialEncryptionPolicy): List<RequestDecryptionKey> {
        val configured = if (policy.requestKeys.isNotEmpty()) {
            policy.requestKeys.map { key ->
                val algorithm = key.algorithm.toKeyManagementAlgorithm()
                    ?: invalidEncryption("Issuer metadata contains an unsupported request key alg")
                RequestDecryptionKey(
                    publicJwk = key.publicJwk.asJsonObject(),
                    algorithm = algorithm,
                    keyId = key.keyId,
                    keyUri = key.keyUri,
                    providerEndpoint = key.providerEndpoint,
                    providerAuthenticationMode = key.providerAuthenticationMode,
                    keyAlgorithm = key.keyAlgorithm,
                )
            }
        } else emptyList()
        if (policy.requestAlgValues.isEmpty()) return configured
        return policy.requestAlgValues.flatMap { alg ->
            configured.filter { it.algorithm.identifier == alg }
        }
    }

    private fun decryptionKeys(policy: CredentialEncryptionPolicy): List<RequestDecryptionKey> {
        return requestKeys(policy)
    }

    private fun requestEnc(policy: CredentialEncryptionPolicy) =
        CONTENT_ENCRYPTION_METHODS.narrowedTo(policy.requestEncValues)

    private fun requestZip(policy: CredentialEncryptionPolicy) =
        COMPRESSION_METHODS.narrowedTo(policy.requestZipValues)

    private fun responseAlg(policy: CredentialEncryptionPolicy) =
        RESPONSE_ALGORITHMS.narrowedTo(policy.responseAlgValues)

    private fun responseEnc(policy: CredentialEncryptionPolicy) =
        CONTENT_ENCRYPTION_METHODS.narrowedTo(policy.responseEncValues)

    private fun responseZip(policy: CredentialEncryptionPolicy) =
        COMPRESSION_METHODS.narrowedTo(policy.responseZipValues)

    // The issuer's order is kept: metadata lists it as the issuer's order of preference, and an
    // unknown value simply drops out rather than failing the whole metadata document.
    private fun <T> List<T>.narrowedTo(selected: List<String>): List<T> where T : Enum<T> =
        if (selected.isEmpty()) this else selected.mapNotNull { value -> firstOrNull { identifierOf(it) == value } }

    private fun identifierOf(value: Enum<*>): String = when (value) {
        is JweContentEncryptionAlgorithm -> value.identifier
        is JweKeyManagementAlgorithm -> value.identifier
        is JweCompression -> value.identifier
        else -> value.name
    }

    private fun parseResponseKey(
        policy: CredentialEncryptionPolicy,
        parameters: CredentialResponseEncryptionParameters,
    ): JsonObject {
        val compression = parameters.zip
        if (parameters.enc.toContentEncryptionAlgorithm() !in responseEnc(policy) ||
            (compression != null && compression.toCompression() !in responseZip(policy))
        ) {
            invalidEncryption("Credential Response uses unsupported enc or zip parameters")
        }
        val algorithm = parameters.jwk.string("alg")?.toKeyManagementAlgorithm()
        if (algorithm !in responseAlg(policy) ||
            parameters.jwk.string("use") !in setOf(null, "enc")
        ) {
            invalidEncryption("Credential Response encryption jwk uses unsupported parameters")
        }
        runCatching { validateKeyType(parameters.jwk, algorithm!!.identifier) }
            .getOrElse { invalidEncryption("Credential Response encryption jwk uses an incompatible key type", it) }
        return runCatching { publicJweJwk(parameters.jwk) }
            .getOrElse { invalidEncryption("Credential Response encryption jwk is invalid", it) }
    }

    private fun validateKeyType(key: JsonObject, algorithm: String) {
        when {
            algorithm.startsWith("ECDH-ES") -> require(
                key.string("kty") == "EC" && key.string("crv") == "P-256",
            ) { "$algorithm requires an EC P-256 JWK" }
            algorithm == "RSA-OAEP-256" -> {
                require(key.string("kty") == "RSA") { "$algorithm requires an RSA JWK" }
                val modulus = key.string("n")?.let(Base64.getUrlDecoder()::decode)
                    ?: error("$algorithm requires an RSA modulus")
                require(modulus.size >= 256) { "$algorithm requires an RSA JWK of at least 2048 bits" }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun JsonObject.asMetadataJwk(): Map<String, Any> =
        objectMapper.readValue(toString(), Map::class.java) as Map<String, Any>

    private fun String.asJsonObject(): JsonObject = json.parseToJsonElement(this).jsonObject

    private fun String.toKeyManagementAlgorithm(): JweKeyManagementAlgorithm? =
        JweKeyManagementAlgorithm.entries.singleOrNull { it.identifier == this }

    private fun String.toContentEncryptionAlgorithm(): JweContentEncryptionAlgorithm? =
        JweContentEncryptionAlgorithm.entries.singleOrNull { it.identifier == this }

    private fun String.toCompression(): JweCompression? =
        JweCompression.entries.singleOrNull { it.identifier == this }

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun invalidEncryption(message: String, cause: Throwable? = null): Nothing =
        throw Oid4vciProtocolException("invalid_encryption_parameters", message, cause)

    private data class RequestDecryptionKey(
        val publicJwk: JsonObject,
        val algorithm: JweKeyManagementAlgorithm,
        val keyId: String,
        val keyUri: String? = null,
        val providerEndpoint: String? = null,
        val providerAuthenticationMode: String? = null,
        val keyAlgorithm: String = algorithm.identifier,
    )

    companion object {
        private val REQUEST_ALGORITHMS = listOf(
            JweKeyManagementAlgorithm.ECDH_ES,
            JweKeyManagementAlgorithm.ECDH_ES_A128KW,
            JweKeyManagementAlgorithm.ECDH_ES_A192KW,
            JweKeyManagementAlgorithm.ECDH_ES_A256KW,
            JweKeyManagementAlgorithm.RSA_OAEP_256
        )
        private val RESPONSE_ALGORITHMS = REQUEST_ALGORITHMS
        private val CONTENT_ENCRYPTION_METHODS = listOf(
            JweContentEncryptionAlgorithm.A128GCM,
            JweContentEncryptionAlgorithm.A192GCM,
            JweContentEncryptionAlgorithm.A256GCM,
            JweContentEncryptionAlgorithm.A128CBC_HS256,
            JweContentEncryptionAlgorithm.A192CBC_HS384,
            JweContentEncryptionAlgorithm.A256CBC_HS512
        )
        private val COMPRESSION_METHODS = listOf(JweCompression.DEF)
    }
}

/**
 * One issuer's selection of JWE parameters, as stored on the platform.
 *
 * An empty list restricts nothing; a null required flag leaves the issuer backend's own
 * `heidi.issuer.credential-*-encryption-required` default in place.
 */
@kotlinx.serialization.Serializable
data class CredentialEncryptionPolicy(
    val requestAlgValues: List<String> = emptyList(),
    val requestEncValues: List<String> = emptyList(),
    val requestZipValues: List<String> = emptyList(),
    val responseAlgValues: List<String> = emptyList(),
    val responseEncValues: List<String> = emptyList(),
    val responseZipValues: List<String> = emptyList(),
    val requestEncryptionRequired: Boolean? = null,
    val responseEncryptionRequired: Boolean? = null,
    val requestKeys: List<CredentialEncryptionKey> = emptyList(),
)

@kotlinx.serialization.Serializable
data class CredentialEncryptionKey(
    val keyId: String,
    val keyUri: String,
    val algorithm: String,
    val publicJwk: String,
    val providerEndpoint: String? = null,
    val providerAuthenticationMode: String? = null,
    val keyAlgorithm: String = algorithm,
)

data class CredentialResponseEncryptionParameters(
    val jwk: JsonObject,
    val enc: String,
    val zip: String?,
)
