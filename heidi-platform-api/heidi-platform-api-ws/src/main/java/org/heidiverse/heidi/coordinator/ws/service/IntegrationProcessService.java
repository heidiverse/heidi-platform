// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.ws.service;

import org.heidiverse.heidi.coordinator.data.service.IntegrationProcessDataService;
import org.heidiverse.heidi.coordinator.model.api.ClientInteractionData;
import org.heidiverse.heidi.coordinator.model.api.InitializeProcessRequest;
import org.heidiverse.heidi.coordinator.model.api.IntegrationProcessInitializationResponse;
import org.heidiverse.heidi.coordinator.model.api.IntegrationProcessResponse;
import org.heidiverse.heidi.coordinator.model.api.IntegrationProcessResult;
import org.heidiverse.heidi.coordinator.model.api.StartIntegrationProcessRequest;
import org.heidiverse.heidi.coordinator.model.api.StartProcessResponse;
import org.heidiverse.heidi.coordinator.model.connection.ConnectionStateV2;
import org.heidiverse.heidi.coordinator.model.entity.IntegrationProcessEntity;
import org.heidiverse.heidi.coordinator.model.exceptions.DoctypeNotFoundException;
import org.heidiverse.heidi.coordinator.model.exceptions.VctNotFoundException;
import org.heidiverse.heidi.coordinator.model.issuance.SignatureToken;
import org.heidiverse.heidi.coordinator.model.issuance.SignatureTokenWithTxCode;
import org.heidiverse.heidi.coordinator.model.oid4vci.Action;
import org.heidiverse.heidi.coordinator.model.oid4vci.SignedData;
import org.heidiverse.heidi.coordinator.service.AuthenticationService;
import org.heidiverse.heidi.coordinator.service.ClientInteractionTokenService;
import org.heidiverse.heidi.coordinator.service.ProcessOrchestrationService;
import org.heidiverse.heidi.coordinator.service.TokenSignatureService;
import org.heidiverse.heidi.entity.service.WalletCatalogService;
import org.heidiverse.heidi.platformapi.extensions.process.ProcessSessionService;

import jakarta.transaction.Transactional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Creates, starts, and reads integration processes. HTTP controllers enforce audience-specific
 * transport rules before delegating here.
 */
@Service
public class IntegrationProcessService implements ProcessSessionService {

    private static final String API_KEY_SCHEME = "ApiKey ";
    private static final String CLIENT_TOKEN_SCHEME = "Bearer ";
    private static final Set<String> SUPPORTED_ACTIONS =
            Set.of(Action.PRE_AUTH_ISSUANCE, Action.PRESENTATION);

    private final ProcessOrchestrationService orchestrationService;
    private final IntegrationProcessDataService processDataService;
    private final ClientInteractionTokenService clientInteractionTokenService;
    private final TokenSignatureService tokenSignatureService;
    private final AuthenticationService authenticationService;
    private final WalletCatalogService walletCatalogService;
    private final ObjectMapper objectMapper;

    public IntegrationProcessService(
            ProcessOrchestrationService orchestrationService,
            IntegrationProcessDataService processDataService,
            ClientInteractionTokenService clientInteractionTokenService,
            TokenSignatureService tokenSignatureService,
            AuthenticationService authenticationService,
            WalletCatalogService walletCatalogService,
            ObjectMapper objectMapper) {
        this.orchestrationService = orchestrationService;
        this.processDataService = processDataService;
        this.clientInteractionTokenService = clientInteractionTokenService;
        this.tokenSignatureService = tokenSignatureService;
        this.authenticationService = authenticationService;
        this.walletCatalogService = walletCatalogService;
        this.objectMapper = objectMapper;
    }

    public IntegrationProcessInitializationResponse initializeProcess(
            InitializeProcessRequest request,
            String authorizationHeader,
            String apiKeyHeader)
            throws JacksonException,
                    URISyntaxException,
                    VctNotFoundException,
                    DoctypeNotFoundException {
        String integrationCredential =
                extractIntegrationCredential(authorizationHeader, apiKeyHeader);
        return initialize(request, integrationCredential);
    }

