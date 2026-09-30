// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.heidiverse.heidi.issuer.model.TrustSystem
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

/** Loads issuer identity signing material from heidi-platform-api-entity. */
@Service
class IssuerConfigurationClient(builder: RestClient.Builder, private val properties: IssuerProperties) {
    private val client = builder.baseUrl(properties.platformInternalBaseUrl).let { clientBuilder ->
        if (properties.platformBasicAuth.isNotBlank()) {
            clientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic ${properties.platformBasicAuth}")
        }
        clientBuilder.build()
    }
    private val json = Json { ignoreUnknownKeys = true }

    fun publicKeys(issuerSlug: String): List<String> {
        val body = client.get().uri("/internal/platform/v1/identities/{issuer}/public-keys", issuerSlug)
            .retrieve().body(String::class.java) ?: error("Public key response is empty")
        return json.parseToJsonElement(body).jsonArray.map { it.jsonPrimitive.content }
    }

    fun profileFor(
        issuerSlug: String,
        credentialIdentifier: String,
        credentialVersion: String,
    ): String {
        val body = client.get()
            .uri("/public/v2/schema/{identifier}/{version}/issuer", credentialIdentifier, credentialVersion)
            .retrieve().body(String::class.java)
            ?: error("Entity service returned an empty issuer schema response")
        val root = json.parseToJsonElement(body).jsonObject
        require(root.string("issuerSlug") == issuerSlug) {
            "Credential schema issuer does not match '$issuerSlug'"
        }
        return root.string("issuanceProfileId")
            ?: error("Credential schema issuer is missing 'issuanceProfileId'")
    }

    fun retainFlow(
        issuer: String, flow: java.util.UUID, keyUri: String,
        purpose: org.heidiverse.heidi.shared.signing.SigningPurpose, expiry: java.time.Instant,
    ) {
        client.post().uri("/internal/platform/v1/identities/{issuer}/signing-flows", issuer)
            .body(mapOf("flowId" to flow, "client" to "ISSUER", "keyUri" to keyUri,
                "purpose" to purpose.name, "expiresAt" to expiry.toString()))
            .retrieve().toBodilessEntity()
    }

    fun releaseFlow(flow: java.util.UUID) {
        client.delete().uri("/internal/platform/v1/identities/signing-flows/ISSUER/{flow}", flow)
            .retrieve().toBodilessEntity()
    }

    fun resolve(
        issuerSlug: String,
        trustSystem: TrustSystem,
        credentialIdentifier: String? = null,
        credentialVersion: String? = null,
        issuanceProfileId: String? = null,
    ): RuntimeSigningConfiguration {
        if (properties.platformInternalBaseUrl.isBlank()) {
            error("heidi.issuer.platform-internal-base-url is required for provider-backed signing")
        }
        val body = try {
            client.get().uri { uri ->
                uri.path("/internal/platform/v1/identities/{issuerSlug}/signing-configuration")
                    .queryParam("trustSystem", trustSystem.name)
                    .apply {
                        credentialIdentifier?.let { queryParam("credentialIdentifier", it) }
                        credentialVersion?.let { queryParam("credentialVersion", it) }
                        issuanceProfileId?.let { queryParam("issuanceProfileId", it) }
                    }
                    .build(issuerSlug)
            }
                .retrieve()
                .body(String::class.java)
                ?: error("Entity service returned an empty signing configuration for '$issuerSlug'")
        } catch (exception: RestClientResponseException) {
            throw exception
        }
        return decodeSigningConfiguration(body, trustSystem)
    }

    fun resolveOperationSigningConfiguration(
        issuerSlug: String,
        trustSystem: TrustSystem,
        operation: String,
        credentialIdentifier: String? = null,
        credentialVersion: String? = null,
        issuanceProfileId: String? = null,
    ): RuntimeSigningConfiguration {
        if (properties.platformInternalBaseUrl.isBlank()) {
            error("heidi.issuer.platform-internal-base-url is required for signing operations")
        }
        val body = client.get().uri { uri ->
            uri.path("/internal/platform/v1/identities/{issuerSlug}/operations/{operation}/signing-configuration")
                .queryParam("trustSystem", trustSystem.name)
                .apply {
                        credentialIdentifier?.let { queryParam("credentialIdentifier", it) }
                        credentialVersion?.let { queryParam("credentialVersion", it) }
                        issuanceProfileId?.let { queryParam("issuanceProfileId", it) }
                }
                .build(issuerSlug, operation)
        }.retrieve().body(String::class.java)
            ?: error("Entity service returned an empty operation signing configuration for '$issuerSlug'")
        return decodeSigningConfiguration(body, trustSystem)
    }

