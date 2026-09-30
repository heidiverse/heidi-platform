// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.heidiverse.heidi.issuer.model.api.IssuerMetadata
import org.heidiverse.heidi.issuer.model.api.TokenResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

class IssuanceMetadataTest {
    @Test
    fun `SD-JWT metadata advertises the type metadata URI without integrity`() {
        val configuration = IssuerMetadata.CredentialConfiguration(
            "dc+sd-jwt",
            "urn:example:card",
            null,
            null,
            listOf("jwk"),
            listOf("ES256"),
            IssuerMetadata.ProofTypesSupported(IssuerMetadata.JwtProofType(listOf("ES256"))),
            null,
            null,
            "https://platform.example/public/v2/schema/card/1.0.0",
        )

        val json: JsonNode = ObjectMapper().valueToTree(configuration)

        assertEquals(
            "https://platform.example/public/v2/schema/card/1.0.0",
            json["vct_metadata_uri"].asString(),
        )
        assertFalse(json.has("vct_metadata_uri#integrity"))
    }

    @Test
    fun `W3C metadata uses credential definition types instead of vct`() {
        val configuration = w3cCredentialConfiguration("ExampleCredential", "ES256")

        assertEquals(null, configuration.vct)
        assertEquals(
            listOf("VerifiableCredential", "ExampleCredential"),
            configuration.credentialDefinition.type,
        )

        val json: JsonNode = ObjectMapper().valueToTree(configuration)
        assertFalse(json.has("vct"))
        assertEquals(
            "ExampleCredential",
            json["credential_definition"]["type"][1].asString(),
        )
    }

    @Test
    fun `optional typed response fields are omitted from JSON`() {
        val response = TokenResponse("token", "Bearer", 300, "nonce", 300, null)

        val json: JsonNode = ObjectMapper().valueToTree(response)

        assertEquals("token", json["access_token"].asString())
        assertFalse(json.has("refresh_token"))
    }
}
