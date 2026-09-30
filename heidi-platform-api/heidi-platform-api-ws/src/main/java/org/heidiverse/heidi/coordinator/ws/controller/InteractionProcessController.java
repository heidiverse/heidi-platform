// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.ws.controller;

import static org.heidiverse.heidi.platformapi.ws.config.ApiDocumentationConfig.TAG_BROWSER_PROCESS;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.heidiverse.heidi.coordinator.model.api.ClientInteractionResponse;
import org.heidiverse.heidi.coordinator.service.ClientInteractionService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.core.JacksonException;

/** Browser participation in an existing integration process. */
@RestController
@RequestMapping("/interaction/v1")
@CrossOrigin(originPatterns = "*")
@Tag(
        name = TAG_BROWSER_PROCESS,
        description =
                "Browser-safe continuation of a process already started by the integrating"
                        + " backend. Heidi Web Components call this automatically; a custom"
                        + " frontend calls it with the short-lived client interaction token.")
public class InteractionProcessController {

    private final ClientInteractionService service;

    public InteractionProcessController(ClientInteractionService service) {
        this.service = service;
    }

    @Operation(
            summary = "Get browser-safe interaction data",
            description =
                    "Call after the backend start endpoint returns `clientInteractionToken`. The"
                            + " response contains current process state, wallet hand-off data,"
                            + " resolved client configuration, and allowlisted display claims. It"
                            + " never returns the integration API key, process token, or complete"
                            + " backend result.")
    @GetMapping("/processes/current")
    public ClientInteractionResponse get(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader)
            throws JacksonException {
        return service.get(authorizationHeader);
    }
}
