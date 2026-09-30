// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service.utils

import org.heidiverse.heidi.shared.localized.LocalizedValue
import org.kapunsdk.visualization.extensions.calculateSaids
import org.kapunsdk.visualization.extensions.getBundleHash
import org.kapunsdk.visualization.oca.model.CaptureBase
import org.kapunsdk.visualization.oca.model.OcaBundleJson
import org.kapunsdk.visualization.oca.model.content.Encoding
import org.kapunsdk.visualization.oca.model.overlay.Overlay
import org.kapunsdk.visualization.oca.model.overlay.presentation.SensitiveOverlay
import org.kapunsdk.visualization.oca.model.overlay.presentation.UbiqueStyleJsonOverlay
import org.kapunsdk.visualization.oca.model.overlay.semantic.CharacterEncodingOverlay
import org.kapunsdk.visualization.oca.model.overlay.semantic.FormatOverlay
import org.kapunsdk.visualization.oca.model.overlay.semantic.LabelOverlay
import org.heidiverse.heidi.entity.model.credentialscheme.*
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import com.github.mustachejava.DefaultMustacheFactory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.io.Writer
import org.kapunsdk.visualization.oca.model.content.AttributeType as KtAttributeType

class NoHtmlEscape : DefaultMustacheFactory() {
    override fun encode(value: String?, writer: Writer?) {
        if (value == null) {
            return
        }
        try {
            writer?.write(value)
        } catch (ex: IOException) {
            throw RuntimeException(ex)
        }
    }
}

private fun interpolateTitles(
    style: UbiqueStyleJsonOverlay,
    attributes: List<Pair<String, String>>?
): UbiqueStyleJsonOverlay {
    if (attributes == null || attributes.isEmpty()) {
        return style
    }
    val languageMap = attributes.associateBy({ it.first }, { it.second }).toMutableMap()
    if (style.orderedProperties.isNotEmpty()) {
        for (ele in style.orderedProperties) {
            languageMap.put(ele, "{{ $ele }}")
        }
    }
    val newTitle = interpolateText(style.title, languageMap)
    val newSubtitle = interpolateText(style.subtitle, languageMap)

    return style.copy(title = newTitle, subtitle = newSubtitle)
}

private fun interpolateText(text: String, languageMap: Map<String, String>): String {
    val mf = NoHtmlEscape()
    val mustache = mf.compile(StringReader(text), "interpolation")
    val sw = StringWriter()
    mustache.execute(sw, languageMap)
    return sw.toString()
}

private fun getAttributesByLanguage(attributes: Map<String, SchemaCreationRequestMultipleLanguages.AttributeDetails>): Map<String, Map<String, SchemaCreationRequest.AttributeDetails>> {
    val attributesByLanguage = mutableMapOf<String, MutableMap<String, SchemaCreationRequest.AttributeDetails>>()
    attributes.forEach { (fieldName, detail) ->
        val fieldNameJsonPointer = fieldName.replace(".", "/")
        val displayName = detail.displayName.toMapWithLanguageTagKeys()
        displayName.forEach { (language, name) ->
            val attributeMap =
                attributesByLanguage.getOrPut(language) { mutableMapOf<String, SchemaCreationRequest.AttributeDetails>() }
            attributeMap[fieldNameJsonPointer] =
                SchemaCreationRequest.AttributeDetails(
                    name,
                    detail.fieldType,
                    detail.isArray,
                    detail.isSensitive,
                    detail.isDisclosable
                )
            attributesByLanguage[language] = attributeMap
        }
    }
    return attributesByLanguage
}

private fun getLocalizedMetaAttributesNullable(schemeMetadata: CredentialSchemeMetadata): LocalizedValue<ArrayList<kotlin.Pair<String, String>>>? {
    if (schemeMetadata.metaAttributes.isEmpty()) {
        return null
    }
    val localizedMetaAttributeMap = schemeMetadata.metaAttributes.flatMap { metaAttribute ->
        metaAttribute.displayName.toMapWithLanguageTagKeys()
            .map { entry -> kotlin.Triple(metaAttribute.attributeKey, entry.key, entry.value) }
    }.groupBy({ it.second }) { Pair(it.first, it.third) }.mapValues { ArrayList(it.value) }
    return LocalizedValue.fromStringMap(localizedMetaAttributeMap)
}

fun JsonNode.asStyleJson(): UbiqueStyleJsonOverlay {
    // the stylejson is almost an overlay, add missing properties as dummy properties
    (this as ObjectNode).put("capture_base", "")
    this.put("language", "de")

    this.get("title")?.asString()?.replace(".", "/")?.let { newTitle -> this.put("title", newTitle) }
    this.get("subtitle")?.asString()?.replace(".", "/")?.let { newSubtitle -> this.put("subtitle", newSubtitle) }

    (this.get("orderedProperties") as? ArrayNode)?.let { array ->
        for (i in 0 until array.size()) {
            val element = array.get(i).asString()
            val newElement = element.replace(".", "/")
            array.set(i, newElement)
        }
    }

    val jsonString = ObjectMapper().writeValueAsString(this)
    return Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }.decodeFromString<UbiqueStyleJsonOverlay>(jsonString)
}

fun UbiqueStyleJsonOverlay.asJsonNode(): JsonNode {
    val jsonString = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }.encodeToString(this)
    return ObjectMapper().readTree(jsonString)
}

fun OcaBundleJson.toJson(): String {
    return Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }.encodeToString(this)
}

