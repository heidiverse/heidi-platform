// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import kotlinx.serialization.json.*
import org.heidiverse.heidi.issuer.model.api.IssuerMetadata
import org.heidiverse.heidi.shared.oca.OcaFormat
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class SchemaMetadataService(
    builder: RestClient.Builder,
    private val properties: IssuerProperties,
) {
    private val client = builder.baseUrl(properties.platformInternalBaseUrl).build()
    private val json = Json { ignoreUnknownKeys = true }

    fun resolve(identifier: String, version: String): SchemaMetadata {
        val root = fetchJson("/public/v2/schema/{identifier}/{version}", identifier, version)
        val detail = fetchJson("/public/v2/schema/{identifier}/{version}/detail", identifier, version)
        val supportedFormats = detail.findStringArray("supportedCredentialTypes")
            .mapNotNullTo(linkedSetOf()) { runCatching { CredentialFormat.valueOf(it) }.getOrNull() }
        require(supportedFormats.isNotEmpty()) {
            "Issuance schema '$identifier' version '$version' has no supported issuer credential format"
        }
        val vct = root.findString("vct") ?: "urn:heidi:$identifier:$version"
        val docType = detail.findString("doctype")
            ?: detail.findString("docType")
            ?: root.findString("doctype")
            ?: root.findString("docType")
            ?: "org.heidiverse.$identifier"
        val ocaBundleFileName = root.findOcaFileName()
            ?: detail.findString("ocaBundleFilename")?.takeIf { it.isNotBlank() }
        val legacyOcaBundleFileName = detail.findString("ocaBundleFilename")
            ?.takeIf { it.isNotBlank() }
        val ocaFormat = detail.findString("ocaVersion")?.let { value ->
            if (value.equals("SWIYU", ignoreCase = true)) OcaFormat.SWIYU else OcaFormat.LEGACY
        } ?: if (ocaBundleFileName != null && legacyOcaBundleFileName != null &&
            ocaBundleFileName != legacyOcaBundleFileName
        ) {
            OcaFormat.SWIYU
        } else {
            OcaFormat.LEGACY
        }
        return SchemaMetadata(
            vct = vct,
            vctMetadataUri = metadataUri(identifier, version, vct),
            docType = docType,
            namespace = detail.findString("namespace")
                ?: root.findString("namespace")
                ?: docType,
            typeMetadata = root,
            supportedFormats = supportedFormats,
            bbsCredentialType = detail.findString("bbsCredentialType"),
            issuerClaimOverride = detail.findString("issClaimOverride"),
            maxBatchSize = detail.findInt("maxBatchSize") ?: 1,
            ocaBundleFileName = ocaBundleFileName,
            credentialDisplay = credentialDisplay(detail, identifier, version),
            attributeMetadata = attributeMetadata(detail),
            ocaFormat = ocaFormat,
            legacyOcaBundleFileName = legacyOcaBundleFileName,
        )
    }

    private fun metadataUri(identifier: String, version: String, vct: String): String? {
        // Keep this response unpinned: its OCA rendering varies by wallet User-Agent.
        val publicBaseUrl = properties.platformPublicBaseUrl.trim().trimEnd('/')
        if (publicBaseUrl.isNotBlank()) {
            return "$publicBaseUrl/public/v2/schema/$identifier/$version"
        }
        return vct.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }

    private fun attributeMetadata(detail: JsonElement): Map<String, AttributeMetadata> {
        val attributes = (detail as? JsonObject)?.get("attributes") as? JsonArray ?: return emptyMap()
        return attributes.mapNotNull { element ->
            val attribute = element as? JsonObject ?: return@mapNotNull null
            val name = attribute["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            name to AttributeMetadata(
                type = attribute["type"]?.jsonPrimitive?.contentOrNull ?: "STRING",
                array = attribute["isArray"]?.jsonPrimitive?.booleanOrNull == true,
                nameOverrides = (attribute["attributeNameOverrides"] as? JsonObject)
                    ?.mapValues { it.value.jsonPrimitive.content }
                    .orEmpty(),
            )
        }.toMap()
    }

    private fun credentialDisplay(detail: JsonElement, identifier: String, version: String): CredentialDisplay {
        val root = detail as? JsonObject
        val style = root?.get("credentialSchemeStyleDetails")
            ?.let { it as? JsonArray }
            ?.lastOrNull()
            ?.let { it as? JsonObject }
            ?.get("style") as? JsonObject
        return CredentialDisplay(
            name = root?.get("displayName")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: "$identifier:$version",
            locale = style?.string("locale") ?: style?.string("language"),
            backgroundColor = style?.get("cardColor")?.cssBackgroundColor(),
            textColor = style?.string("textColor")?.let(::cssTextColor),
            backgroundImageUri = style?.string("backgroundCard"),
        )
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonElement.cssBackgroundColor(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        primitive.longOrNull?.let(::argbToCssColor)?.let { return it }
        return primitive.contentOrNull?.takeIf { it.isNotBlank() }
    }

    private fun cssTextColor(value: String): String = when (value.lowercase()) {
        "light" -> "#FFFFFF"
        "dark" -> "#000000"
        else -> value
    }

    private fun argbToCssColor(value: Long): String {
        val argb = value and 0xffffffffL
        val alpha = (argb shr 24) and 0xff
        val red = (argb shr 16) and 0xff
        val green = (argb shr 8) and 0xff
        val blue = argb and 0xff
        return if (alpha == 0xffL) {
            "#%02X%02X%02X".format(red, green, blue)
        } else {
            val opacity = (alpha / 255.0).toString().trimEnd('0').trimEnd('.')
            "rgba($red, $green, $blue, $opacity)"
        }
    }

    private fun fetchJson(path: String, identifier: String, version: String): JsonElement {
        val body = client.get()
            .uri(path, identifier, version)
            .retrieve()
            .body(String::class.java)
            ?: error("Entity service returned an empty schema response")
        return json.parseToJsonElement(body)
    }

    private fun JsonElement?.findOcaFileName(): String? {
        val current = this ?: return null
        if (current is JsonObject) {
            current["uri"]?.jsonPrimitive?.contentOrNull?.let { uri ->
                OCA_URI.find(uri)?.groupValues?.get(1)?.let { return it }
            }
            current.values.forEach { child -> child.findOcaFileName()?.let { return it } }
        }
        if (current is JsonArray) {
            current.forEach { child -> child.findOcaFileName()?.let { return it } }
        }
        return null
    }

    private fun JsonElement?.findString(name: String): String? {
        val current = this ?: return null
        if (current is JsonObject) {
            current[name]?.jsonPrimitive?.contentOrNull?.let { return it }
            current.values.forEach { child -> child.findString(name)?.let { return it } }
        }
        if (current is JsonArray) {
            current.forEach { child -> child.findString(name)?.let { return it } }
        }
        return null
    }

    private fun JsonElement?.findStringArray(name: String): List<String> {
        val current = this ?: return emptyList()
        if (current is JsonObject) {
            current[name]?.let { value ->
                if (value is JsonArray) return value.mapNotNull { it.jsonPrimitive.contentOrNull }
            }
            current.values.forEach { child ->
                child.findStringArray(name).takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        if (current is JsonArray) {
            current.forEach { child ->
                child.findStringArray(name).takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return emptyList()
    }

    private fun JsonElement?.findInt(name: String): Int? {
        val current = this ?: return null
        if (current is JsonObject) {
            current[name]?.jsonPrimitive?.intOrNull?.let { return it }
            current.values.forEach { child -> child.findInt(name)?.let { return it } }
        }
        if (current is JsonArray) {
            current.forEach { child -> child.findInt(name)?.let { return it } }
        }
        return null
    }

    companion object {
        private val OCA_URI = Regex("/oca/([^/?]+)\\.json(?:\\?.*)?$")
    }

    enum class CredentialFormat {
        SD_JWT,
        MSO_MDOC,
        ZKP_VC,
        W3C_VCDM,
    }

    data class SchemaMetadata(
        val vct: String,
        val docType: String,
        val typeMetadata: JsonElement?,
        val supportedFormats: Set<CredentialFormat>,
        val bbsCredentialType: String?,
        val maxBatchSize: Int = 1,
        val ocaBundleFileName: String? = null,
        val credentialDisplay: CredentialDisplay? = null,
        val issuerClaimOverride: String? = null,
        val namespace: String = docType,
        val attributeMetadata: Map<String, AttributeMetadata> = emptyMap(),
        val ocaFormat: OcaFormat = OcaFormat.LEGACY,
        val legacyOcaBundleFileName: String? = null,
        val vctMetadataUri: String? = null,
    )

    data class AttributeMetadata(
        val type: String,
        val array: Boolean,
        val nameOverrides: Map<String, String>,
    )

    data class CredentialDisplay(
        val name: String,
        val locale: String? = null,
        val backgroundColor: String? = null,
        val backgroundImageUri: String? = null,
        val textColor: String? = null,
    ) {
        fun toOid4vci() = IssuerMetadata.CredentialDisplay(
            name,
            locale,
            backgroundColor,
            backgroundImageUri?.let { IssuerMetadata.BackgroundImage(it) },
            textColor,
        )
    }
}
