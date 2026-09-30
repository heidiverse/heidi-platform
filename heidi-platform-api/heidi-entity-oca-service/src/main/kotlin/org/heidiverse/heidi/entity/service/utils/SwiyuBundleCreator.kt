// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service.utils

import org.heidiverse.heidi.entity.model.entity.CredentialSchemeAttributeEntity
import org.heidiverse.heidi.entity.model.credentialscheme.AttributeType
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

object SwiyuBundleCreator {
    private const val PROFILE_VERSION = "swiss-profile-vc:1.0.0"
    private const val CAPTURE_TYPE = "spec/capture_base/1.0"
    private const val LABEL_TYPE = "spec/overlays/label/1.1"
    private const val META_TYPE = "spec/overlays/meta/1.0"
    private const val BRANDING_TYPE = "aries/overlays/branding/1.1"
    private const val SOURCE_TYPE = "extend/overlays/data_source/2.0"
    private const val STANDARD_TYPE = "spec/overlays/standard/1.0"
    private const val SENSITIVE_TYPE = "spec/overlays/sensitive/1.0"
    private const val ENCODING_TYPE = "spec/overlays/character_encoding/1.0"
    private const val FORMAT_TYPE = "spec/overlays/format/1.0"
    private const val ORDER_TYPE = "extend/overlays/order/1.0"
    private const val ISO_DATE = "urn:iso:std:iso:8601"
    private const val IMAGE_STANDARD = "urn:ietf:rfc:2083"
    private const val UTF_8 = "utf-8"
    private const val BASE_64 = "base64"
    private const val SD_JWT = "SD_JWT"
    private const val DC_SD_JWT = "dc+sd-jwt"
    private const val VC_SD_JWT = "vc+sd-jwt"
    private const val IMAGE_PNG = "image/png"
    private const val LINK_WEB = "Link[Web]"
    private const val LINK_DOWNLOAD = "Link[Download]"
    private const val LINK_PHONE = "Link[Phone]"
    private const val LINK_MAIL = "Link[Mail]"
    private const val HASH_ALGORITHM = "SHA-256"
    private const val DIGEST_SIZE = 44
    private const val CARD_COLOR = "cardColor"
    private const val BACKGROUND_CARD = "backgroundCard"
    private const val PNG_DATA_PREFIX = "data:image/png;base64,"
    private const val JPEG_DATA_PREFIX = "data:image/jpeg;base64,"
    private val formats = listOf(DC_SD_JWT, VC_SD_JWT)
    private val languagePattern = Regex("^[a-z]{2}(-[A-Z]{2})?$")
    private val colorPattern = Regex("^[0-9a-fA-F]{6}$")

    // Build from all schema attributes; missing translations must not change the capture base.
    @JvmStatic
    fun create(name: String, attributes: List<CredentialSchemeAttributeEntity>): String {
        return create(name, attributes, null)
    }

    @JvmStatic
    fun create(
        name: String,
        attributes: List<CredentialSchemeAttributeEntity>,
        styleJson: String?,
    ): String {
        val ordered = attributes.sortedBy { it.fieldName }
        val branding = parseBranding(styleJson)
        val capture = buildJsonObject {
            put("type", CAPTURE_TYPE)
            put("digest", "#".repeat(DIGEST_SIZE))
            putJsonObject("attributes") {
                ordered.forEach { put(it.fieldName, attributeType(it)) }
            }
        }
        val digest = hash(canonical(capture).toString())
        val overlays = mutableListOf<JsonObject>()
        formats.forEach { format ->
            overlays += overlay(SOURCE_TYPE, digest) {
                put("format", format)
                putJsonObject("attribute_sources") {
                    ordered.forEach { attribute ->
                        val path = attribute.formatSpecificAttributeName[SD_JWT]
                            ?.takeIf { it.isNotBlank() }
                            ?: attribute.fieldName
                        putJsonArray(attribute.fieldName) {
                            path.split('.').forEach { add(it) }
                            if (attribute.isArray) add(JsonNull)
                        }
                    }
                }
            }
        }

        val labels = sortedMapOf<String, MutableMap<String, String>>()
        ordered.forEach { attribute ->
            attribute.displayName.orEmpty().toSortedMap().forEach { (language, label) ->
                if (label.isBlank()) return@forEach
                val locale = Locale.forLanguageTag(language.replace('_', '-'))
                val tags = buildList {
                    locale.toLanguageTag().takeIf { languagePattern.matches(it) }?.let(::add)
                    locale.language.takeIf { languagePattern.matches(it) && it !in this }?.let(::add)
                }
                tags.forEach { tag ->
                    // swiyu's default language priority contains base tags only.
                    labels.getOrPut(tag) { sortedMapOf() }
                        .putIfAbsent(attribute.fieldName, label)
                }
            }
        }
        if (labels.isEmpty()) labels["en"] = sortedMapOf()
        labels.forEach { (language, values) ->
            overlays += overlay(LABEL_TYPE, digest) {
                put("language", language)
                put("attribute_labels", JsonObject(values.mapValues { JsonPrimitive(it.value) }))
            }
            overlays += overlay(META_TYPE, digest) {
                put("language", language)
                put("name", name)
            }
            // Keep card styling in the standard overlay understood by swiyu.
            if (branding != null) overlays += overlay(BRANDING_TYPE, digest) {
                put("language", language)
                branding.color?.let { put("primary_background_color", it) }
                branding.image?.let { put("background_image", it) }
            }
        }

        val standardAttributes = ordered.filter {
            baseType(it.fieldType) == "DateTime" || it.fieldType == AttributeType.IMAGE
        }
        if (standardAttributes.isNotEmpty()) overlays += overlay(STANDARD_TYPE, digest) {
            put("attr_standards", JsonObject(standardAttributes.associate {
                it.fieldName to JsonPrimitive(
                    if (it.fieldType == AttributeType.IMAGE) IMAGE_STANDARD else ISO_DATE
                )
            }))
        }

        val images = ordered.filter { it.fieldType == AttributeType.IMAGE }
        overlays += overlay(ENCODING_TYPE, digest) {
            put("default_character_encoding", UTF_8)
            if (images.isNotEmpty()) {
                put("attribute_character_encoding", JsonObject(images.associate {
                    it.fieldName to JsonPrimitive(BASE_64)
                }))
            }
        }

        val formatted = ordered.filter { it.fieldType == AttributeType.IMAGE || it.fieldType in setOf(
            AttributeType.LINK, AttributeType.FILE_DOWNLOAD, AttributeType.PHONE, AttributeType.MAIL
        ) }
        if (formatted.isNotEmpty()) overlays += overlay(FORMAT_TYPE, digest) {
            put("attribute_formats", JsonObject(formatted.associate {
                it.fieldName to JsonPrimitive(format(it.fieldType))
            }))
        }

        overlays += overlay(ORDER_TYPE, digest) {
            put("attribute_orders", JsonObject(ordered.mapIndexed { index, attribute ->
                attribute.fieldName to JsonPrimitive(index + 1)
            }.toMap()))
        }

        val sensitive = ordered.filter { it.isSensitive }
        if (sensitive.isNotEmpty()) overlays += overlay(SENSITIVE_TYPE, digest) {
            putJsonArray("attributes") { sensitive.forEach { add(it.fieldName) } }
        }

        return canonical(buildJsonObject {
            put("profile_version", PROFILE_VERSION)
            putJsonArray("capture_bases") { add(JsonObject(capture + ("digest" to JsonPrimitive(digest)))) }
            put("overlays", JsonArray(overlays))
        }).toString()
    }

