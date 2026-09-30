// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.heidiverse.heidi.coordinator.model.ProofSchemeResponse;
import org.heidiverse.heidi.coordinator.model.TenantResponse;
import org.heidiverse.heidi.coordinator.model.oid4vci.PresentationData;
import org.heidiverse.heidi.coordinator.model.oidc4vp.VerifierParResponse;
import org.heidiverse.heidi.coordinator.service.feign.VerifierFeignClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.kapunsdk.DcqlQuerySerializer;
import org.kapunsdk.presentation.request.model.OID4VPVersion;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

class Oid4vpServiceTest {

    @Test
    void profileResponseModeCannotBeOverriddenByDcApi() {
        assertEquals(
                "direct_post.jwt",
                Oid4vpService.responseModeFor("EUDI_PRESENTATION_2026_1", false));

        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> Oid4vpService.responseModeFor("EUDI_PRESENTATION_2026_1", true));

        assertTrue(exception.getMessage().contains("DC API"));
    }

    @Test
    void requestRequiresProfileBeforeSelectingResponseMode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Oid4vpService.responseModeFor(null, true));
    }

    @Test
    void readsScopeFromSwissVerificationQueryStatement() {
        var payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"request\":{\"type\":\"DCQL\",\"scope\":\"ch.example.identity\"}}"
                        .getBytes(StandardCharsets.UTF_8));
        var statement = "eyJhbGciOiJFUzI1NiJ9." + payload + ".signature";

        assertEquals("ch.example.identity", Oid4vpService.swissScope(statement, new ObjectMapper()));
    }

    @Test
    void customProfileIgnoresSwiss() throws Exception {
        var verifierClient = mock(VerifierFeignClient.class);
        var entityGateway = mock(CoordinatorEntityGateway.class);
        var statement = swissStatement("ch.example.identity");
        var proofScheme = proofScheme("CUSTOM_PRESENTATION_2026_1", statement);
        var flow = new VerifierParResponse.RequestParams("transaction", "request", 0);

        when(entityGateway.getProofScheme("proof")).thenReturn(proofScheme);
        when(entityGateway.getTenantInformation("tenant"))
                .thenReturn(
                        new TenantResponse(
                                "tenant", null, null, null, null,
                                List.of(TenantResponse.TrustRegistryType.CH), null));
        when(verifierClient.sendParRequest(any())).thenReturn(
                new VerifierParResponse("client", flow, flow));

        var service = new Oid4vpService(
                verifierClient,
                entityGateway,
                new ObjectMapper(),
                new DcqlQueryService("https://platform.example"));

        service.getAuthorizationRequestObject(
                new PresentationData(
                        "proof", List.of(), false, OID4VPVersion.DRAFT_28,
                        "CUSTOM_PRESENTATION_2026_1"));

        var requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(verifierClient).sendParRequest(requestCaptor.capture());
        var request = new ObjectMapper()
                .readTree((String) requestCaptor.getValue().get("credentialRequest"));

        assertNull(request.get("scope"));
        assertNull(request.get("verifier_info"));
        assertNull(request.get("trustframework"));
        assertEquals("CH", request.get("trust_configuration").get("trustframework").asText());
    }

    @Test
    void rejectsEmptySwissPayload() throws Exception {
        var statement = "header."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[0])
                + ".signature";
        var objectMapper = mock(ObjectMapper.class);
        when(objectMapper.readTree("")).thenReturn(null);

        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> Oid4vpService.swissScope(statement, objectMapper));

        assertTrue(exception.getMessage().contains("valid request scope"));
    }

    @Test
    void addsProofSchemeTrustedAuthoritiesToCredentialQueries() throws Exception {
        var credential =
                new ProofSchemeResponse.CredentialScheme(
                        "credential-id",
                        "pid",
                        "1.0",
                        "PID",
                        null,
                        List.of(),
                        new ProofSchemeResponse.IssuerSettings(
                                1, "JWK", null, null, "https://example.com/pid", Set.of("SD_JWT")));
        var proofScheme =
                new ProofSchemeResponse(
                        "proof-id",
                        "Proof",
                        "Purpose",
                        null,
                        "DISABLED",
                        Instant.now(),
                        Instant.now(),
                        null,
                        "company",
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        false,
                        List.of(credential),
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        List.of(
                                new ProofSchemeResponse.TrustedAuthorityQuery(
                                        "aki", List.of("s9tIpPmhxdiuNkHMEWNpYim8S8Y"), null),
                                new ProofSchemeResponse.TrustedAuthorityQuery(
                                        "openid_federation",
                                        List.of("https://trustanchor.example.com"),
                                        "different_credential"),
                                new ProofSchemeResponse.TrustedAuthorityQuery(
                                        "did",
                                        List.of("did:tdw:QmExample:identifier.example.ch"),
                                        "pid_dc__sd-jwt")),
                        "EUDI_PRESENTATION_2026_1");
        var service =
                new Oid4vpService(
                        null,
                        null,
                        new ObjectMapper(),
                        new DcqlQueryService("https://platform.example"));

        var json =
                DcqlQuerySerializer.toJson(
                        service.getDcqlQuery(proofScheme, OID4VPVersion.DRAFT_28));

        assertTrue(json.contains("\"trusted_authorities\""));
        assertTrue(json.contains("\"type\":\"aki\""));
        assertTrue(json.contains("s9tIpPmhxdiuNkHMEWNpYim8S8Y"));
        assertFalse(json.contains("\"type\":\"openid_federation\""));
        assertTrue(json.contains("\"type\":\"did\""));
        assertTrue(json.contains("did:tdw:QmExample:identifier.example.ch"));
    }

    private static String swissStatement(String scope) {
        var payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"request\":{\"type\":\"DCQL\",\"scope\":\""
                        + scope + "\"}}").getBytes(StandardCharsets.UTF_8));
        return "eyJhbGciOiJFUzI1NiJ9." + payload + ".signature";
    }

    private static ProofSchemeResponse proofScheme(String profile, String statement) {
        var credential = new ProofSchemeResponse.CredentialScheme(
                "credential-id",
                "pid",
                "1.0",
                "PID",
                null,
                List.of(),
                new ProofSchemeResponse.IssuerSettings(
                        1, "JWK", null, null, "https://example.com/pid", Set.of("SD_JWT")));
        return new ProofSchemeResponse(
                "proof",
                "Proof",
                "Purpose",
                null,
                "DISABLED",
                Instant.now(),
                Instant.now(),
                "https://example.com/redirect",
                "tenant",
                null,
                false,
                null,
                null,
                null,
                null,
                false,
                List.of(credential),
                null,
                null,
                statement,
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                profile);
    }
}
