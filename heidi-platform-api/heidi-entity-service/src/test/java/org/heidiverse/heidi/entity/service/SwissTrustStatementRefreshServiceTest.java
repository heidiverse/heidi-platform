// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustConfigurationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

class SwissTrustStatementRefreshServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestsAndReturnsThePublishedVerificationQueryStatement() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://auth.example/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(
                        "grant_type=refresh_token&refresh_token=refresh-token"
                                + "&client_id=client-id&client_secret=client-secret"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"access-token\",\"refresh_token\":\"rotated-refresh-token\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "https://authoring.example/api/v1/trust/vqps-submissions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andExpect(content().json("""
                        {
                          "waitForPublication": true,
                          "sub": "did:example:verifier",
                          "purpose_name": {"default": "Verify identity"},
                          "purpose_description": {"default": "Request identity claims"},
                          "scope": "heidi.proof.example",
                          "query": {"credentials": []}
                        }
                        """))
                .andRespond(withSuccess(
                        "{\"publicationResult\":{\"jwt\":\"statement-jwt\"}}",
                        MediaType.APPLICATION_JSON));

        var identity = new IssuerDefinitionEntity();
        identity.setId(42);
        var settings = new IssuerTrustConfigurationRequest(
                "did:example:verifier", "did:example:verifier", "https://read.example",
                "https://authoring.example", "https://auth.example/token", "client-id",
                "client-secret", "refresh-token", null, null, null, null);
        var service = new SwissTrustStatementRefreshService(null, null, builder);

        var statement = service.requestVerificationQueryStatement(
                identity, settings,
                "Verify identity",
                "Request identity claims",
                "heidi.proof.example",
                objectMapper.readTree("{\"credentials\":[]}"));

        assertEquals(java.util.Optional.of("statement-jwt"), statement);
        server.verify();
    }

    @Test
    void doesNotRequestAStatementWhenNoRefreshCredentialsAreConfigured() {
        var service = new SwissTrustStatementRefreshService(null, null, RestClient.builder());
        var settings = new IssuerTrustConfigurationRequest(
                "did:example:verifier", "did:example:verifier", "https://read.example",
                "https://authoring.example", "https://auth.example/token", null, null, null,
                null, null, null, null);

        assertTrue(service.requestVerificationQueryStatement(
                null, settings, "Title", "Purpose", "heidi.proof.example",
                objectMapper.createObjectNode()).isEmpty());
    }

    @Test
    void listsPublishedVerificationQueryStatements() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://auth.example/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"access-token\"}",
                        MediaType.APPLICATION_JSON));
        var payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"purpose\":{\"name\":{\"default\":\"Verify identity\"}},"
                        + "\"request\":{\"scope\":\"heidi.proof.example\","
                        + "\"query\":{\"credentials\":[]}}}")
                        .getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo(
                        "https://authoring.example/api/v1/trust/vqps-submissions?page=0&size=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"content\":[{\"id\":\"vqps-1\",\"version\":2,"
                                + "\"status\":\"PUBLISHED\",\"publicationResult\":{"
                                + "\"jwt\":\"header." + payload + ".signature\","
                                + "\"expiresAt\":\"2026-12-31T00:00:00Z\"},"
                                + "\"createdAt\":\"2026-01-01T00:00:00Z\","
                                + "\"updatedAt\":\"2026-01-02T00:00:00Z\"}],"
                                + "\"last\":true}",
                        MediaType.APPLICATION_JSON));

        var identity = new IssuerDefinitionEntity();
        var settings = new IssuerTrustConfigurationRequest(
                "did:example:verifier", "did:example:verifier", "https://read.example",
                "https://authoring.example", "https://auth.example/token", "client-id",
                "client-secret", "refresh-token", null, null, null, null);

        var queries = new SwissTrustStatementRefreshService(null, null, builder)
                .listVerificationQueryStatements(identity, settings);

        assertEquals(1, queries.size());
        assertEquals("vqps-1", queries.getFirst().id());
        assertEquals("Verify identity", queries.getFirst().purposeName());
        assertEquals("heidi.proof.example", queries.getFirst().scope());
        assertEquals("PUBLISHED", queries.getFirst().status());
        server.verify();
    }

    @Test
    void recognizesAnUnchangedVerificationQuery() {
        var service = new SwissTrustStatementRefreshService(null, null, RestClient.builder());
        var query = query();

        assertTrue(service.matchesVerificationQuery(
                statement(Instant.now().plus(1, ChronoUnit.HOURS)),
                "heidi.proof.example", query));
        assertFalse(service.matchesVerificationQuery(
                statement(Instant.now().plus(1, ChronoUnit.HOURS)),
                "heidi.proof.changed", query));
    }

    @Test
    void rejectsAnExpiredVerificationQuery() {
        var service = new SwissTrustStatementRefreshService(null, null, RestClient.builder());

        assertFalse(service.matchesVerificationQuery(
                statement(Instant.now().minus(1, ChronoUnit.SECONDS)),
                "heidi.proof.example", query()));
    }

    private JsonNode query() {
        return objectMapper.createObjectNode().set(
                "credentials", objectMapper.createArrayNode());
    }

    private String statement(Instant expiration) {
        var payload = objectMapper.createObjectNode();
        payload.put("exp", expiration.getEpochSecond());
        var request = payload.putObject("request");
        request.put("scope", "heidi.proof.example");
        request.set("query", query());
        var encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
                payload.toString().getBytes(StandardCharsets.UTF_8));
        return "header." + encoded + ".signature";
    }
}
