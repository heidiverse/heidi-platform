// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.ws.controller;

import static org.heidiverse.heidi.platformapi.ws.config.ApiDocumentationConfig.TAG_PRESENTATION_HANDOFF;

import org.kapunsdk.presentation.request.model.OID4VPVersion;
import org.heidiverse.heidi.coordinator.data.service.ProtocolProcessDataService;
import org.heidiverse.heidi.coordinator.model.ProtocolType;
import org.heidiverse.heidi.coordinator.model.ProofSchemeResponse;
import org.heidiverse.heidi.coordinator.model.exceptions.DoctypeNotFoundException;
import org.heidiverse.heidi.coordinator.model.exceptions.VctNotFoundException;
import org.heidiverse.heidi.coordinator.model.oid4vci.PresentationData;
import org.heidiverse.heidi.coordinator.service.Oid4vpService;
import org.heidiverse.heidi.platformapi.extensions.process.PresentationHandoffProvider;

import tools.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.NotBlank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;

import java.util.*;

@RestController
@RequestMapping("/interaction/v1")
@Validated
@Tag(
        name = TAG_PRESENTATION_HANDOFF,
        description =
                "Browser endpoint that turns a presentation hand-off token into the redirect to"
                        + " the user's wallet. Web Components navigate here automatically.")
public class OpenId4vpController {

    private static final Logger logger = LoggerFactory.getLogger(OpenId4vpController.class);

    private final Oid4vpService oid4vpService;
    private final ProtocolProcessDataService processDataService;
    private final PresentationHandoffProvider handoffProvider;
    private final ObjectMapper objectMapper;

    public OpenId4vpController(
            Oid4vpService oid4vpService,
            ProtocolProcessDataService processDataService,
            ObjectProvider<PresentationHandoffProvider> handoffProvider,
            ObjectMapper objectMapper) {
        this.oid4vpService = oid4vpService;
        this.processDataService = processDataService;
        this.handoffProvider = handoffProvider.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    @Operation(
            summary = "Redirect the browser to a presentation wallet",
            description =
                    "Consumes the one-time `authFlowToken` from Heidi's presentation hand-off"
                            + " URL, creates the OpenID4VP authorization request, and responds with"
                            + " a `303` redirect to the wallet. Integrators normally use the"
                            + " hand-off URL returned by the browser interaction endpoint instead"
                            + " of constructing this request themselves.",
            responses =
                    @ApiResponse(
                            responseCode = "303",
                            description = "Redirect to the wallet authorization request",
                            headers =
                                    @Header(
                                            name = HttpHeaders.LOCATION,
                                            description = "Wallet authorization request URI",
                                            schema = @Schema(type = "string", format = "uri"))))
    @GetMapping("/qr/openid4vp")
    public ResponseEntity<Void> initiateOpenId4vpFlow(
            @Parameter(
                            name = "authFlowToken",
                            in = ParameterIn.QUERY,
                            description =
                                    "One-time presentation hand-off token. Treat it as a"
                                            + " short-lived browser capability and do not log it.")
                    @RequestParam
                    @NotBlank
                    String authFlowToken,
            @Parameter(
                            name = "useDcApi",
                            in = ParameterIn.QUERY,
                            description =
                                    "Use the browser Digital Credentials API instead of a wallet"
                                            + " URI redirect when supported.")
                    @RequestParam(defaultValue = "false")
                    boolean useDcApi,
            @Parameter(
                            name = "OID4VPVersion",
                            in = ParameterIn.QUERY,
                            description =
                                    "OID4VP protocol version expected by the target wallet. Keep"
                                            + " the default unless the deployment profile requires"
                                            + " another version.",
                            example = "DRAFT_28")
                    @RequestParam(defaultValue = "DRAFT_28")
                    OID4VPVersion OID4VPVersion)
            throws VctNotFoundException, DoctypeNotFoundException, JacksonException {

        final ProofSchemeResponse proofScheme = fetchAuthFlow(authFlowToken);

        // Build presentation data
        final PresentationData presentationData =
                new PresentationData(
                        proofScheme.uuid(), null, useDcApi, OID4VPVersion,
                        proofScheme.presentationProfileId());

        // Generate PAR request to verifier
        final var authorizationRequestObject =
                oid4vpService.getAuthorizationRequestObject(presentationData);

        // Persist presentation data
        processDataService.createProcess(
                authorizationRequestObject.state(),
                objectMapper.valueToTree(presentationData),
                ProtocolType.OID4VP.toString());

        // Bind transaction ID to auth flow ID
        associateTransactionId(authFlowToken, authorizationRequestObject.state());

        // Redirect to same-device URI
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, authorizationRequestObject.responseUriSameDevice())
                .build();
    }

    // -------------------- helpers --------------------

    private ProofSchemeResponse fetchAuthFlow(String authFlowToken) throws ResponseStatusException {
        try {
            if (authFlowToken == null || authFlowToken.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "authFlowToken must be provided");
            }
            if (handoffProvider == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Presentation hand-off is not configured");
            }

            final ProofSchemeResponse proofScheme = handoffProvider.getProofScheme(authFlowToken);
            if (proofScheme == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proof scheme not found");
            }
            return proofScheme;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid authFlowToken format");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            logger.error("Failed to fetch auth flow", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Auth flow error");
        }
    }

    private void associateTransactionId(String authFlowToken, String transactionId)
            throws ResponseStatusException {
        try {
            if (authFlowToken == null || authFlowToken.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "authFlowToken must be provided");
            }
            if (handoffProvider == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Presentation hand-off is not configured");
            }

            handoffProvider.associateTransactionId(authFlowToken, transactionId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid authFlowToken format");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            logger.error("Failed to associate transaction ID", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Auth flow error");
        }
    }
}
