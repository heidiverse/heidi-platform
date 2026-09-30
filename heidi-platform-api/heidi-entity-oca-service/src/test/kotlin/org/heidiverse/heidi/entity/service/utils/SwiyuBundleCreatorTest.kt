// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service.utils

import kotlinx.serialization.json.*
import org.heidiverse.heidi.entity.model.credentialscheme.AttributeType
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeAttributeEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.Base64

class SwiyuBundleCreatorTest {
    @Test
    fun `creates mapped multilingual bundle`() {
        val givenName = attribute(
            "givenName",
            AttributeType.STRING,
            mapOf("de-CH" to "Vorname", "de" to "", "en" to "Given name"),
        )
        givenName.formatSpecificAttributeName = mapOf("SD_JWT" to "given_name")
        val roles = attribute("roles", AttributeType.STRING, mapOf("fr" to "Rôles"))
        roles.isArray = true
        roles.isSensitive = true
        val birthDate = attribute("person.birthDate", AttributeType.DATE, mapOf("en" to "Birth date"))
        val photo = attribute("photo", AttributeType.IMAGE, mapOf("en" to "Photo"))
        val website = attribute("website", AttributeType.LINK, mapOf("en" to "Website"))

        val raw = SwiyuBundleCreator.create("Employee card", listOf(givenName, roles, birthDate, photo, website))
        val bundle = Json.parseToJsonElement(raw).jsonObject
        assertEquals("swiss-profile-vc:1.0.0", bundle.getValue("profile_version").jsonPrimitive.content)
        assertTrue(bundle["capture_bases"] is JsonArray, "swiyu requires capture_bases")
        assertFalse(bundle.containsKey("capture_base"))
        val capture = bundle.getValue("capture_bases").jsonArray.single().jsonObject
        val attributes = capture.getValue("attributes").jsonObject
        assertEquals(setOf("givenName", "roles", "person.birthDate", "photo", "website"), attributes.keys)
        assertEquals("Array[Text]", attributes.getValue("roles").jsonPrimitive.content)
        assertEquals("DateTime", attributes.getValue("person.birthDate").jsonPrimitive.content)

        val overlays = bundle.getValue("overlays").jsonArray.map { it.jsonObject }
        val sources = overlays.filter { it["type"]?.jsonPrimitive?.content == "extend/overlays/data_source/2.0" }
        assertEquals(setOf("vc+sd-jwt", "dc+sd-jwt"), sources.map { it.getValue("format").jsonPrimitive.content }.toSet())
        sources.forEach {
            val paths = it.getValue("attribute_sources").jsonObject
            assertEquals(Json.parseToJsonElement("[\"given_name\"]"), paths["givenName"])
            assertEquals(Json.parseToJsonElement("[\"roles\",null]"), paths["roles"])
            assertEquals(Json.parseToJsonElement("[\"person\",\"birthDate\"]"), paths["person.birthDate"])
        }
        val labels = overlays.filter { it["type"]?.jsonPrimitive?.content == "spec/overlays/label/1.1" }
        assertEquals(setOf("de-CH", "de", "en", "fr"), labels.map { it.getValue("language").jsonPrimitive.content }.toSet())
        assertEquals("Vorname", labels.single { it["language"]?.jsonPrimitive?.content == "de-CH" }
            .getValue("attribute_labels").jsonObject.getValue("givenName").jsonPrimitive.content)
        assertEquals("Vorname", labels.single { it["language"]?.jsonPrimitive?.content == "de" }
            .getValue("attribute_labels").jsonObject.getValue("givenName").jsonPrimitive.content)
        assertTrue(overlays.any { it["type"]?.jsonPrimitive?.content == "spec/overlays/meta/1.0" })
        assertTrue(overlays.any { it["type"]?.jsonPrimitive?.content == "spec/overlays/standard/1.0" })
        val standardsOverlay = overlays.single {
            it["type"]?.jsonPrimitive?.content == "spec/overlays/standard/1.0"
        }
        assertEquals("urn:ietf:rfc:2083", standardsOverlay.getValue("attr_standards")
            .jsonObject.getValue("photo").jsonPrimitive.content)
        assertTrue(overlays.any { it["type"]?.jsonPrimitive?.content == "spec/overlays/character_encoding/1.0" })
        assertTrue(overlays.any { it["type"]?.jsonPrimitive?.content == "spec/overlays/format/1.0" })
        val formatsOverlay = overlays.single {
            it["type"]?.jsonPrimitive?.content == "spec/overlays/format/1.0"
        }
        assertEquals("image/png", formatsOverlay.getValue("attribute_formats")
            .jsonObject.getValue("photo").jsonPrimitive.content)
        assertTrue(overlays.any { it["type"]?.jsonPrimitive?.content == "extend/overlays/order/1.0" })
        assertTrue(overlays.all { it["capture_base"] == capture["digest"] })
        assertEquals(raw, SwiyuBundleCreator.create("Employee card", listOf(website, birthDate, roles, givenName, photo)))
    }

    @Test
    fun `capture digest matches swiss CESR`() {
        val bundle = Json.parseToJsonElement(SwiyuBundleCreator.create("Card", emptyList())).jsonObject
        assertTrue(bundle["capture_bases"] is JsonArray, "swiyu requires capture_bases")
        val capture = bundle.getValue("capture_bases").jsonArray.single().jsonObject
        // This fixture contains only strings and an empty object, so key sorting is JCS-equivalent.
        val dummy = JsonObject((capture + ("digest" to JsonPrimitive("#".repeat(44)))).toSortedMap())
        val digest = MessageDigest.getInstance("SHA-256").digest(dummy.toString().toByteArray())
        val expected = "I" + Base64.getUrlEncoder().withoutPadding().encodeToString(byteArrayOf(0) + digest).drop(1)
        assertEquals(expected, capture.getValue("digest").jsonPrimitive.content)
    }

    @Test
    fun `maps card style to branding overlay`() {
        val raw = SwiyuBundleCreator.create(
            "Card",
            listOf(attribute("givenName", AttributeType.STRING, mapOf("en" to "Given name"))),
            """{"cardColor":4279312947,"backgroundCard":"data:image/png;base64,AA=="}""",
        )
        val overlays = Json.parseToJsonElement(raw).jsonObject.getValue("overlays").jsonArray
            .map { it.jsonObject }
        val branding = overlays.single { it.getValue("type").jsonPrimitive.content == "aries/overlays/branding/1.1" }
        assertEquals("#112233", branding.getValue("primary_background_color").jsonPrimitive.content)
        assertEquals("data:image/png;base64,AA==", branding.getValue("background_image").jsonPrimitive.content)
    }

    private fun attribute(name: String, type: AttributeType, labels: Map<String, String>) =
        CredentialSchemeAttributeEntity().apply {
            fieldName = name
            fieldType = type
            displayName = labels
        }

}
