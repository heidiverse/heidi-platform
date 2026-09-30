// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.ws.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.heidiverse.heidi.verifier.model.api.verifier.CredentialsRequest;
import org.heidiverse.heidi.verifier.model.api.verifier.CredentialsRequestResponse;
import org.heidiverse.heidi.shared.testing.TestCertificates;
import org.heidiverse.heidi.verifier.service.IdentitySigningService;
import org.heidiverse.heidi.verifier.ws.BaseWsDataServiceTest;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.core.io.Resource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.net.URI;
import java.security.interfaces.ECPrivateKey;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "heidi.verifier.oid4vp.wallet.response-base-url=http://localhost"})
class VerifierFlowIntegrationTest extends BaseWsDataServiceTest {

    /**
     * Presentations are generated per run rather than pasted in as base64. See {@link
     * Oid4vpFixtures}; the audience matches the {@code heidi.platform.rp-registrar.leaf-san} the local profile
     * configures the verifier with.
     */
    private static final String AUDIENCE = "x509_san_dns:localhost";

    private static final Oid4vpFixtures fixtures;

    /**
     * Written in a static initializer rather than {@code @BeforeAll} because
     * {@link DynamicPropertySource} is evaluated while the application context is being built,
     * which happens first. Creating the fixtures there would hand the verifier a different issuer
     * key than the presentations are signed with.
     */
    private static final Path issuerJwksFile;

