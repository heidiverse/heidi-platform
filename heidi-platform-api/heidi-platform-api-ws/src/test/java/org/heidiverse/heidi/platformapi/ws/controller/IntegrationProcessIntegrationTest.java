// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.heidiverse.heidi.coordinator.data.service.ProtocolProcessDataService;
import org.heidiverse.heidi.coordinator.model.api.IntegrationProcessInitializationResponse;
import org.heidiverse.heidi.coordinator.model.api.IntegrationProcessResponse;
import org.heidiverse.heidi.coordinator.model.oid4vci.Action;
import org.heidiverse.heidi.coordinator.model.oid4vci.CredentialOfferType;
import org.heidiverse.heidi.coordinator.model.oid4vci.PreAuthIssuanceData;
import org.heidiverse.heidi.coordinator.service.AuthenticationService;
import org.heidiverse.heidi.coordinator.service.IssuanceProcessService;
import org.heidiverse.heidi.coordinator.service.Oid4vciService;
import org.heidiverse.heidi.coordinator.service.CoordinatorEntityGateway;
import org.heidiverse.heidi.coordinator.service.feign.VerifierFeignClient;
import org.heidiverse.heidi.entity.service.WalletCatalogService;
import org.heidiverse.heidi.platformapi.ws.HeidiPlatformApiApplication;
import org.heidiverse.heidi.entity.model.tenant.WalletCatalogEntry;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootTest(
        classes = {
            HeidiPlatformApiApplication.class,
            IntegrationProcessIntegrationTest.TestSecurityConfiguration.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "management.server.port=0",
            "spring.main.allow-bean-definition-overriding=true"
        })
@ActiveProfiles("test")
@TestPropertySource("classpath:application-test.properties")
class IntegrationProcessIntegrationTest {

    @SuppressWarnings("resource")
    @ServiceConnection
    static PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:latest"));

    static {
        postgresContainer.start();
    }

    private static final String API_KEY = "integration-test-key";
    private static final String TENANT_ID = "local";
    private static final String CREDENTIAL_IDENTIFIER = "test-credential";
    private static final String CONNECTION_ID = "VCI:test-connection";

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private AuthenticationService authenticationService;
    @MockitoBean private IssuanceProcessService issuanceProcessService;
    @MockitoBean private CoordinatorEntityGateway entityGateway;
    @MockitoBean private VerifierFeignClient verifierFeignClient;
    @MockitoBean private Oid4vciService oid4vciService;
    @MockitoBean private ProtocolProcessDataService processDataService;
    @MockitoBean private WalletCatalogService walletCatalogService;

    private MockMvc mockMvc;
    private AtomicBoolean walletCompleted;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        walletCompleted = new AtomicBoolean();
        jdbcTemplate.update(
                "INSERT INTO t_tenant (pk_tenant_id, display_name) VALUES (?, ?) "
                        + "ON CONFLICT (pk_tenant_id) DO NOTHING",
                TENANT_ID,
                "Integration test");