inline fun <reified T> encodeToJson(value: T): String {
    return Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }.encodeToString(value)
}

fun getOcaBundleHash(bundle: OcaBundleJson): String {
    return bundle.getBundleHash()
}

fun generateOcaBundle(
    schemaInformationMultipleLanguages: SchemaInformationMultipleLanguages,
    schemeMetadata: CredentialSchemeMetadata
): OcaBundleJson? {
    val metadata = schemaInformationMultipleLanguages.metadata
    val attributes = schemaInformationMultipleLanguages.attributes
    val style = schemaInformationMultipleLanguages.style.asStyleJson()
    val attributesByLanguage = getAttributesByLanguage(attributes)
    val metaAttributes = getLocalizedMetaAttributesNullable(schemeMetadata)?.toMapWithLanguageTagKeys()

    var schemaInformationLanguageMap = mutableMapOf<SchemaInformation, String>()
    attributesByLanguage.forEach { (language, attributeMap) ->
        schemaInformationLanguageMap.put(
            SchemaInformation(
                metadata,
                attributeMap,
                interpolateTitles(style, metaAttributes?.get(language)).asJsonNode()
            ), language
        )
    }
    if (attributesByLanguage.isEmpty()) {
        schemaInformationLanguageMap.put(
            SchemaInformation(metadata, mapOf(), interpolateTitles(style, metaAttributes?.get("de-CH")).asJsonNode()),
            "de-CH"
        )
    }
    return generateOcaBundle(schemaInformationLanguageMap)
}

fun generateOcaBundle(payload: Map<SchemaInformation, String>?): OcaBundleJson? {
    // Create the capture base node with the digest dummy
    if (payload == null || payload.isEmpty()) {
        return null
    }
    val overlays = mutableListOf<Overlay>()
    // First add language independent values
    val firstRequest = payload.keys.firstOrNull()
    overlays.add(
        CharacterEncodingOverlay(
            defaultCharacterEncoding = Encoding.UTF_8,
            attributeCharacterEncoding = firstRequest?.let { request ->
                request.attributes.mapValues {
                    if (it.value.fieldType == AttributeType.IMAGE) {
                        Encoding.BASE_64
                    } else {
                        Encoding.UTF_8
                    }
                }.filter { it.value != Encoding.UTF_8 }
            } ?: emptyMap(),
            captureBase = "",
        ))
    overlays.add(
        FormatOverlay(
            captureBase = "",
            attributeFormats = firstRequest?.let { request ->
                request.attributes.mapValues {
                    when (it.value.fieldType) {
                        AttributeType.LINK -> "Link[Web]"
                        AttributeType.FILE_DOWNLOAD -> "Link[Download]"
                        AttributeType.PHONE -> "Link[Phone]"
                        AttributeType.MAIL -> "Link[Mail]"
                        else -> "plain/text"
                    }
                }.filter { it.value != "plain/text" }
            } ?: emptyMap()))

    // Add `SensitiveOverlay` for sensitive attributes
    val sensitiveAttributes = firstRequest?.attributes
        ?.filter { it.value.isSensitive }
        ?.map { it.key }
        ?: emptyList()
    if (sensitiveAttributes.isNotEmpty()) {
        overlays.add(
            SensitiveOverlay(
                attributes = sensitiveAttributes,
                captureBase = ""
            )
        )
    }

    // for each language add localized layer
    for (entry in payload.entries) {
        val request = entry.key
        val language = entry.value

        overlays.add(createStyleJsonOverlay(request, language))
        overlays.add(
            LabelOverlay(
                captureBase = "",
                language = language,
                attributeLabels = request.attributes.mapValues {
                    it.value.displayName
                })
        )
    }
    val bundle = OcaBundleJson(captureBase = createCaptureBase(payload.keys.first()), overlays = overlays)
    // generate integrity hashes
    return bundle.calculateSaids()
}

private fun createCaptureBase(request: SchemaInformation): CaptureBase {
    // Add attributes
    val attributesNode = mutableMapOf<String, KtAttributeType>()
    val flaggedAttributesNode: MutableList<String> = mutableListOf()
    for (entry in request.attributes.entries) {
        val attributeName = entry.key
        if (entry.value.fieldType != null) {
            val attributeType: KtAttributeType = mapLegacyAttributeToKtAttribute(entry.value.fieldType)
            attributesNode.put(attributeName, attributeType)
            if (entry.value.isSensitive) {
                flaggedAttributesNode.add(attributeName)
            }
        }
    }
    return CaptureBase(attributes = attributesNode, flaggedAttributes = flaggedAttributesNode)
}

private fun mapLegacyAttributeToKtAttribute(attribute: AttributeType): KtAttributeType {
    return when (attribute) {
        AttributeType.STRING, AttributeType.LOCATION, AttributeType.OTHER -> KtAttributeType.Text
        AttributeType.NUMBER -> KtAttributeType.Numeric
        AttributeType.BOOLEAN -> KtAttributeType.Boolean
        AttributeType.DATE,
        AttributeType.TIME,
        AttributeType.DATETIME,
        AttributeType.DATEOFBIRTH -> KtAttributeType.DateTime

        AttributeType.IMAGE -> KtAttributeType.Binary
        else -> KtAttributeType.Text
    }
}

private fun createStyleJsonOverlay(request: SchemaInformation, language: String): UbiqueStyleJsonOverlay {
    val node = request.style().asStyleJson()
    return node.copy(language = language)
}