    static {
        try {
            fixtures = new Oid4vpFixtures();
            issuerJwksFile =
                    fixtures.writeIssuerJwks(Files.createTempDirectory("heidi-oid4vp-fixtures"));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void publishIssuerKey(final DynamicPropertyRegistry registry) {
        registry.add(
                "heidi.verifier.oid4vp.predefined-jwks",
                () -> "{ '" + Oid4vpFixtures.ISSUER + "': 'file:" + issuerJwksFile + "' }");
    }

    private static final String VERIFIER_IDENTITY = "verifier-identity";
    private static final String VERIFIER_HOST = "localhost";

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private ObjectMapper objectMapper;
    @LocalManagementPort private int managementPort;
    @MockitoBean
    private IdentitySigningService identitySigningService;

    @Value("classpath:credentials_request_dcql.json")
    private Resource credentialsRequestDcql;

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();

        // The fixtures name this identity; the platform would hand over its key and chain.
        when(identitySigningService.snapshot(eq(VERIFIER_IDENTITY), any(), any(), any()))
                .thenReturn("request-signing-configuration");
        when(identitySigningService.resolveSnapshot("request-signing-configuration"))
                .thenReturn(verifierIdentity());
    }

    /** A verifier identity whose leaf, like one the platform issues, names the verifier host. */
    private static IdentitySigningService.ResolvedSigner verifierIdentity() throws Exception {
        var ca = TestCertificates.ca("Verifier Test CA");
        var keyPair = TestCertificates.ecKeyPair();
        var leaf =
                TestCertificates.leaf(
                        ca, keyPair.getPublic(), VERIFIER_HOST, List.of(VERIFIER_HOST));
        var publicJwk =
                new ECKey.Builder(TestCertificates.publicJwk(keyPair))
                        .x509CertChain(leaf.encodedChain().stream().map(Base64::new).toList())
                        .build();
        return new IdentitySigningService.ResolvedSigner(
                publicJwk, new ECDSASigner((ECPrivateKey) keyPair.getPrivate()), null);
    }

    @Test
    void renewalPreservesIssuedRequest() throws Exception {
        var original = verifierIdentity();
        var renewed = verifierIdentity();
        when(identitySigningService.resolveSnapshot("request-signing-configuration")).thenReturn(original);
        when(identitySigningService.resolveSnapshot("renewed-configuration")).thenReturn(renewed);
        when(identitySigningService.x509CertificateHash(any(IdentitySigningService.ResolvedSigner.class)))
                .thenAnswer(call ->
                        IdentitySigningService.x509CertificateHash(
                                call.<IdentitySigningService.ResolvedSigner>getArgument(0).publicJwk()));
        var request = (ObjectNode) objectMapper.readTree(
                credentialsRequestDcql.getContentAsString(StandardCharsets.UTF_8));
        request.put("client_id_scheme", "x509_hash");
        request.put("signing_trust_system", "EUDI");
        var created = mockMvc.perform(post("/internal/verifier/v1/par")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("credentialRequest=" + URLEncoder.encode(
                                objectMapper.writeValueAsString(request), StandardCharsets.UTF_8)))
                .andExpect(status().isCreated()).andReturn();
        var response = objectMapper.readValue(created.getResponse().getContentAsString(),
                CredentialsRequestResponse.class);

        // Renewal occurs after the QR/client_id was issued but before the wallet retrieves it.
        when(identitySigningService.snapshot(eq(VERIFIER_IDENTITY), any(), any(), any()))
                .thenReturn("renewed-configuration");
        for (var flow : List.of(response.sameDeviceFlow(), response.crossDeviceFlow())) {
            var fetched = mockMvc.perform(get("/v1/wallet/par/" + flow.requestUri()))
                    .andExpect(status().isOk()).andReturn();
            var jwt = SignedJWT.parse(fetched.getResponse().getContentAsString());
            assertEquals(response.clientId(), jwt.getJWTClaimsSet().getStringClaim("client_id"));
            assertEquals(original.publicJwk().getX509CertChain(), jwt.getHeader().getX509CertChain());
            assertTrue(jwt.verify(new com.nimbusds.jose.crypto.ECDSAVerifier(
                    original.publicJwk().toECKey())));
        }
    }

    @Test
    void testSetup() throws Exception {
        assertNotNull(mockMvc);

        // Public liveness endpoint is available on the application port.
        this.mockMvc
                .perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        this.mockMvc
                .perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        // Actuator runs in a separate management context for Kubernetes monitoring.
        final var httpClient = HttpClient.newHttpClient();
        assertHealth(httpClient, "/actuator/health");
        assertHealth(httpClient, "/actuator/health/liveness");
        assertHealth(httpClient, "/actuator/health/readiness");
    }

    private void assertHealth(HttpClient httpClient, String path) throws Exception {
        final var response =
                httpClient.send(
                        HttpRequest.newBuilder(
                                        URI.create("http://localhost:" + managementPort + path))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
    }

    @Test
    void testTransactionDataHashMismatch() throws Exception {
        // Read into object
        final var credentialsRequestJson =
                objectMapper.readTree(
                        credentialsRequestDcql.getContentAsString(StandardCharsets.UTF_8));
        ((ObjectNode) credentialsRequestJson)
                .set(
                        "transaction_data",
                        objectMapper.valueToTree(List.of(Oid4vpFixtures.TRANSACTION_DATA)));
        final var credentialsRequest =
                objectMapper.convertValue(credentialsRequestJson, CredentialsRequest.class);

        // URL escape credential request
        final var definition =
                URLEncoder.encode(
                        objectMapper.writeValueAsString(credentialsRequest),
                        StandardCharsets.UTF_8);

        // Verifier submits verification request
        final MvcResult credentialRequest =
                this.mockMvc
                        .perform(
                                post("/internal/verifier/v1/par")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .accept(MediaType.APPLICATION_JSON)
                                        .content("credentialRequest=" + definition))
                        .andExpect(status().isCreated())
                        .andReturn();

        // Parse response
        final var credentialsRequestResponse =
                objectMapper.readValue(
                        credentialRequest.getResponse().getContentAsString(),
                        CredentialsRequestResponse.class);
        final var requestId = credentialsRequestResponse.sameDeviceFlow().requestUri();
        final var transactionId = credentialsRequestResponse.sameDeviceFlow().transactionId();

        // Verifier periodically polls for verification response (404)
        this.mockMvc
                .perform(
                        get("/internal/verifier/v1/authorization")
                                .param("transactionId", transactionId)
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // Wallet fetches verification request
        final var wrappedCredentialRequest =
                this.mockMvc
                        .perform(get("/v1/wallet/par/" + requestId))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        final var jwt = JWTParser.parse(wrappedCredentialRequest);
        assertInstanceOf(SignedJWT.class, jwt);
        assertFalse(((SignedJWT) jwt).getHeader().getX509CertChain().isEmpty());

        // we omit any further verification steps here for brevity ...

        // Wallet submits response
        final String body =
                String.format(
                        "%s=%s&state=%s",
                        URLEncoder.encode("vp_token", StandardCharsets.UTF_8),
                        fixtures.vpTokenWithWrongTransactionData(
                                "full_credential_for_sd-jwt", AUDIENCE),
                        requestId);

        // A presentation the verifier cannot verify is a client error, not a
        // server one: WalletExceptionHandling maps VpVerificationException onto an
        // OAuth invalid_request response, and only genuinely unexpected failures
        // reach the handler that answers 5xx.
        this.mockMvc
                .perform(
                        post("/v1/wallet/authorization")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

        // Verifier periodically polls for verification response (404)
        this.mockMvc
                .perform(
                        get("/internal/verifier/v1/authorization")
                                .param("transactionId", transactionId)
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

}