    @Override
    public IntegrationProcessResponse createProcessForSession(
            InitializeProcessRequest request, String authorizationHeader)
            throws JacksonException,
                    URISyntaxException,
                    VctNotFoundException,
                    DoctypeNotFoundException {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "No authenticated session credential present");
        }
        IntegrationProcessInitializationResponse initialized =
                initialize(request, authorizationHeader);
        return startInitializedProcess(
                initialized.processId(), initialized.processToken(), null, authorizationHeader);
    }

    @Override
    public StartProcessResponse startProcessForSession(
            InitializeProcessRequest request, String authorizationHeader)
            throws JacksonException,
                    URISyntaxException,
                    VctNotFoundException,
                    DoctypeNotFoundException {
        var initialized = orchestrationService.initialize(request, authorizationHeader);
        return orchestrationService.start(new SignatureToken(initialized.token()));
    }

    private IntegrationProcessInitializationResponse initialize(
            InitializeProcessRequest request, String coordinatorAuthorization)
            throws JacksonException,
                    URISyntaxException,
                    VctNotFoundException,
                    DoctypeNotFoundException {
        if (!SUPPORTED_ACTIONS.contains(request.action())
                && !orchestrationService.supportsIntegrationAction(request.action())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported action for integration process: " + request.action());
        }
        JsonNode requestedDisplayClaims =
                request.clientDisplayClaims() == null
                        ? objectMapper.createArrayNode()
                        : objectMapper.valueToTree(request.clientDisplayClaims());

        SignatureTokenWithTxCode initialized =
                orchestrationService.initialize(request, coordinatorAuthorization);
        SignedData signedData =
                tokenSignatureService.verifyAndGetObject(
                        new SignatureToken(initialized.token()), SignedData.class);
        ZonedDateTime expiresAt = signedData.expiresAt();
        if (expiresAt == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Coordinator process token has no expiration");
        }
        UUID processId = UUID.randomUUID();
        IntegrationProcessEntity process = new IntegrationProcessEntity();
        process.setProcessId(processId);
        process.setProcessTokenHash(clientInteractionTokenService.hash(initialized.token()));
        process.setIntegrationCredentialHash(hashIntegrationCredential(coordinatorAuthorization));
        String tenantId = orchestrationService.resolveProcessTenantId(request);
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Process action cannot be used through the integration API: "
                            + request.action());
        }
        process.setTenantId(tenantId);
        process.setAction(request.action());
        process.setUseDcApi(
                request.presentationData() != null && request.presentationData().useDcApi());
        process.setProofSchemeId(
                request.presentationData() == null
                        ? null
                        : request.presentationData().proofSchemeId());
        process.setIncludeVpToken(request.includeVpToken());
        process.setTxCode(initialized.txCode());
        process.setClientConfiguration(copyClientConfigurationObject(request.clientConfiguration()));
        process.setClientDisplayClaims(requestedDisplayClaims);
        process.setExpiresAt(expiresAt);
        processDataService.save(process);

        return new IntegrationProcessInitializationResponse(
                processId, initialized.token(), initialized.txCode(), expiresAt);
    }

    @Transactional
    public IntegrationProcessResponse startIntegrationProcess(
            UUID processId,
            StartIntegrationProcessRequest request,
            String authorizationHeader,
            String apiKeyHeader)
            throws JacksonException,
                    URISyntaxException,
                    VctNotFoundException,
                    DoctypeNotFoundException {
        String integrationCredential =
                extractIntegrationCredential(authorizationHeader, apiKeyHeader);
        return startInitializedProcess(
                processId,
                request.processToken(),
                request.clientConfiguration(),
                integrationCredential);
    }

    private IntegrationProcessResponse startInitializedProcess(
            UUID processId,
            String processToken,
            JsonNode requestedClientConfiguration,
            String integrationCredential)
            throws JacksonException,
                    URISyntaxException,
                    VctNotFoundException,
                    DoctypeNotFoundException {
        IntegrationProcessEntity process = processDataService.getByIdForUpdate(processId);
        requireIntegrationCredential(process, integrationCredential);
        requireNotExpired(process.getExpiresAt());
        requireProcessToken(process, processToken);

        String clientInteractionToken = clientInteractionTokenService.generate(processId);
        if (process.getCoordinatorConnectionId() != null) {
            if (process.getClientInteractionData() == null
                    || process.getClientInteractionTokenHash() == null) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Started process is incomplete");
            }
            return new IntegrationProcessResponse(
                    processId,
                    clientInteractionToken,
                    process.getTxCode(),
                    process.getExpiresAt());
        }

        JsonNode clientConfigurationOverride =
                requestedClientConfiguration == null || requestedClientConfiguration.isNull()
                        ? process.getClientConfiguration()
                        : requestedClientConfiguration;
        JsonNode clientConfiguration =
                resolveClientConfiguration(process.getTenantId(), clientConfigurationOverride);

        StartProcessResponse started =
                orchestrationService.start(new SignatureToken(processToken));
        StartProcessResponse.ProcessData processData =
                started.crossDevice() != null ? started.crossDevice() : started.sameDevice();
        if (processData == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Process did not return a connection");
        }

        process.setClientInteractionTokenHash(
                clientInteractionTokenService.hash(clientInteractionToken));
        process.setCoordinatorConnectionId(processData.connectionId());
        process.setClientInteractionData(
                objectMapper.valueToTree(toClientInteractionData(started)));
        process.setClientConfiguration(clientConfiguration);
        processDataService.save(process);

        return new IntegrationProcessResponse(
                processId, clientInteractionToken, process.getTxCode(), process.getExpiresAt());
    }

    private JsonNode copyClientConfigurationObject(JsonNode clientConfiguration) {
        if (clientConfiguration == null || clientConfiguration.isNull()) {
            return null;
        }
        if (!clientConfiguration.isObject()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "clientConfiguration must be a JSON object");
        }
        return clientConfiguration.deepCopy();
    }

    private JsonNode resolveClientConfiguration(
            String tenantId, JsonNode clientConfigurationOverride) {
        JsonNode configuration =
                clientConfigurationOverride == null || clientConfigurationOverride.isNull()
                        ? authenticationService.getTenantClientConfiguration(tenantId)
                        : clientConfigurationOverride;
        if (configuration == null || configuration.isNull()) {
            return walletCatalogService.getDefaultClientConfiguration();
        }
        return copyClientConfigurationObject(configuration);
    }

    public IntegrationProcessResult getProcessResult(
            UUID processId, String authorizationHeader, String apiKeyHeader)
            throws JacksonException {
        String integrationCredential =
                extractIntegrationCredential(authorizationHeader, apiKeyHeader);
        IntegrationProcessEntity process = processDataService.getById(processId);
        if (!MessageDigest.isEqual(
                process.getIntegrationCredentialHash().getBytes(StandardCharsets.UTF_8),
                hashIntegrationCredential(integrationCredential).getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Process not found");
        }
        if (process.getCoordinatorConnectionId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Process has not been started");
        }

        ConnectionStateV2 state =
                orchestrationService.getState(
                        process.getCoordinatorConnectionId());
        JsonNode vpToken = null;
        if (process.isIncludeVpToken()
                && Action.PRESENTATION.equals(process.getAction())
                && state.state() == ConnectionStateV2.ConnectionStateEnum.CREDENTIAL_ACCEPTED) {
            vpToken = orchestrationService.getVpToken(process.getCoordinatorConnectionId());
        }
        return new IntegrationProcessResult(processId, state, vpToken);
    }

    private ClientInteractionData toClientInteractionData(StartProcessResponse started) {
        return new ClientInteractionData(
                toPersistedClientProcessData(started.crossDevice()),
                toPersistedClientProcessData(started.sameDevice()));
    }

    private ClientInteractionData.ProcessData toPersistedClientProcessData(
            StartProcessResponse.ProcessData processData) {
        return processData == null
                ? null
                : new ClientInteractionData.ProcessData(
                        processData.qrCodeDataPath(), processData.qrCodeDataScheme());
    }

    private String extractIntegrationCredential(
            String authorizationHeader, String apiKeyHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith(API_KEY_SCHEME)) {
            if (!authorizationHeader.substring(API_KEY_SCHEME.length()).isBlank()) {
                return authorizationHeader;
            }
        }
        if (authorizationHeader != null
                && authorizationHeader.startsWith("Bearer ")
                && !authorizationHeader.substring("Bearer ".length()).isBlank()) {
            if (clientInteractionTokenService.isClientInteractionToken(
                    authorizationHeader.substring(CLIENT_TOKEN_SCHEME.length()))) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Client interaction token is not a server credential");
            }
            return authorizationHeader;
        }
        if (authorizationHeader != null
                && authorizationHeader.startsWith("Basic ")
                && !authorizationHeader.substring("Basic ".length()).isBlank()) {
            return authorizationHeader;
        }
        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            return API_KEY_SCHEME + apiKeyHeader;
        }
        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "API key, server bearer, or server basic credential required");
    }

    private String hashIntegrationCredential(String integrationCredential) {
        return clientInteractionTokenService.hash(integrationCredential);
    }

    private void requireIntegrationCredential(
            IntegrationProcessEntity process, String integrationCredential) {
        if (process.getIntegrationCredentialHash() == null
                || !MessageDigest.isEqual(
                        process.getIntegrationCredentialHash().getBytes(StandardCharsets.UTF_8),
                        hashIntegrationCredential(integrationCredential)
                                .getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Process not found");
        }
    }

    private void requireProcessToken(IntegrationProcessEntity process, String processToken) {
        if (process.getProcessTokenHash() == null
                || !MessageDigest.isEqual(
                        process.getProcessTokenHash().getBytes(StandardCharsets.UTF_8),
                        clientInteractionTokenService.hash(processToken)
                                .getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Process not found");
        }
    }

    private void requireNotExpired(ZonedDateTime expiresAt) {
        if (expiresAt == null || expiresAt.isBefore(ZonedDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Interaction expired");
        }
    }
}
