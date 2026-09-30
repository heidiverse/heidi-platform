// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.ws;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "heidi.signing.auth.mode=none",
            "heidi.signing.auth.fail-open=true",
            "heidi.signing.auth.timestamp-window=60s",
            // This context has no datasource, and HEIDI_SIGNING_SOFTWARE_DATABASE_ENABLED is set
            // in .env.local, which just(1) exports into the test JVM.
            "heidi.signing.software.database.enabled=false"
        })
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SigningServiceApplicationTest {
    private static final String BBS_OPERATION =
            "w3c.bbs-data-integrity-credential-issuance";

    @Autowired private MockMvc client;

    @Test
    void servesCapabilitiesAndCreatesAKeyOverHttp() throws Exception {
        client.perform(get("/v1/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supportedAlgorithms").isArray())
                .andExpect(jsonPath("$.supportedOperations").isArray())
                .andExpect(jsonPath("$.keylessOperations").isArray())
                .andExpect(jsonPath("$.supportedAlgorithms").value(org.hamcrest.Matchers.hasItem("ML-DSA-65")));

        client.perform(post("/v1/keys")
                        .contentType(APPLICATION_JSON)
                        .content("{\"keyId\":\"http-smoke\",\"algorithm\":\"EdDSA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uri").value("software://http-smoke"));
    }

    @Test
    void rejectsAnUnsupportedProtocolOperation() throws Exception {
        client.perform(post("/v1/operations")
                        .contentType(APPLICATION_JSON)
                        .content("{\"operation\":\""
                                + BBS_OPERATION
                                + "\",\"keyUri\":\"software://missing\",\"input\":{}}"))
                .andExpect(status().isNotImplemented())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "Provider cannot execute operation: "
                                + BBS_OPERATION));
    }

    @Test
    void createsAKeyInTheRequestedLogicalKeyNamespace() throws Exception {
        var logicalKeyId = "11111111-1111-4111-8111-111111111111";
        var versionId = "22222222-2222-4222-8222-222222222222";
        client.perform(post("/v1/keys")
                        .contentType(APPLICATION_JSON)
                        .content("{\"keyId\":\"" + versionId
                                + "\",\"namespace\":\"kc/" + logicalKeyId
                                + "\",\"algorithm\":\"ES256\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uri")
                        .value("software://kc/" + logicalKeyId + "/" + versionId));
    }

    @Test
    void returnsProblemDetailsForProtocolErrors() throws Exception {
        client.perform(post("/v1/keys")
                        .contentType(APPLICATION_JSON)
                        .content("{\"keyId\":\"http-smoke\",\"algorithm\":\"\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://heidi.heidiverse.org/problems/signing"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("algorithm must not be blank"));
    }

    @Test
    void logsTheProviderCauseWhenKeyCreationFails(CapturedOutput output) throws Exception {
        var request = post("/v1/keys")
                .contentType(APPLICATION_JSON)
                .content("{\"keyId\":\"http-duplicate\",\"algorithm\":\"ES256\"}");

        client.perform(request).andExpect(status().isCreated());
        client.perform(request)
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Signing provider refused the operation"));

        org.junit.jupiter.api.Assertions.assertTrue(
                output.getAll().contains("Signing provider operation failed client=null: POST /v1/keys"));
        org.junit.jupiter.api.Assertions.assertTrue(
                output.getAll().contains(
                        "A software signing key already exists: software://http-duplicate"));
    }
}
