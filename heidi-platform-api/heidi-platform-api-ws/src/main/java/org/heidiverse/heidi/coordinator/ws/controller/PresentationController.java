// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.ws.controller;

import static org.heidiverse.heidi.platformapi.ws.config.ApiDocumentationConfig.TAG_PRESENTATION_SUPPORT;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.kapunsdk.presentation.request.model.OID4VPVersion;
import org.heidiverse.heidi.coordinator.model.exceptions.DoctypeNotFoundException;
import org.heidiverse.heidi.coordinator.model.exceptions.VctNotFoundException;
import org.heidiverse.heidi.coordinator.service.Oid4vpService;

import org.springframework.web.bind.annotation.*;

import uniffi.kapun_dcql_rust.DcqlQuery;

@RestController
@RequestMapping("/interaction/v1/presentation")
@CrossOrigin(originPatterns = "*")
@Tag(
        name = TAG_PRESENTATION_SUPPORT,
        description =
                "Browser helpers for custom presentation pages. Standard Web Components handle"
                        + " these calls internally.")
public class PresentationController {
    private final Oid4vpService oid4vpService;

    public PresentationController(Oid4vpService oid4vpService) {
        this.oid4vpService = oid4vpService;
    }

    @Operation(
            summary = "Build the DCQL request for a proof schema",
            description =
                    "Advanced helper for a custom presentation UI that needs the exact claims"
                            + " requested by a published proof schema. The standard process flow"
                            + " and Heidi Web Components resolve this automatically.")
    @GetMapping("/dcqlQuery/{schemaId}")
    public DcqlQuery getDcqlQuery(
            @Parameter(description = "UUID of the published proof schema.")
                    @PathVariable("schemaId")
                    String schemaId)
            throws DoctypeNotFoundException, VctNotFoundException {
        var proofSchema = this.oid4vpService.getProofScheme(schemaId);
        return this.oid4vpService.getDcqlQuery(proofSchema, OID4VPVersion.DRAFT_28);
    }
}
