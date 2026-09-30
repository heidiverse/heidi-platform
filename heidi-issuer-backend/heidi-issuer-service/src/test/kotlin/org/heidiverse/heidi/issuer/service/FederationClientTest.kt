// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class FederationClientTest {
    private val builder = RestClient.builder()
    private val platform = MockRestServiceServer.bindTo(builder).build()
    private val client = FederationClient(
        builder,
        IssuerProperties(platformInternalBaseUrl = "https://platform.example"),
    )

    @Test
    fun `asks the platform to sign the credential issuer's entity configuration`() {
        platform.expect(requestTo(ENDPOINT))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.entityId").value(ENTITY_ID))
            .andExpect(jsonPath("$.metadata.openid_credential_issuer.credential_issuer").value(ENTITY_ID))
            .andRespond(withSuccess("signed.entity.configuration", MediaType.TEXT_PLAIN))

        val configuration = client.entityConfiguration(
            "member", ENTITY_ID, mapOf("credential_issuer" to ENTITY_ID))

        assertEquals("signed.entity.configuration", configuration)
        platform.verify()
    }

    @Test
    fun `returns nothing for an identity outside any federation`() {
        platform.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertNull(client.entityConfiguration("member", ENTITY_ID, emptyMap<String, Any>()))
    }

    private companion object {
        const val ENTITY_ID = "https://issuer.example/member/c/pid/1.0"
        const val ENDPOINT =
            "https://platform.example/internal/platform/v1/identities/member/federation/entity-configuration"
    }
}
