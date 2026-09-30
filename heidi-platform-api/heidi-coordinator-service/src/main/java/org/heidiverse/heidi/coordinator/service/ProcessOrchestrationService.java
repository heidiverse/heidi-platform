// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service;

import feign.FeignException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.NotImplementedException;
import org.heidiverse.heidi.coordinator.data.service.ProtocolProcessDataService;
import org.heidiverse.heidi.coordinator.model.ProtocolType;
import org.heidiverse.heidi.coordinator.model.ProofSchemeResponse;
import org.heidiverse.heidi.coordinator.model.api.InitializeProcessRequest;
import org.heidiverse.heidi.coordinator.model.api.StartProcessResponse;
import org.heidiverse.heidi.coordinator.model.connection.ConnectionStateV2;
import org.heidiverse.heidi.coordinator.model.exceptions.DoctypeNotFoundException;
import org.heidiverse.heidi.coordinator.model.exceptions.VctNotFoundException;
import org.heidiverse.heidi.coordinator.model.integration.IntegrationScope;
import org.heidiverse.heidi.coordinator.model.issuance.SignatureToken;
import org.heidiverse.heidi.coordinator.model.issuance.SignatureTokenWithTxCode;
import org.heidiverse.heidi.coordinator.model.oid4vci.Action;
import org.heidiverse.heidi.coordinator.model.oid4vci.ActionPayload;
import org.heidiverse.heidi.coordinator.model.oid4vci.CredentialOfferType;
import org.heidiverse.heidi.coordinator.model.oid4vci.PreAuthIssuanceData;
import org.heidiverse.heidi.coordinator.model.oid4vci.PresentationData;
import org.heidiverse.heidi.coordinator.model.oid4vci.SignedData;
import org.heidiverse.heidi.coordinator.service.feign.VerifierFeignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;

/** Runs issuance and presentation processes independently of their HTTP adapters. */
@Service
public class ProcessOrchestrationService {

    public static final String VCI_PREFIX = "VCI:";
    public static final String VP_PREFIX = "VP:";

    private static final String CREDENTIAL_OFFER_URI = "credential_offer_uri";
    private static final String CREDENTIAL_OFFER = "credential_offer";
    private static final Logger logger =
            LoggerFactory.getLogger(ProcessOrchestrationService.class);

    private final TokenSignatureService tokenSignatureService;
    private final ProtocolProcessDataService processDataService;
    private final ObjectMapper objectMapper;
    private final Oid4vciService oid4vciService;
    private final Oid4vpService oid4vpService;
    private final VerifierFeignClient verifierFeignClient;
    private final AuthenticationService authenticationService;
    private final IssuanceProcessService issuanceProcessService;
    private final List<ProcessActionExtension> processActionExtensions;

    public ProcessOrchestrationService(
            TokenSignatureService tokenSignatureService,
            ProtocolProcessDataService processDataService,
            ObjectMapper objectMapper,
            Oid4vciService oid4vciService,
            Oid4vpService oid4vpService,
            VerifierFeignClient verifierFeignClient,
            AuthenticationService authenticationService,
            IssuanceProcessService issuanceProcessService,
            List<ProcessActionExtension> processActionExtensions) {
        this.tokenSignatureService = tokenSignatureService;
        this.processDataService = processDataService;
        this.objectMapper = objectMapper;
        this.oid4vciService = oid4vciService;
        this.oid4vpService = oid4vpService;
        this.verifierFeignClient = verifierFeignClient;
        this.authenticationService = authenticationService;
        this.issuanceProcessService = issuanceProcessService;
        this.processActionExtensions = processActionExtensions;
    }

