// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.ws;

import org.heidiverse.heidi.issuer.service.IssuerProperties;
import org.heidiverse.heidi.issuer.service.OcaBundleService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OcaControllerTest {
    @Test
    void routesSwiyuRequests() throws Exception {
        assertBundle("/oca/test.json", "swiyuWallet", "?format=swiyu", SWIYU);
        assertBundle("/oca/test.json", "swiyuSandboxWallet", "?format=swiyu", SWIYU);
    }

    @Test
    void keepsLegacyDefault() throws Exception {
        assertBundle("/oca/test.json", "Heidi", "", LEGACY);
        assertBundle("/oca/test.json", "", "", LEGACY);
    }

    @Test
    void explicitUrlPinsTheFormat() throws Exception {
        assertBundle("/oca/test.json?format=swiyu", "", "?format=swiyu", SWIYU);
        assertBundle("/oca/test.json?format=legacy", "swiyuWallet", "?format=legacy", LEGACY);
    }

    private void assertBundle(String path, String agent, String query, String body) throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var properties = new IssuerProperties();
        properties.setPlatformInternalBaseUrl("https://platform.example");
        var controller = new OcaController(new OcaBundleService(builder, properties));
        var request = server.expect(requestTo("https://platform.example/public/v2/oca/test" + query));
        if (!agent.isEmpty()) request.andExpect(header(HttpHeaders.USER_AGENT, agent));
        request.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(get(path).header(HttpHeaders.USER_AGENT, agent))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .stringValues(HttpHeaders.VARY, org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString(HttpHeaders.USER_AGENT))))
                .andExpect(content().string(body));
        server.verify();
    }

    private static final String LEGACY = "{\"capture_base\":{},\"overlays\":[]}";
    private static final String SWIYU = "{\"capture_bases\":[{}],\"overlays\":[]}";
}
