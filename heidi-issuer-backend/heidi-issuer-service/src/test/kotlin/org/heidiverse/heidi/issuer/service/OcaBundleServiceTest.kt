// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class OcaBundleServiceTest {
    @Test
    fun `loads the generated OCA bundle from the platform`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val service = OcaBundleService(
            builder,
            IssuerProperties(platformInternalBaseUrl = "https://platform.example"),
        )
        val bundle = """{"capture_base":{"type":"spec/capture_base/1.0"},"overlays":[]}"""

        server.expect(requestTo("https://platform.example/public/v2/oca/Eexample"))
            .andRespond(withSuccess(bundle, MediaType.APPLICATION_JSON))

        assertEquals(bundle, service.resolve("Eexample"))
        server.verify()
    }
}
