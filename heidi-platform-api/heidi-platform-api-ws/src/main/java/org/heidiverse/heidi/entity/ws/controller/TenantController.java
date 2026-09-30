// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import org.heidiverse.heidi.entity.model.exceptions.TenantNotFoundException;
import org.heidiverse.heidi.entity.model.tenant.TenantFeatures;
import org.heidiverse.heidi.entity.model.tenant.TenantRequest;
import org.heidiverse.heidi.entity.model.tenant.TenantResponse;
import org.heidiverse.heidi.entity.model.tenant.WalletCatalogEntry;
import org.heidiverse.heidi.entity.service.TenantService;
import org.heidiverse.heidi.entity.service.WalletCatalogService;
import org.heidiverse.heidi.entity.service.utils.JwtUtils;
import org.heidiverse.heidi.entity.service.utils.ThumbnailUtils;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/management/v1/tenants")
@CrossOrigin(originPatterns = "*")
public class TenantController {
    private final TenantService tenantService;
    private final WalletCatalogService walletCatalogService;

    public TenantController(TenantService tenantService, WalletCatalogService walletCatalogService) {
        this.tenantService = tenantService;
        this.walletCatalogService = walletCatalogService;
    }

    @Operation(summary = "Get the configured wallet catalog")
    @GetMapping("/wallet-catalog")
    public ResponseEntity<List<WalletCatalogEntry>> getWalletCatalog() {
        return ResponseEntity.ok(walletCatalogService.getWallets());
    }

    @Operation(summary = "Get tenant details")
    @GetMapping({"/{tenantId}", ""})
    public ResponseEntity<TenantResponse> getTenant(
            @PathVariable(required = false) String tenantId) {
        // Get tenant ID from JWT if not set
        String jwtTenantId = JwtUtils.getUserProfile().tenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = jwtTenantId;
        }

        // Deactivated tenants stay readable here; the response carries the flag.
        return tenantService
                .getTenantIncludingDeleted(tenantId)
                .map(TenantResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "List all tenants, deactivated ones included")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<List<TenantResponse>> getTenants() {
        return ResponseEntity.ok(
                tenantService.getTenants().stream()
                        .map(TenantResponse::from)
                        .toList());
    }

    @Operation(summary = "Bootstrap the authenticated super admin's tenant")
    @PostMapping("/bootstrap")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<Void> bootstrapTenant() {
        String tenantId = JwtUtils.getUserProfile().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // Include deactivated tenants in the check so signing in never reactivates one.
        if (tenantService.getTenantIncludingDeleted(tenantId).isEmpty()) {
            tenantService.upsertTenant(tenantId, new TenantRequest());
        }

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Update tenant details")
    @PostMapping({"/{tenantId}", ""})
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Void> updateTenant(
            @PathVariable(required = false) String tenantId,
            @Valid @RequestBody TenantRequest request) {

        // Tenant ID validation
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);

        // Validate thumbnail
        if (request.getThumbnail() != null && !request.getThumbnail().isEmpty()) {
            ThumbnailUtils.validateThumbnail(request.getThumbnail());
        }

        tenantService.upsertTenant(tenantId, request);

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get tenant features")
    @GetMapping("/{tenantId}/features")
    public ResponseEntity<TenantFeatures> getTenantFeatures(
            @PathVariable String tenantId) throws TenantNotFoundException {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        return ResponseEntity.ok(tenantService.getTenantFeatures(tenantId));
    }

    @Operation(summary = "Update tenant features")
    @PostMapping("/{tenantId}/features")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Void> updateTenantFeatures(
            @PathVariable String tenantId, @Valid @RequestBody TenantFeatures request)
            throws TenantNotFoundException {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        tenantService.updateTenantFeatures(tenantId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Delete tenant")
    @DeleteMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteTenant(@PathVariable String tenantId) {
        tenantService.deleteTenant(tenantId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tenantId}/trust-registry/de")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Void> addTenantToGermanTrustRegistry(
            @PathVariable(required = true) String tenantId) throws Exception {

        // Tenant ID validation
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);

        tenantService.insertTenantIntoGermanTrustRegistry(tenantId);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{tenantId}/trust-registry/de/generateVerifierCertificate")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Void> generateVerifierCertificate(
            @PathVariable(required = true) String tenantId) throws Exception {

        // Tenant ID validation
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);

        tenantService.updateTenantCertificateInGermanTrustRegistry(tenantId);

        return ResponseEntity.ok().build();
    }

}
