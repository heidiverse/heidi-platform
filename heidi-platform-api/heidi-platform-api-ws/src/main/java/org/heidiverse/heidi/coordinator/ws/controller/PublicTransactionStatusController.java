// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.ws.controller;

import static org.heidiverse.heidi.platformapi.ws.config.ApiDocumentationConfig.TAG_PRESENTATION_SUPPORT;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.heidiverse.heidi.coordinator.model.connection.ConnectionStateV2;
import org.heidiverse.heidi.coordinator.service.ProcessOrchestrationService;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Status adapter for ticket pages that already have a transaction identifier. */
@RestController
@RequestMapping({"/interaction/v1/transactions", "/public/v1/transactions"})
@CrossOrigin(originPatterns = "*")
@Tag(
        name = TAG_PRESENTATION_SUPPORT,
        description =
                "Browser helpers for custom presentation pages. Standard Web Components handle"
                        + " these calls internally.")
public class PublicTransactionStatusController {

    private final ProcessOrchestrationService orchestrationService;

    public PublicTransactionStatusController(ProcessOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @Operation(
            summary = "Read public presentation transaction status",
            description =
                    "Lets a browser ticket page poll a presentation transaction after it has a"
                            + " transaction ID. It returns lifecycle status only; disclosed claims"
                            + " remain available exclusively from the backend process result"
                            + " endpoint.")
    @GetMapping("/{transactionId}/status")
    public ConnectionStateV2 getStatus(@PathVariable String transactionId) {
        return orchestrationService.getState(ProcessOrchestrationService.VP_PREFIX + transactionId);
    }
}
