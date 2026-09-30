// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.ws.controller;

import static org.heidiverse.heidi.platformapi.ws.config.ApiDocumentationConfig.TAG_BACKEND_PROCESS;

import org.heidiverse.heidi.coordinator.model.api.InitializeProcessRequest;
import org.heidiverse.heidi.coordinator.model.api.IntegrationProcessInitializationResponse;
import org.heidiverse.heidi.coordinator.model.api.IntegrationProcessResponse;
import org.heidiverse.heidi.coordinator.model.api.IntegrationProcessResult;
import org.heidiverse.heidi.coordinator.model.api.StartIntegrationProcessRequest;
import org.heidiverse.heidi.coordinator.model.exceptions.DoctypeNotFoundException;
import org.heidiverse.heidi.coordinator.model.exceptions.VctNotFoundException;
import org.heidiverse.heidi.coordinator.ws.service.IntegrationProcessService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.core.JacksonException;

import java.net.URISyntaxException;
import java.util.UUID;

/** Server-to-server integration API. */
@RestController
@RequestMapping("/integration/v1")
@Tag(
        name = TAG_BACKEND_PROCESS,
        description =
                "Backend-only calls for initializing, starting, and retrieving issuance or"
                        + " presentation processes. Never expose the API key or process token to a"
                        + " browser.")
public class IntegrationProcessController {

    private final IntegrationProcessService service;

    public IntegrationProcessController(IntegrationProcessService service) {
        this.service = service;
    }

    @Operation(
            summary = "Initialize an integration process",
            description =
                    "Creates an issuance or presentation process. Returns `processId` and a"
                            + " backend-only `processToken`. Retain both on the integrating server,"
                            + " then exchange the token through the start endpoint. The token must"
                            + " never reach a browser.")
    @PostMapping("/processes")
    public IntegrationProcessInitializationResponse initialize(
            @Valid @NotNull @RequestBody InitializeProcessRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(name = "X-API-KEY", required = false) String apiKeyHeader)
            throws JacksonException,
                    URISyntaxException,
                    VctNotFoundException,
                    DoctypeNotFoundException {
        return service.initializeProcess(request, authorizationHeader, apiKeyHeader);
    }

    @Operation(
            summary = "Start an initialized integration process",
            description =
                    "Exchanges the backend-only process token and starts the issuer or verifier"
                            + " transaction. The response contains a short-lived"
                            + " `clientInteractionToken`; send only that token to the browser or"
                            + " Web Component.")
    @PostMapping("/processes/{processId}/start")
    public IntegrationProcessResponse start(
            @PathVariable UUID processId,
            @Valid @NotNull @RequestBody StartIntegrationProcessRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(name = "X-API-KEY", required = false) String apiKeyHeader)
            throws JacksonException,
                    URISyntaxException,
                    VctNotFoundException,
                    DoctypeNotFoundException {
        return service.startIntegrationProcess(
                processId, request, authorizationHeader, apiKeyHeader);
    }

    @Operation(
            summary = "Get the complete integration process result",
            description =
                    "Poll from the integrating backend using the original API key. Returns process"
                            + " state and disclosed claims. Raw VP data is returned only when"
                            + " `includeVpToken` was requested during initialization.")
    @GetMapping("/processes/{processId}/result")
    public IntegrationProcessResult result(
            @PathVariable UUID processId,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(name = "X-API-KEY", required = false) String apiKeyHeader)
            throws JacksonException {
        return service.getProcessResult(processId, authorizationHeader, apiKeyHeader);
    }
}
