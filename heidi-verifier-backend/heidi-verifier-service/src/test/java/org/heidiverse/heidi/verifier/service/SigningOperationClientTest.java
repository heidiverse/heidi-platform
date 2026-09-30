// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

class SigningOperationClientTest {
    private static final String OPERATION = "w3c.bbs-data-integrity-presentation-setup";

    @Test
    void executesAKeylessProviderOperation() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> {
            assertTrue(request.getURI().getPath().equals(
                    "/internal/platform/v1/proof-schemas/proof-1/operations/" + OPERATION));
            assertTrue("Basic c2VydmljZTpwYXNz"
                    .equals(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)));
            assertTrue(MediaType.APPLICATION_JSON.equals(request.getHeaders().getContentType()));
            var body = ((org.springframework.mock.http.client.MockClientHttpRequest) request)
                    .getBodyAsString(StandardCharsets.UTF_8);
            assertTrue(body.contains("\"requirements\":[{\"type\":\"required\",\"key\":\"name\"}]"));
        }).andRespond(MockRestResponseCreators.withSuccess(
                "{\"status\":\"COMPLETED\","
                        + "\"resultJson\":\"{\\\"provingKeys\\\":{},"
                        + "\\\"verifyingKeys\\\":{}}\"}",
                MediaType.APPLICATION_JSON));

        var client = new SigningOperationClient(
                builder,
                new ObjectMapper(),
                "http://platform.example",
                "c2VydmljZTpwYXNz");

        var result = client.execute("proof-1", OPERATION,
                "{\"requirements\":[{\"type\":\"required\",\"key\":\"name\"}]}");

        assertTrue(result.path("provingKeys").isObject());
        assertTrue(result.path("verifyingKeys").isObject());
        server.verify();
    }

    @Test
    void fetchesIssuerMetadataForTheSelectedBbsQueries() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertEquals(
                        "http://platform.example/internal/platform/v1/proof-schemas/proof-1/bbs-issuer-metadata"
                                + "?credentialQueryId=employee_bbs-termwise"
                                + "&credentialQueryId=address_bbs-termwise",
                        request.getURI().toString()))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"issuerPk\":\"public-key\","
                                + "\"issuerId\":\"did:example:issuer\","
                                + "\"issuerKeyId\":\"did:example:issuer#bbs-1\"}",
                        MediaType.APPLICATION_JSON));
        var client = new SigningOperationClient(
                builder, new ObjectMapper(), "http://platform.example", "");

        var result = client.bbsIssuerMetadata(
                "proof-1", List.of("employee_bbs-termwise", "address_bbs-termwise"));

        assertEquals("public-key", result.issuerPk());
        assertEquals("did:example:issuer", result.issuerId());
        assertEquals("did:example:issuer#bbs-1", result.issuerKeyId());
        server.verify();
    }
}