    public SignatureTokenWithTxCode initialize(
            @Valid @NotNull InitializeProcessRequest request, String authorizationHeader) {
        var issuedAt = ZonedDateTime.now();
        var expiresAt = issuedAt.plusMinutes(5);
        var extension = findExtension(request.action());
        if (extension != null) {
            return extension.initialize(request, authorizationHeader, issuedAt, expiresAt);
        }

        if (Action.PRE_AUTH_ISSUANCE.equals(request.action())) {
            if (request.preAuthIssuanceData() == null) {
                throw new IllegalArgumentException(
                        "`preAuthIssuanceData` must be set for PRE_AUTH_ISSUANCE action.");
            }

            var resolvedIssuer = authenticationService.performAuthenticationForInitializeProcess(
                    authorizationHeader, request, IntegrationScope.ISSUE);
            var issuanceData = issuanceProcessService.normalize(
                    request.preAuthIssuanceData(), resolvedIssuer);
            var payload = new SignedData(
                    issuedAt,
                    expiresAt,
                    new ActionPayload(request.action(), issuanceData, null));
            return tokenSignatureService.generateToken(
                    payload,
                    issuanceData.schemaIdentifier().credentialIdentifier(),
                    issuanceData.includeTxCode());
        }

        if (Action.PRESENTATION.equals(request.action())) {
            if (request.presentationData() == null) {
                throw new IllegalArgumentException(
                        "`presentationData` must be set for PRESENTATION action.");
            }

            authenticationService.performAuthenticationForInitializeProcess(
                    authorizationHeader, request, IntegrationScope.VERIFY);
            var payload = new SignedData(
                    issuedAt,
                    expiresAt,
                    new ActionPayload(
                            request.action(),
                            request.presentationData(),
                            null,
                            request.includeVpToken()));
            return tokenSignatureService.generateToken(payload);
        }

        throw new IllegalArgumentException("Unsupported action: " + request.action());
    }

    public boolean supportsIntegrationAction(String action) {
        return findExtension(action) != null;
    }

    public String resolveProcessTenantId(InitializeProcessRequest request) {
        var extension = findExtension(request.action());
        if (extension != null) {
            return extension.resolveProcessTenantId(request);
        }
        return authenticationService.resolveProcessTenantId(request);
    }

    public StartProcessResponse start(SignatureToken processToken)
            throws JacksonException,
                    URISyntaxException,
                    VctNotFoundException,
                    DoctypeNotFoundException {
        SignedData signedData =
                tokenSignatureService.verifyAndGetObject(processToken, SignedData.class);
        ActionPayload payload = objectMapper.convertValue(signedData.data(), ActionPayload.class);
        var extension = findExtension(payload.getAction());
        if (extension != null) return extension.start(processToken, payload);

        if (Action.PRE_AUTH_ISSUANCE.equals(payload.getAction())) {
            return startIssuance(processToken, payload);
        }
        if (Action.PRESENTATION.equals(payload.getAction())) {
            return startPresentation(payload);
        }
        throw new NotImplementedException();
    }

    private StartProcessResponse startIssuance(SignatureToken processToken, ActionPayload payload) {
        PreAuthIssuanceData issuanceData =
                objectMapper.convertValue(payload.getData(), PreAuthIssuanceData.class);
        JsonNode credentialOffer = oid4vciService.fetchCredentialOfferWithToken(
                processToken.token(), issuanceData.issuerSlug());
        String connectionId = oid4vciService.extractConnectionId(credentialOffer);

        processDataService.createProcess(
                connectionId,
                objectMapper.valueToTree(issuanceData),
                ProtocolType.OID4VCI.toString());
        String offer = credentialOfferData(credentialOffer, issuanceData.credentialOfferType());
        return new StartProcessResponse(
                new StartProcessResponse.ProcessData(
                        offer, "openid-credential-offer://", VCI_PREFIX + connectionId),
                null);
    }

    private StartProcessResponse startPresentation(ActionPayload payload)
            throws VctNotFoundException, DoctypeNotFoundException, JacksonException {
        PresentationData presentationData =
                objectMapper.convertValue(payload.getData(), PresentationData.class);
        var request = oid4vpService.getAuthorizationRequestObject(
                presentationData, payload.getIncludeVpToken());
        String clientId = URLEncoder.encode(request.clientId(), StandardCharsets.UTF_8);
        String crossDevice = "?client_id=" + clientId + "&request_uri="
                + URLEncoder.encode(request.responseUriCrossDevice(), StandardCharsets.UTF_8);
        String sameDevice = "?client_id=" + clientId + "&request_uri="
                + URLEncoder.encode(request.responseUriSameDevice(), StandardCharsets.UTF_8);

        processDataService.createProcess(
                request.state(),
                objectMapper.valueToTree(presentationData),
                ProtocolType.OID4VP.toString());
        return new StartProcessResponse(
                new StartProcessResponse.ProcessData(
                        crossDevice, "openid4vp://", VP_PREFIX + request.state()),
                new StartProcessResponse.ProcessData(
                        sameDevice, "openid4vp://", VP_PREFIX + request.state()));
    }