    private data class Branding(val color: String?, val image: String?)

    private fun parseBranding(styleJson: String?): Branding? {
        val style = styleJson?.let {
            runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull()
        } ?: return null
        val color = style[CARD_COLOR]?.let { parseColor(it) }
        val image = style[BACKGROUND_CARD]
            ?.takeIf { it !is JsonNull }
            ?.jsonPrimitive?.content?.let(::parseImage)
        return Branding(color, image).takeIf { it.color != null || it.image != null }
    }

    private fun parseColor(value: JsonElement): String? {
        val raw = value.jsonPrimitive.content.trim()
        val hex = if (value.jsonPrimitive.isString) raw.removePrefix("#") else {
            val number = raw.toLongOrNull() ?: return null
            "%06X".format(Locale.ROOT, number and 0xFFFFFF)
        }
        return hex.takeIf { colorPattern.matches(it) }?.let { "#${it.uppercase(Locale.ROOT)}" }
    }

    private fun parseImage(value: String): String? {
        if (!value.startsWith(PNG_DATA_PREFIX) && !value.startsWith(JPEG_DATA_PREFIX)) return null
        val payload = value.substringAfter(';').substringAfter(',')
        if (payload.isEmpty()) return null
        return runCatching {
            Base64.getDecoder().decode(payload)
            value
        }.getOrNull()
    }

    @JvmStatic
    fun hash(value: String): String {
        val canonicalValue = runCatching {
            canonical(Json.parseToJsonElement(value)).toString()
        }.getOrElse { value }
        val digest = MessageDigest.getInstance(HASH_ALGORITHM).digest(canonicalValue.toByteArray(Charsets.UTF_8))
        return "I" + Base64.getUrlEncoder().withoutPadding().encodeToString(byteArrayOf(0) + digest).drop(1)
    }

    private fun overlay(type: String, digest: String, content: JsonObjectBuilder.() -> Unit) = buildJsonObject {
        put("type", type)
        put("capture_base", digest)
        content()
    }

    private fun attributeType(attribute: CredentialSchemeAttributeEntity): String {
        val type = baseType(attribute.fieldType)
        return if (attribute.isArray) "Array[$type]" else type
    }

    private fun baseType(type: AttributeType): String = when (type) {
        AttributeType.NUMBER -> "Numeric"
        AttributeType.BOOLEAN -> "Boolean"
        AttributeType.DATE, AttributeType.TIME, AttributeType.DATETIME, AttributeType.DATEOFBIRTH -> "DateTime"
        AttributeType.IMAGE -> "Binary"
        else -> "Text"
    }

    private fun format(type: AttributeType): String = when (type) {
        AttributeType.IMAGE -> IMAGE_PNG
        AttributeType.LINK -> LINK_WEB
        AttributeType.FILE_DOWNLOAD -> LINK_DOWNLOAD
        AttributeType.PHONE -> LINK_PHONE
        AttributeType.MAIL -> LINK_MAIL
        else -> "plain/text"
    }

    // Generated objects contain only strings, arrays and null: sorting keys suffices for JCS.
    private fun canonical(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.toSortedMap().mapValues { canonical(it.value) })
        is JsonArray -> JsonArray(value.map { canonical(it) })
        else -> value
    }
}