        when(authenticationService.resolveProcessTenantId(any())).thenReturn(TENANT_ID);
        when(issuanceProcessService.normalize(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(oid4vciService.fetchCredentialOfferWithToken(anyString(), eq("local")))
                .thenReturn(
                        objectMapper.readTree(
                                "{\"credential_issuer\":\"https://issuer.example/local\","
                                        + "\"credential_offer_uri\":\"https://issuer.example/offer\","
                                        + "\"connection_id\":\""
                                        + CONNECTION_ID
                                        + "\"}"));
        when(oid4vciService.extractConnectionId(any())).thenReturn("test-connection");
        when(oid4vciService.getConnectionStatus("test-connection"))
                .thenAnswer(
                        invocation ->
                                objectMapper.readTree(
                                        walletCompleted.get()
                                                ? "{\"accessTokenIssued\":true}"
                                                : "{\"accessTokenIssued\":false}"));
    }

    @Test
    void initializesStartsCompletesThroughWalletAndReturnsResult() throws Exception {
        var initialization =
                mockMvc
                        .perform(
                                post("/integration/v1/processes")
                                        .header("Authorization", "ApiKey " + API_KEY)
                                        .contentType("application/json")
                                        .content(initializationRequest()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.processId").exists())
                        .andExpect(jsonPath("$.processToken").isString())
                        .andReturn();
        var initialized =
                objectMapper.readValue(
                        initialization.getResponse().getContentAsString(),
                        IntegrationProcessInitializationResponse.class);

        var started =
                mockMvc
                        .perform(
                                post("/integration/v1/processes/{processId}/start", initialized.processId())
                                        .header("Authorization", "ApiKey " + API_KEY)
                                        .contentType("application/json")
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of("processToken", initialized.processToken()))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.processId").value(initialized.processId().toString()))
                        .andExpect(jsonPath("$.clientInteractionToken").value(startsWith("cit_")))
                        .andReturn();
        var startedProcess =
                objectMapper.readValue(
                        started.getResponse().getContentAsString(), IntegrationProcessResponse.class);

        mockMvc
                .perform(
                        get("/interaction/v1/processes/current")
                                .header(
                                        "Authorization",
                                        "Bearer " + startedProcess.clientInteractionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value(Action.PRE_AUTH_ISSUANCE))
                .andExpect(jsonPath("$.state").value("NOT_STARTED"))
                .andExpect(jsonPath("$.crossDevice.qrCodeDataScheme").value("openid-credential-offer://"))
                .andExpect(jsonPath("$.crossDevice.qrCodeDataPath").value(startsWith("?credential_offer=")))
                .andExpect(jsonPath("$.clientConfiguration.wallet.defaultWallet").value("heidi"));

        walletCompleted.set(true);

        mockMvc
                .perform(
                        get("/interaction/v1/processes/current")
                                .header(
                                        "Authorization",
                                        "Bearer " + startedProcess.clientInteractionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("STARTED"));

        mockMvc
                .perform(
                        get("/integration/v1/processes/{processId}/result", initialized.processId())
                                .header("Authorization", "ApiKey " + API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processId").value(initialized.processId().toString()))
                .andExpect(jsonPath("$.connectionState.connectionId").value(CONNECTION_ID))
                .andExpect(jsonPath("$.connectionState.state").value("STARTED"))
                .andExpect(jsonPath("$.connectionState.terminal").value(false));
    }

    @Test
    void interactionRequiresCapability() throws Exception {
        mockMvc
                .perform(get("/interaction/v1/processes/current"))
                .andExpect(status().isUnauthorized());

    }

    @Test
    void interactionCapabilityCannotInitializeProcess() throws Exception {
        mockMvc
                .perform(
                        post("/integration/v1/processes")
                                .header("Authorization", "Bearer cit_browser-capability")
                                .contentType("application/json")
                                .content(initializationRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cockpitProcessResolvesIssuerFromCredentialSchema() throws Exception {
        when(issuanceProcessService.normalize(any(), any()))
                .thenAnswer(
                        invocation -> {
                            PreAuthIssuanceData data = invocation.getArgument(0);
                            return new PreAuthIssuanceData(
                                    data.schemaIdentifier(),
                                    data.values(),
                                    data.attributeUrl(),
                                    "assigned-issuer",
                                    data.includeTxCode(),
                                    data.credentialOfferType(),
                                    data.issuanceProfileId());
                        });
        when(oid4vciService.fetchCredentialOfferWithToken(anyString(), eq("assigned-issuer")))
                .thenReturn(
                        objectMapper.readTree(
                                "{\"credential_issuer\":\"https://issuer.example/assigned-issuer\","
                                        + "\"connection_id\":\""
                                        + CONNECTION_ID
                                        + "\"}"));

        mockMvc
                .perform(
                        post("/management/v1/testing/processes")
                                .header("Authorization", "Bearer cockpit-session")
                                .contentType("application/json")
                                .content(cockpitInitializationRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientInteractionToken").isString());

        verify(issuanceProcessService).normalize(any(), any());
        verify(oid4vciService)
                .fetchCredentialOfferWithToken(anyString(), eq("assigned-issuer"));
    }

    @Test
    void usesCredentialOfferUriWhenRequestedByTheSchema() throws Exception {
        when(issuanceProcessService.normalize(any(), any()))
                .thenAnswer(
                        invocation -> {
                            PreAuthIssuanceData data = invocation.getArgument(0);
                            return new PreAuthIssuanceData(
                                    data.schemaIdentifier(),
                                    data.values(),
                                    data.attributeUrl(),
                                    data.issuerSlug(),
                                    data.includeTxCode(),
                                    CredentialOfferType.URI,
                                    data.issuanceProfileId());
                        });
        when(oid4vciService.fetchCredentialOfferWithToken(anyString(), eq("local")))
                .thenReturn(
                        objectMapper.readTree(
                                "{\"credential_issuer\":\"https://issuer.example/local\","
                                        + "\"credential_offer_uri\":\"https://issuer.example/offer\","
                                        + "\"connection_id\":\""
                                        + CONNECTION_ID
                                        + "\"}"));

        var initialization =
                mockMvc
                        .perform(
                                post("/integration/v1/processes")
                                        .header("Authorization", "ApiKey " + API_KEY)
                                        .contentType("application/json")
                                        .content(initializationRequest()))
                        .andExpect(status().isOk())
                        .andReturn();
        var initialized =
                objectMapper.readValue(
                        initialization.getResponse().getContentAsString(),
                        IntegrationProcessInitializationResponse.class);

        var started =
                mockMvc
                        .perform(
                                post("/integration/v1/processes/{processId}/start", initialized.processId())
                                        .header("Authorization", "ApiKey " + API_KEY)
                                        .contentType("application/json")
                                        .content(objectMapper.writeValueAsString(
                                                Map.of("processToken", initialized.processToken()))))
                        .andExpect(status().isOk())
                        .andReturn();
        var startedProcess =
                objectMapper.readValue(
                        started.getResponse().getContentAsString(), IntegrationProcessResponse.class);

        mockMvc
                .perform(
                        get("/interaction/v1/processes/current")
                                .header("Authorization", "Bearer " + startedProcess.clientInteractionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crossDevice.qrCodeDataPath")
                        .value(startsWith("?credential_offer_uri=")));
    }

    @Test
    void usesOrganisationDefaultWhenProcessDoesNotOverrideIt() throws Exception {
        when(authenticationService.getTenantClientConfiguration(TENANT_ID))
                .thenReturn(
                        objectMapper.readTree(
                                "{\"wallet\":{\"defaultWallet\":\"organisation-wallet\"}}"));

        var initialization =
                mockMvc
                        .perform(
                                post("/integration/v1/processes")
                                        .header("Authorization", "ApiKey " + API_KEY)
                                        .contentType("application/json")
                                        .content(initializationRequest(false)))
                        .andExpect(status().isOk())
                        .andReturn();
        var initialized =
                objectMapper.readValue(
                        initialization.getResponse().getContentAsString(),
                        IntegrationProcessInitializationResponse.class);

        var started =
                mockMvc
                        .perform(
                                post(
                                                "/integration/v1/processes/{processId}/start",
                                                initialized.processId())
                                        .header("Authorization", "ApiKey " + API_KEY)
                                        .contentType("application/json")
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of(
                                                                "processToken",
                                                                initialized.processToken()))))
                        .andExpect(status().isOk())
                        .andReturn();
        var startedProcess =
                objectMapper.readValue(
                        started.getResponse().getContentAsString(), IntegrationProcessResponse.class);

        mockMvc
                .perform(
                        get("/interaction/v1/processes/current")
                                .header(
                                        "Authorization",
                                        "Bearer " + startedProcess.clientInteractionToken()))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.clientConfiguration.wallet.defaultWallet")
                                .value("organisation-wallet"));
    }

    @Test
    void usesPlatformWalletCatalogWhenOrganisationHasNoOverride() throws Exception {
        when(authenticationService.getTenantClientConfiguration(TENANT_ID)).thenReturn(null);
        when(walletCatalogService.getDefaultClientConfiguration())
                .thenReturn(
                        objectMapper.readTree(
                                "{\"wallet\":{\"supportedWallets\":[{\"name\":\"heidi\"}]}}"));

        var initialization =
                mockMvc
                        .perform(
                                post("/integration/v1/processes")
                                        .header("Authorization", "ApiKey " + API_KEY)
                                        .contentType("application/json")
                                        .content(initializationRequest(false)))
                        .andExpect(status().isOk())
                        .andReturn();
        var initialized =
                objectMapper.readValue(
                        initialization.getResponse().getContentAsString(),
                        IntegrationProcessInitializationResponse.class);

        var started =
                mockMvc
                        .perform(
                                post("/integration/v1/processes/{processId}/start", initialized.processId())
                                        .header("Authorization", "ApiKey " + API_KEY)
                                        .contentType("application/json")
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of("processToken", initialized.processToken()))))
                        .andExpect(status().isOk())
                        .andReturn();
        var startedProcess =
                objectMapper.readValue(
                        started.getResponse().getContentAsString(), IntegrationProcessResponse.class);

        mockMvc
                .perform(
                        get("/interaction/v1/processes/current")
                                .header(
                                        "Authorization",
                                        "Bearer " + startedProcess.clientInteractionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientConfiguration.wallet.supportedWallets[0].name").value("heidi"));
    }

    @Test
    void exposesWalletCatalogToTheCockpit() throws Exception {
        when(walletCatalogService.getWallets())
                .thenReturn(
                        java.util.List.of(
                                new WalletCatalogEntry(
                                        "heidi", "Heidi", "https://example.org/icon.png", "https://example.org/open")));

        mockMvc
                .perform(get("/management/v1/tenants/wallet-catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("heidi"))
                .andExpect(jsonPath("$[0].universalLink").value("https://example.org/open"));
    }

    private String initializationRequest() throws Exception {
        return initializationRequest(true);
    }

    private String initializationRequest(boolean includeClientConfiguration) throws Exception {
        var preAuthData = new java.util.LinkedHashMap<String, Object>();
        preAuthData.put(
                "schemaIdentifier",
                Map.of("credentialIdentifier", CREDENTIAL_IDENTIFIER, "version", "1.0"));
        preAuthData.put("values", Map.of("given_name", "Ada"));
        preAuthData.put("issuerSlug", "local");
        preAuthData.put("includeTxCode", false);

        var request = new java.util.LinkedHashMap<String, Object>();
        request.put("action", Action.PRE_AUTH_ISSUANCE);
        request.put("preAuthIssuanceData", preAuthData);
        if (includeClientConfiguration) {
            request.put(
                    "clientConfiguration", Map.of("wallet", Map.of("defaultWallet", "heidi")));
        }
        return objectMapper.writeValueAsString(
                request);
    }

    private String cockpitInitializationRequest() throws Exception {
        return objectMapper.writeValueAsString(
                Map.of(
                        "action",
                        Action.PRE_AUTH_ISSUANCE,
                        "preAuthIssuanceData",
                        Map.of(
                                "schemaIdentifier",
                                Map.of(
                                        "credentialIdentifier", CREDENTIAL_IDENTIFIER,
                                        "version", "1.0"),
                                "values", Map.of("given_name", "Ada"))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSecurityConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                if (token.startsWith("cit_")) {
                    throw new JwtException("Client interaction tokens are opaque capabilities");
                }
                return Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject("integration-test")
                        .build();
            };
        }
    }
}
