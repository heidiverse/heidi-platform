// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import jakarta.validation.Valid;
import org.heidiverse.heidi.entity.model.exceptions.InvalidAttributeException;
import org.heidiverse.heidi.entity.model.exceptions.SchemaNotFoundException;
import org.heidiverse.heidi.entity.model.exceptions.TenantNotFoundException;
import org.heidiverse.heidi.entity.model.tenant.TrustRegistryType;
import org.heidiverse.heidi.entity.model.proofscheme.*;
import org.heidiverse.heidi.entity.service.ProofSchemeService;
import org.heidiverse.heidi.entity.service.utils.JwtUtils;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/management/v1/proof-schemas")
@CrossOrigin(originPatterns = "*")
public class ProofSchemeController {

    private final ProofSchemeService proofSchemeService;

    public ProofSchemeController(final ProofSchemeService proofSchemeService) {
        this.proofSchemeService = proofSchemeService;
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('EDITOR', 'ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ProofSchemeResponse> createProofScheme(
            @Valid @RequestBody final ProofSchemePayload proofSchemePayload,
            @RequestParam(required = false) String tenantId)
            throws SchemaNotFoundException, InvalidAttributeException, TenantNotFoundException {
        // Resolve tenant ID
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);

        // Insert proof scheme
        UUID id = proofSchemeService.insertProofScheme(proofSchemePayload, tenantId).uuid();
        return ResponseEntity.ok(new ProofSchemeResponse(id, "Proof scheme successfully created."));
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasAnyAuthority('EDITOR', 'ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<String> updateProofScheme(
            @PathVariable final UUID id,
            @Valid @RequestBody final ProofSchemePayload proofSchemePayload)
            throws SchemaNotFoundException, InvalidAttributeException, TenantNotFoundException {
        proofSchemeService.updateProofScheme(id, proofSchemePayload);
        return ResponseEntity.ok("Proof scheme successfully updated.");
    }

    @PutMapping("/archive/{id}")
    @PreAuthorize("hasAnyAuthority('EDITOR', 'ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<String> archiveProofScheme(@PathVariable final UUID id) {
        proofSchemeService.archiveProofScheme(id);
        return ResponseEntity.ok("Proof scheme successfully archived.");
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProofSchemeDetail> getProofScheme(@PathVariable final UUID id) {
        return ResponseEntity.ok(proofSchemeService.findById(id, true));
    }

    @PutMapping("/publish/{proofschemeId}/trust-registry/de")
    @PreAuthorize("hasAnyAuthority('EDITOR', 'ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<String> publishProofScheme(@PathVariable final UUID proofschemeId)
            throws TenantNotFoundException {
        proofSchemeService.publishProofSchemeToTrustRegistry(proofschemeId, TrustRegistryType.DE);
        return ResponseEntity.ok("Proof scheme successfully published to german trust registry.");
    }

    @GetMapping("/overview")
    public ResponseEntity<ProofSchemeOverview> getAllProofSchemes(
            @RequestParam(required = false) List<String> credentialIdentifiers) {
        return ResponseEntity.ok(proofSchemeService.findAll(true, credentialIdentifiers));
    }
}