    fun resolveTrustSigningConfiguration(
        issuerSlug: String,
        trustSystem: TrustSystem,
        credentialIdentifier: String? = null,
        credentialVersion: String? = null,
        issuanceProfileId: String? = null,
    ): RuntimeSigningConfiguration {
        if (properties.platformInternalBaseUrl.isBlank()) {
            error("heidi.issuer.platform-internal-base-url is required for trust signing")
        }
        val body = client.get().uri { uri ->
            uri.path("/internal/platform/v1/identities/{issuerSlug}/trust-signing-configuration")
                .queryParam("trustSystem", trustSystem.name)
                .apply {
                        credentialIdentifier?.let { queryParam("credentialIdentifier", it) }
                        credentialVersion?.let { queryParam("credentialVersion", it) }
                        issuanceProfileId?.let { queryParam("issuanceProfileId", it) }
                }
                .build(issuerSlug)
        }.retrieve().body(String::class.java)
            ?: error("Entity service returned an empty trust signing configuration for '$issuerSlug'")
        return decodeSigningConfiguration(body, trustSystem)
    }

    private fun decodeSigningConfiguration(
        body: String,
        trustSystem: TrustSystem,
    ): RuntimeSigningConfiguration {
        val root = json.parseToJsonElement(body).jsonObject
        val configuration = IssuerProperties.IssuerSigning(
            keyId = root.requiredString("keyId"),
            keyUri = root.string("keyUri"),
            algorithm = root.string("algorithm") ?: "ES256",
            providerEndpoint = root.string("providerEndpoint"),
            providerAuthenticationMode = root.string("providerAuthenticationMode"),
            issuerJwk = root.string("issuerJwk") ?: "{}",
            certificateChain = root["certificateChain"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            previousIssuerJwks = root["previousIssuerJwks"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            supportedAlgorithms = root["supportedAlgorithms"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            supportedOperations = root["supportedOperations"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
        )
        return RuntimeSigningConfiguration(
            TrustSystem.valueOf(root.string("trustSystem") ?: trustSystem.name),
            root.string("issuerClaim"),
            configuration,
            root.string("issuanceProfileId") ?: root.string("profileId"),
        )
    }

    fun resolveOperationConfiguration(
        issuerSlug: String,
        trustSystem: TrustSystem,
        operation: String,
        issuanceProfileId: String? = null,
    ): RuntimeOperationConfiguration {
        if (properties.platformInternalBaseUrl.isBlank()) {
            error("heidi.issuer.platform-internal-base-url is required for signing operations")
        }
        val body = client.get().uri { uri ->
            uri.path("/internal/platform/v1/identities/{issuerSlug}/operations/{operation}/config")
                .queryParam("trustSystem", trustSystem.name)
                .apply { issuanceProfileId?.let { queryParam("issuanceProfileId", it) } }
                .build(issuerSlug, operation)
        }.retrieve().body(String::class.java)
            ?: error("Entity service returned an empty operation configuration for '$issuerSlug'")
        val root = json.parseToJsonElement(body).jsonObject
        val resolvedOperation = root.string("operation")
            ?: error("Issuer operation configuration is missing 'operation'")
        require(resolvedOperation == operation) {
            "Issuer operation configuration returned '$resolvedOperation', expected '$operation'"
        }
        val schemaVersion = root.string("schemaVersion")?.toIntOrNull()
            ?: error("Issuer operation configuration is missing 'schemaVersion'")
        val configuration = root["configuration"]?.jsonObject
            ?: error("Issuer operation configuration is missing 'configuration'")
        return RuntimeOperationConfiguration(resolvedOperation, schemaVersion, configuration)
    }

    fun resolveOperationConfigurationOrNull(
        issuerSlug: String,
        trustSystem: TrustSystem,
        operation: String,
        issuanceProfileId: String? = null,
    ): RuntimeOperationConfiguration? = try {
        resolveOperationConfiguration(issuerSlug, trustSystem, operation, issuanceProfileId)
    } catch (exception: RestClientResponseException) {
        if (exception.statusCode.value() == 404) null else throw exception
    }

    fun resolveSwissTrustConfiguration(issuerSlug: String): SwissTrustConfiguration? {
        if (properties.platformInternalBaseUrl.isBlank()) return null
        val body = try {
            client.get()
                .uri(
                    "/internal/platform/v1/identities/{issuerSlug}/trust-configuration?trustSystem=Switzerland",
                    issuerSlug,
                )
                .retrieve().body(String::class.java)
        } catch (_: RestClientResponseException) {
            return null
        } ?: return null
        val root = json.parseToJsonElement(body).jsonObject
        return SwissTrustConfiguration(
            did = root.string("swissDid"),
            identityStatement = root.string("swissIdentityStatement"),
            issuanceStatements = root["swissIssuanceStatements"]?.jsonObject
                ?.mapValues { it.value.jsonPrimitive.content }.orEmpty(),
        )
    }

    /** Loads the issuer's encryption policy for its immutable issuance profile. */
    fun resolveCredentialEncryption(
        issuerSlug: String,
        issuanceProfileId: String? = null,
    ): CredentialEncryptionPolicy? {
        if (properties.platformInternalBaseUrl.isBlank()) return null
        val body = try {
            client.get().uri { uri ->
                uri.path("/internal/platform/v1/identities/{issuerSlug}/credential-encryption")
                    .apply { issuanceProfileId?.let { queryParam("issuanceProfileId", it) } }
                    .build(issuerSlug)
            }
                .retrieve().body(String::class.java)
        } catch (exception: RestClientResponseException) {
            if (exception.statusCode.value() == HTTP_NOT_FOUND) return null
            throw exception
        } ?: return null
        val root = json.parseToJsonElement(body).jsonObject
        return CredentialEncryptionPolicy(
            requestAlgValues = root.strings("requestAlgValues"),
            requestEncValues = root.strings("requestEncValues"),
            requestZipValues = root.strings("requestZipValues"),
            responseAlgValues = root.strings("responseAlgValues"),
            responseEncValues = root.strings("responseEncValues"),
            responseZipValues = root.strings("responseZipValues"),
            requestEncryptionRequired = root.boolean("requestEncryptionRequired"),
            responseEncryptionRequired = root.boolean("responseEncryptionRequired"),
            requestKeys = root["requestKeys"]?.jsonArray?.map { key ->
                val objectKey = key.jsonObject
                CredentialEncryptionKey(
                    keyId = objectKey.requiredString("keyId"),
                    keyUri = objectKey.requiredString("keyUri"),
                    algorithm = objectKey.requiredString("algorithm"),
                    publicJwk = objectKey.requiredString("publicJwk"),
                    providerEndpoint = objectKey.string("providerEndpoint"),
                    providerAuthenticationMode = objectKey.string("providerAuthenticationMode"),
                    keyAlgorithm = objectKey.string("keyAlgorithm") ?: objectKey.requiredString("algorithm"),
                )
            }.orEmpty(),
        )
    }

    fun allocateStatusList(
        issuerSlug: String,
        credentialIdentifier: String,
        credentialVersion: String,
        allocationId: String,
        count: Int,
    ): StatusListReference? {
        if (properties.platformInternalBaseUrl.isBlank()) return null
        val body = client.post().uri { uri ->
            uri.path("/internal/platform/v1/identities/{issuerSlug}/status-list-allocation")
                .queryParam("credentialIdentifier", credentialIdentifier)
                .queryParam("credentialVersion", credentialVersion)
                .queryParam("allocationId", allocationId)
                .queryParam("count", count)
                .build(issuerSlug)
        }.retrieve().body(String::class.java) ?: return null
        val root = json.parseToJsonElement(body).jsonObject
        return StatusListReference(
            uri = root.requiredString("uri"),
            indices = root["indices"]?.jsonArray
                ?.map { it.jsonPrimitive.content.toInt() }
                ?: error("Status list allocation is missing 'indices'"),
        )
    }

    private fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun kotlinx.serialization.json.JsonObject.strings(name: String): List<String> =
        this[name]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()

    private fun kotlinx.serialization.json.JsonObject.boolean(name: String): Boolean? =
        this[name]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

    private fun kotlinx.serialization.json.JsonObject.requiredString(name: String): String =
        string(name) ?: error("Issuer signing configuration is missing '$name'")

    data class StatusListReference(val uri: String, val indices: List<Int>)

    data class RuntimeSigningConfiguration(
        val trustSystem: TrustSystem,
        val issuerClaim: String?,
        val signing: IssuerProperties.IssuerSigning,
        val issuanceProfileId: String? = null,
    )

    data class RuntimeOperationConfiguration(
        val operation: String,
        val schemaVersion: Int,
        val configuration: kotlinx.serialization.json.JsonObject,
    )

    data class SwissTrustConfiguration(
        val did: String?,
        val identityStatement: String?,
        val issuanceStatements: Map<String, String>,
    )

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
