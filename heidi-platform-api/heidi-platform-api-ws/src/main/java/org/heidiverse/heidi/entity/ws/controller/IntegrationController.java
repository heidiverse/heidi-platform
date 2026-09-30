// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import org.heidiverse.heidi.entity.model.integration.*;
import org.heidiverse.heidi.entity.model.user.UserRole;
import org.heidiverse.heidi.entity.service.IntegrationService;
import org.heidiverse.heidi.entity.service.utils.JwtUtils;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/management/v1/integrations")
@CrossOrigin(originPatterns = "*")
public class IntegrationController {

    private final IntegrationService integrationService;

    public IntegrationController(final IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<IntegrationDetail> createIntegration(
            @RequestBody IntegrationPayload integrationPayload,
            @RequestParam(required = false) String tenantId) {

        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        return ResponseEntity.ok(
                integrationService.createIntegration(integrationPayload, tenantId));
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN', 'DEVELOPER')")
    public ResponseEntity<IntegrationDetail> updateIntegration(
            @PathVariable UUID id, @RequestBody IntegrationPayload integrationPayload) {
        return ResponseEntity.ok(integrationService.updateIntegration(id, integrationPayload));
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN', 'DEVELOPER')")
    public ResponseEntity<String> deleteIntegration(@PathVariable UUID id) {
        integrationService.deleteIntegration(id);
        return ResponseEntity.ok("Integration successfully deleted.");
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN', 'DEVELOPER')")
    public ResponseEntity<IntegrationOverview> getAllIntegrations(
            @RequestParam(required = false) Boolean isPublic) {
        var userProfile = JwtUtils.getUserProfile();
        if (userProfile.permissions().contains(UserRole.SUPER_ADMIN)) {
            // Return all integrations for super admins
            return ResponseEntity.ok(
                    new IntegrationOverview(integrationService.findAllOverview(isPublic)));
        }
        // Otherwise filter by tenant ID
        var tenantId = userProfile.tenantId();
        return ResponseEntity.ok(
                new IntegrationOverview(
                        integrationService.findAllOverviewByTenantId(tenantId, isPublic)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN', 'DEVELOPER')")
    public ResponseEntity<IntegrationDetail> getIntegration(@PathVariable UUID id) {
        return ResponseEntity.ok(integrationService.findById(id));
    }

    @GetMapping("/credentials")
    public ResponseEntity<IntegrationAuthorizedCredentialsResponse> getAuthorizedCredentials(
            @RequestHeader("X-API-KEY") String apiKey) {
        List<String> credentials = integrationService.findCredentialsByApiKey(apiKey);
        List<IntegrationScope> scopes = integrationService.findScopesByApiKey(apiKey);
        String tenantId = integrationService.findTenantIdByApiKey(apiKey);
        return credentials.isEmpty()
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(
                        new IntegrationAuthorizedCredentialsResponse(
                                credentials, scopes, tenantId));
    }
}