    public ProofSchemeResponse getProofScheme(String proofSchemeId) {
        return oid4vpService.getProofScheme(proofSchemeId);
    }

    public ConnectionStateV2 getState(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            throw new IllegalArgumentException("connectionId must be provided");
        }

        if (connectionId.startsWith(VP_PREFIX)) return getPresentationState(connectionId);
        String cleanId = connectionId.startsWith(VCI_PREFIX)
                ? connectionId.substring(VCI_PREFIX.length())
                : connectionId;
        JsonNode status = oid4vciService.getConnectionStatus(cleanId);
        boolean accessTokenIssued = status.path("accessTokenIssued").asBoolean();
        return new ConnectionStateV2(
                connectionId,
                accessTokenIssued
                        ? ConnectionStateV2.ConnectionStateEnum.STARTED
                        : ConnectionStateV2.ConnectionStateEnum.NOT_STARTED,
                null);
    }

    private ConnectionStateV2 getPresentationState(String connectionId) {
        String verifierId = connectionId.substring(VP_PREFIX.length());
        try {
            var state = verifierFeignClient.getState(verifierId);
            var status = (String) state.get("status");
            var validation = (Boolean) state.get("validation_result");
            if ("NOT_STARTED".equals(status) || "STARTED".equals(status)) {
                return new ConnectionStateV2(
                        connectionId,
                        ConnectionStateV2.ConnectionStateEnum.valueOf(status),
                        null);
            }
            if ("FAILED".equals(status)) {
                return new ConnectionStateV2(
                        connectionId,
                        ConnectionStateV2.ConnectionStateEnum.CREDENTIAL_VALIDATION_FAILED,
                        null,
                        validation);
            }

            var authorization = verifierFeignClient.getAuthorization(verifierId);
            return new ConnectionStateV2(
                    connectionId,
                    ConnectionStateV2.ConnectionStateEnum.CREDENTIAL_ACCEPTED,
                    authorization.get("disclosures"),
                    validation);
        } catch (FeignException exception) {
            logger.error(
                    "Error fetching data for transaction ID {}: {}",
                    connectionId,
                    exception.getMessage());
            return new ConnectionStateV2(
                    connectionId,
                    ConnectionStateV2.ConnectionStateEnum.ERROR_POLL_CREDENTIAL_VERIFICATION,
                    null);
        }
    }

    public JsonNode getVpToken(String connectionId) {
        if (connectionId == null || !connectionId.startsWith(VP_PREFIX)) return null;
        return verifierFeignClient.getVpToken(connectionId.substring(VP_PREFIX.length()));
    }

    private String credentialOfferData(JsonNode credentialOffer, CredentialOfferType type) {
        if (CredentialOfferType.URI.equals(type)) {
            String uri = credentialOffer.path(CREDENTIAL_OFFER_URI).textValue();
            if (uri != null && !uri.isBlank()) {
                return "?" + CREDENTIAL_OFFER_URI + "="
                        + URLEncoder.encode(uri, StandardCharsets.UTF_8);
            }
        }

        JsonNode offer = credentialOffer.deepCopy();
        if (offer instanceof ObjectNode object) object.remove(CREDENTIAL_OFFER_URI);
        return "?" + CREDENTIAL_OFFER + "="
                + URLEncoder.encode(offer.toString(), StandardCharsets.UTF_8);
    }

    private ProcessActionExtension findExtension(String action) {
        return processActionExtensions.stream()
                .filter(extension -> extension.supports(action))
                .findFirst()
                .orElse(null);
    }
}
