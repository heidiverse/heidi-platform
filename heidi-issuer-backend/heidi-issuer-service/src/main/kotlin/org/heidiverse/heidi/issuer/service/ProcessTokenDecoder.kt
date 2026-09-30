// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import kotlinx.serialization.json.*
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import org.heidiverse.heidi.issuer.model.TrustSystem

@Service
class ProcessTokenDecoder {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(token: String): CredentialData {
        val decoded = String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8)
        val signedJson = decoded.substringBeforeLast('.', decoded)
        val root = json.parseToJsonElement(signedJson).jsonObject
        var actionPayload = root["data"]?.jsonObject ?: error("Process token has no data")
        val deferredTransactionId = actionPayload["deferredTransactionId"]?.jsonPrimitive?.contentOrNull
            ?.let(UUID::fromString)
        val encryptedTxCode = root["txCodeEncrypted"]?.jsonPrimitive?.contentOrNull
        var data = actionPayload
        if (actionPayload["action"] != null) {
            data = actionPayload["data"]?.jsonObject ?: error("Process token action has no data")
        }
        if (data["issuance"] is JsonObject) {
            data = data["issuance"]!!.jsonObject
        }

        val schema = data["schemaIdentifier"]?.jsonObject
            ?: error("Process token has no schemaIdentifier")
        val identifier = schema.string("credentialIdentifier")
        val version = schema.string("version")
        val attributes = decodeAttributes(data)
        return CredentialData(
            credentialIdentifier = identifier,
            credentialVersion = version,
            issuerSlug = data["issuerSlug"]?.jsonPrimitive?.contentOrNull,
            attributes = attributes,
            deferredTransactionId = deferredTransactionId,
            encryptedTxCode = encryptedTxCode,
            attributeUrl = data["attributeUrl"]?.jsonPrimitive?.contentOrNull,
            trustSystem = data["trustSystem"]?.jsonPrimitive?.contentOrNull
                ?.let(TrustSystem::valueOf),
            issuanceProfileId = data["issuanceProfileId"]?.jsonPrimitive?.contentOrNull,
        )
    }

    fun decodeIssuanceData(body: String, issuerSlug: String?): CredentialData {
        val data = json.parseToJsonElement(body).jsonObject
        val schema = data["schemaIdentifier"]?.jsonObject ?: error("Deferred issuance data has no schemaIdentifier")
        val attributes = decodeAttributes(data)
        return CredentialData(
            credentialIdentifier = schema.string("credentialIdentifier"),
            credentialVersion = schema.string("version"),
            issuerSlug = issuerSlug,
            attributes = attributes,
            attributeUrl = data["attributeUrl"]?.jsonPrimitive?.contentOrNull,
            trustSystem = data["trustSystem"]?.jsonPrimitive?.contentOrNull
                ?.let(TrustSystem::valueOf),
            issuanceProfileId = data["issuanceProfileId"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun JsonObject.string(name: String): String =
        this[name]?.jsonPrimitive?.content ?: error("Missing $name in process token")

    private fun decodeAttributes(data: JsonObject): Map<String, CredentialData.Attribute> {
        val values = data["values"]?.jsonObject
        if (values != null) {
            return values.mapValues { (_, value) ->
                CredentialData.Attribute(
                    value = value.toRuntimeValue(),
                    type = "STRING",
                    array = value is JsonArray,
                    nameOverrides = emptyMap(),
                )
            }
        }

        // Accept old signed tokens while active issuance sessions drain. New process requests
        // never produce this shape and cannot supply schema metadata themselves.
        return data["attributes"]?.jsonObject.orEmpty().mapValues { (_, element) ->
            val attribute = element.jsonObject
            val array = attribute["isArray"]?.jsonPrimitive?.booleanOrNull == true
            CredentialData.Attribute(
                value = attribute["value"].toRuntimeValue(array),
                type = attribute["attributeType"]?.jsonPrimitive?.content ?: "STRING",
                array = array,
                nameOverrides = attribute["attributeNameOverrides"]?.jsonObject
                    ?.mapValues { it.value.jsonPrimitive.content }
                    .orEmpty(),
            )
        }
    }

    private fun JsonElement?.toRuntimeValue(arrayEncoded: Boolean = false): Any? {
        if (this == null || this is JsonNull) return null
        if (arrayEncoded && this is JsonPrimitive) {
            return runCatching { json.parseToJsonElement(content).toRuntimeValue() }
                .getOrElse { listOf(content) }
        }
        return when (this) {
            is JsonNull -> null
            is JsonArray -> map { it.toRuntimeValue() }
            is JsonObject -> mapValues { it.value.toRuntimeValue() }
            is JsonPrimitive -> booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
        }
    }
}
