// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import org.heidiverse.heidi.entity.model.proofscheme.BbsIssuerMetadata;
import org.heidiverse.heidi.entity.service.ProofSchemeService;
import org.heidiverse.heidi.shared.signing.SigningOperationResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Platform proof-schema operations consumed by the verifier backend. */
@RestController
@RequestMapping("/internal/platform/v1/proof-schemas")
public class PlatformInternalProofSchemaController {

    private final ProofSchemeService service;

    public PlatformInternalProofSchemaController(ProofSchemeService service) {
        this.service = service;
    }

    @PostMapping(
            value = "/{id}/operations/{operation}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SigningOperationResult> execute(
            @PathVariable UUID id,
            @PathVariable String operation,
            @RequestBody String inputJson) {
        return ResponseEntity.ok(service.executeOperation(id, operation, inputJson));
    }

    @GetMapping("/{id}/bbs-issuer-metadata")
    public ResponseEntity<BbsIssuerMetadata> bbsMetadata(
            @PathVariable UUID id,
            @RequestParam List<String> credentialQueryId) {
        return ResponseEntity.ok(service.bbsIssuerMetadata(id, credentialQueryId));
    }
}
