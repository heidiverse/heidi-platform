// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;


/**
 * The verifier signs request objects with its presentation-signing slot.
 */
class IdentitySigningConfigurationTest {
    private static final String PLATFORM = "http://platform.example";
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer platform = MockRestServiceServer.bindTo(builder).build();

    @Test
    void asksForThePresentationSigningKey() throws Exception {
        platform.expect(requestTo(PLATFORM
                        + "/internal/platform/v1/identities/member/presentation-signing-configuration"
                        + "?trustSystem=Default"))
                .andRespond(withSuccess(envelope(), MediaType.APPLICATION_JSON));
        platform.expect(requestTo("http://provider.example/v1/capabilities"))
                .andRespond(withSuccess(
                        "{\"scheme\":\"software\",\"supportedAlgorithms\":[\"ES256\"],"
                                + "\"digestSigningAlgorithms\":[],\"canCreate\":false,"
                                + "\"canImport\":false,\"canDelete\":false}",
                        MediaType.APPLICATION_JSON));
        platform.expect(requestTo("http://provider.example/v1/keys?uri=software://kc/9f1c/version-1"))
                .andRespond(withSuccess(
                        "{\"uri\":\"software://kc/9f1c/version-1\",\"publicKeyDocument\":"
                                + "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"x\",\"y\":\"y\"},"
                                + "\"algorithm\":\"ES256\"}",
                        MediaType.APPLICATION_JSON));

        var resolved = service().resolve("member", "Default");

        assertEquals("ES256", resolved.publicJwk().getAlgorithm().getName());
        platform.verify();
    }

    @Test
    void retainsExactSnapshotVersion() throws Exception {
        var flowId = java.util.UUID.randomUUID().toString();
        var expiry = java.time.Instant.now().plusSeconds(300);
        platform.expect(requestTo(PLATFORM + "/internal/platform/v1/identities/member/signing-flows"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(
                        org.springframework.http.HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.flowId").value(flowId))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.client").value("VERIFIER"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.purpose").value("SIGNING"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.keyUri").value("software://kc/9f1c/version-1"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent());
        platform.expect(requestTo(PLATFORM + "/internal/platform/v1/identities/signing-flows/VERIFIER/" + flowId))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(
                        org.springframework.http.HttpMethod.DELETE))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent());
        var signing = service();
        signing.retainSnapshot("member", envelope(), flowId, expiry);
        signing.releaseSnapshot(flowId);
        platform.verify();
    }

    private String envelope() throws Exception {
        var signingKey = new ECKeyGenerator(Curve.P_256).algorithm(com.nimbusds.jose.JWSAlgorithm.ES256)
                .generate();
        return new ObjectMapper().writeValueAsString(java.util.Map.of(
                "algorithm", "ES256",
                "keyUri", "software://kc/9f1c/version-1",
                "issuerJwk", signingKey.toPublicJWK().toJSONString(),
                "providerEndpoint", "http://provider.example"));
    }

    private IdentitySigningService service() {
        return new IdentitySigningService(
                builder, new ObjectMapper(), PLATFORM, "", "http://provider.example", "none", "");
    }
}
