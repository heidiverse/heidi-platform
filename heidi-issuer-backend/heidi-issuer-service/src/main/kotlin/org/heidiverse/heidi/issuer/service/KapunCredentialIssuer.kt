// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import org.kapunsdk.credentials.Mdoc
import org.kapunsdk.credentials.SdJwt
import org.kapunsdk.credentials.W3C
import org.kapunsdk.credentials.createDisclosureForObject
import org.kapunsdk.credentials.toClaimsPointer
import org.kapunsdk.util.extensions.fromJsonElement
import org.kapunsdk.util.extensions.asObject
import org.kapunsdk.util.extensions.isObject
import org.kapunsdk.util.extensions.toCbor
import org.springframework.stereotype.Service
import uniffi.kapun_util_rust.Value
import uniffi.kapun_dcql_sdjwt_rust.Header
import uniffi.kapun_dcql_sdjwt_rust.SdJwtHasher
import java.time.Instant
import java.util.Base64
import java.util.HashMap
import org.heidiverse.heidi.issuer.model.TrustSystem

private const val RENDER_CLAIM = "render"
private const val VCT_METADATA_URI_CLAIM = "vct_metadata_uri"

@Service
class KapunCredentialIssuer(
    private val signatureClient: SignatureClient,
    private val properties: IssuerProperties,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun issueSdJwt(
        data: CredentialData,
        issuerSlug: String,
        issuer: String,
        vct: String,
        holderJwk: String?,
        snapshot: IssuerFlowSnapshot,
        trustSystem: TrustSystem = TrustSystem.Default,
        ocaUrl: String? = null,
        statusList: CredentialData.StatusListReference? = null,
        vctMetadataUri: String? = null,
    ): String {
        val signer = signatureClient.fromSnapshot(issuerSlug, snapshot)
        val keyId = publishedIssuerJwkKeyId(signer.configuration, issuerSlug)
        val now = Instant.now().epochSecond
        val claims = linkedMapOf<String, Any?>(
            "iss" to issuer,
            "vct" to vct,
            "iat" to now,
            "nbf" to now,
            "exp" to now + properties.credentialLifetimeSeconds,
        )
        val disclosurePaths = mutableListOf<List<Any?>>()
        vctMetadataUri?.let { claims[VCT_METADATA_URI_CLAIM] = it }
        statusList?.let { claims["status"] = statusClaim(it) }
        ocaUrl?.let {
            claims[RENDER_CLAIM] = hashMapOf(
                "type" to "OverlaysCaptureBundleV1",
                "oca" to it,
            )
            disclosurePaths += listOf(RENDER_CLAIM)
        }
        data.attributes.forEach { (name, attribute) ->
            val path = attribute.nameOverrides["SD_JWT"] ?: name
            putNested(claims, path, attribute.value)
            disclosurePaths += path.split('.')
        }
        val claimsValue = claims.toCbor().toUnorderedValue()
        val holderValue = holderJwk?.let { Value.fromJsonElement(json.parseToJsonElement(it)) }
        val disclosures = disclosurePaths.mapNotNull { it.toClaimsPointer() }
        if (signer.configuration.certificateChain.isNotEmpty()) {
            return createCertificateBackedSdJwt(
                claimsValue, disclosures, keyId, signer, holderValue,
            )
        }
        return SdJwt.create(
            claimsValue, disclosures, keyId, signer, holderValue, "sha-256",
        )?.innerJwt?.originalSdjwt ?: error("Kapun could not create the SD-JWT VC")
    }

    fun issueMdoc(
        data: CredentialData,
        issuerSlug: String,
        docType: String,
        namespace: String,
        holderJwk: String,
        snapshot: IssuerFlowSnapshot,
        trustSystem: TrustSystem = TrustSystem.Default,
    ): String {
        val signer = signatureClient.fromSnapshot(issuerSlug, snapshot)
        val namespaceAttributes = linkedMapOf<String, Any?>()
        data.attributes.forEach { (name, attribute) ->
            namespaceAttributes[attribute.nameOverrides["MSO_MDOC"] ?: name] = attribute.value
        }
        val propertiesValue = mapOf(namespace to namespaceAttributes).toCbor()
        val holderValue = Value.fromJsonElement(json.parseToJsonElement(holderJwk))
        val certificates = signer.configuration.certificateChain.map(Base64.getDecoder()::decode)
        require(certificates.isNotEmpty()) {
            "A certificate chain is required for mdoc issuer '$issuerSlug'"
        }
        return Mdoc.create(propertiesValue, signer, docType, certificates, holderValue)
            .getOrThrow()
            .mdoc
            .originalMdoc
    }

    fun issueW3c(
        data: CredentialData,
        issuerSlug: String,
        issuer: String,
        vct: String,
        holderJwk: String?,
        snapshot: IssuerFlowSnapshot,
        trustSystem: TrustSystem = TrustSystem.Default,
        ocaUrl: String? = null,
        statusList: CredentialData.StatusListReference? = null,
    ): String {
        val signer = signatureClient.fromSnapshot(issuerSlug, snapshot)
        val keyId = publishedIssuerJwkKeyId(signer.configuration, issuerSlug)
        val now = Instant.now()
        val credentialSubject = HashMap<String, Any?>()
        val disclosurePaths = mutableListOf<List<Any?>>()
        data.attributes.forEach { (name, attribute) ->
            val path = attribute.nameOverrides["W3C_VCDM"] ?: name
            putNested(credentialSubject, path, attribute.value)
            disclosurePaths += listOf("credentialSubject") + path.split('.')
        }
        val claims = hashMapOf<String, Any?>(
            "@context" to listOf("https://www.w3.org/ns/credentials/v2"),
            "type" to listOf("VerifiableCredential", vct),
            "issuer" to issuer,
            "validFrom" to now.toString(),
            "validUntil" to now.plusSeconds(properties.credentialLifetimeSeconds).toString(),
            "credentialSubject" to credentialSubject,
        )
        statusList?.let { claims["status"] = statusClaim(it) }
        ocaUrl?.let {
            claims[RENDER_CLAIM] = hashMapOf(
                "type" to "OverlaysCaptureBundleV1",
                "oca" to it,
            )
            disclosurePaths += listOf(RENDER_CLAIM)
        }
        val holderValue = holderJwk?.let { Value.fromJsonElement(json.parseToJsonElement(it)) }
        val credential = W3C.SdJwt.create(
            claims.toCbor().toUnorderedValue(),
            disclosurePaths.mapNotNull { it.toClaimsPointer() },
            keyId,
            signer,
            holderValue,
            "sha-256",
        ) ?: error("Kapun could not create the W3C VC")
        return credential.inner.originalSdjwt
    }

    @Suppress("UNCHECKED_CAST")
    private fun putNested(root: MutableMap<String, Any?>, path: String, value: Any?) {
        val parts = path.split('.')
        var current = root
        parts.dropLast(1).forEach { part ->
            current = current.getOrPut(part) { HashMap<String, Any?>() } as MutableMap<String, Any?>
        }
        current[parts.last()] = value
    }

    private fun statusClaim(reference: CredentialData.StatusListReference) = mapOf(
        "status_list" to mapOf(
            "idx" to reference.index,
            "uri" to reference.uri,
        ),
    )

    private fun publishedIssuerJwkKeyId(
        configuration: IssuerProperties.IssuerSigning,
        issuerSlug: String,
    ): String? {
        val jwk = json.parseToJsonElement(configuration.issuerJwk)
        require(jwk is JsonObject && jwk.isNotEmpty()) {
            "Issuer JWK is required for SD-JWT issuer '$issuerSlug'"
        }
        val jwkKeyId = jwk["kid"]?.jsonPrimitive?.content
        require(
            jwkKeyId.isNullOrBlank() || jwkKeyId == configuration.keyId ||
                jwkKeyId.endsWith("#${configuration.keyId}"),
        ) {
            "Issuer JWK kid '$jwkKeyId' does not match configured key-id '${configuration.keyId}' for SD-JWT issuer '$issuerSlug'; when the published issuer JWK contains a kid, that kid is emitted in the SD-JWT header and must match the configured key-id for verifier key selection"
        }
        return jwkKeyId?.takeIf { it.isNotBlank() }
    }

    private fun createCertificateBackedSdJwt(
        claims: Value,
        disclosures: List<org.kapunsdk.credentials.ClaimsPointer>,
        keyId: String?,
        signer: SignatureClient.KapunsSignaturCreator,
        holderJwk: Value?,
    ): String {
        require(claims.isObject()) { "SD-JWT claims must be an object" }
        val keyClaims = claims.asObject()!!.toMutableMap()
        holderJwk?.let { keyClaims["cnf"] = Value.Object(mapOf("jwk" to it)) }
        val disclosure = createDisclosureForObject(
            Value.Object(keyClaims), disclosures, 1, SdJwtHasher.fromStr("sha-256"),
        ).getOrThrow()
        val header = Header(
            typ = "dc+sd-jwt",
            alg = signer.alg(),
            kid = keyId,
            x5c = signer.configuration.certificateChain,
        )
        val headerEncoded = base64Url(json.encodeToString(header).encodeToByteArray())
        val bodyEncoded = base64Url(json.encodeToString(disclosure.disclosedObject).encodeToByteArray())
        val payload = "$headerEncoded.$bodyEncoded"
        val signature = base64Url(signer.sign(payload.encodeToByteArray()))
        return "$payload.$signature~${disclosure.disclosure.joinToString("~")}~"
    }

    private fun base64Url(value: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)
}

/** Kapun RC1's W3C builder rejects its own OrderedObject representation. */
internal fun Value.toUnorderedValue(): Value = when (this) {
    is Value.OrderedObject -> Value.Object(
        v1.entries.associate { entry ->
            (entry.key as Value.String).v1 to entry.value.toUnorderedValue()
        },
    )
    is Value.Object -> Value.Object(v1.mapValues { it.value.toUnorderedValue() })
    is Value.Array -> Value.Array(v1.map { it.toUnorderedValue() })
    else -> this
}
