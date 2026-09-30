// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.ws.controller;

import org.heidiverse.heidi.coordinator.model.api.InitializeProcessRequest;
import org.heidiverse.heidi.coordinator.model.api.IntegrationProcessResponse;
import org.heidiverse.heidi.coordinator.model.exceptions.DoctypeNotFoundException;
import org.heidiverse.heidi.coordinator.model.exceptions.VctNotFoundException;
import org.heidiverse.heidi.coordinator.ws.service.IntegrationProcessService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.core.JacksonException;

import java.net.URISyntaxException;

/**
 * Cockpit-only process operations. These endpoints support the authenticated testing UI and are
 * deliberately outside the external integrator API namespace.
 */
@RestController
@RequestMapping("/management/v1/testing")
public class CockpitTestingProcessController {

    private final IntegrationProcessService service;

    public CockpitTestingProcessController(IntegrationProcessService service) {
        this.service = service;
    }

    @Operation(
            summary = "Create and start a process for Cockpit testing",
            description =
                    "Uses the authenticated Cockpit operator's platform session to initialize"
                            + " and start a process server-side. Returns only the client interaction"
                            + " token needed by the testing UI.")
    @Transactional
    @PostMapping("/processes")
    public IntegrationProcessResponse createTestingProcess(
            @Valid @NotNull @RequestBody InitializeProcessRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader)
            throws JacksonException,
                    URISyntaxException,
                    VctNotFoundException,
                    DoctypeNotFoundException {
        return service.createProcessForSession(request, authorizationHeader);
    }
}
