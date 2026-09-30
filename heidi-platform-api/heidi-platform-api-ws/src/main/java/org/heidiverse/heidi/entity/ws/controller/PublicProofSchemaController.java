// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import org.heidiverse.heidi.entity.model.proofscheme.ProofSchemeDetail;
import org.heidiverse.heidi.entity.model.proofscheme.ProofSchemeOverview;
import org.heidiverse.heidi.entity.service.ProofSchemeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Wallet-facing proof configuration. These protocol URLs remain stable. */
@RestController
@RequestMapping("/public/v1/proofscheme")
@CrossOrigin(originPatterns = "*")
public class PublicProofSchemaController {

    private final ProofSchemeService proofSchemeService;

    public PublicProofSchemaController(ProofSchemeService proofSchemeService) {
        this.proofSchemeService = proofSchemeService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProofSchemeDetail> getProofScheme(@PathVariable UUID id) {
        return ResponseEntity.ok(proofSchemeService.findById(id, false));
    }

    @GetMapping("/overview")
    public ResponseEntity<ProofSchemeOverview> getAllProofSchemes(
            @RequestParam(required = false) List<String> credentialIdentifiers) {
        return ResponseEntity.ok(proofSchemeService.findAll(false, credentialIdentifiers));
    }
}
