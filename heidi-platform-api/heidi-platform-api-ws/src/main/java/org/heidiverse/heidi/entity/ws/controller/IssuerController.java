// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import org.heidiverse.heidi.coordinator.service.AuthenticationService;
import org.heidiverse.heidi.entity.model.issuer.CredentialEncryptionAlgorithms;
import org.heidiverse.heidi.entity.model.issuer.FederationAuthority;
import org.heidiverse.heidi.entity.model.issuer.IssuerCredentialEncryption;
import org.heidiverse.heidi.entity.model.issuer.IssuerDefinitionRequest;
import org.heidiverse.heidi.entity.model.issuer.IssuerDefinitionResponse;
import org.heidiverse.heidi.entity.model.issuer.IssuerFederation;
import org.heidiverse.heidi.entity.model.issuer.IssuerFederationResponse;
import org.heidiverse.heidi.entity.model.issuer.IssuerOverview;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotRequest;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotResponse;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeyIntegrityReport;
import org.heidiverse.heidi.entity.model.issuer.IdentityGrantStatus;
import org.heidiverse.heidi.entity.model.user.UserRole;
import org.heidiverse.heidi.entity.service.IdentityFederationService;
import org.heidiverse.heidi.entity.service.IdentityKeyIntegrityService;
import org.heidiverse.heidi.entity.service.IdentityKeySlotService;
import org.heidiverse.heidi.entity.service.IssuerService;
import org.heidiverse.heidi.entity.service.TenantService;
import org.heidiverse.heidi.entity.service.utils.JwtUtils;

import jakarta.validation.Valid;
import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/management/v1/identities")
@CrossOrigin(originPatterns = "*")
public class IssuerController {
    private final IssuerService issuerService;
    private final AuthenticationService authenticationService;
    private final TenantService tenantService;
    private final IdentityFederationService federationService;

    @Autowired(required = false)
    private IdentityKeySlotService identityKeySlotService;

    @Autowired(required = false)
    private IdentityKeyIntegrityService identityKeyIntegrityService;

    public IssuerController(
            final IssuerService issuerService,
            final AuthenticationService authenticationService,
            final TenantService tenantService,
            final IdentityFederationService federationService) {
        this.issuerService = issuerService;
        this.authenticationService = authenticationService;
        this.tenantService = tenantService;
        this.federationService = federationService;
    }

    @GetMapping("/overview")
    public ResponseEntity<IssuerOverview> getAllIssuers(
            @RequestParam(required = false) String tenantId) {
        var user = JwtUtils.getUserProfile();
        var isSuperAdmin = user.permissions().contains(UserRole.SUPER_ADMIN);
        if (tenantId != null || !isSuperAdmin) {
            return ResponseEntity.ok(issuerService.findIdentitiesAvailableToTenant(
                    JwtUtils.validateAndResolveTenantId(tenantId)));
        }
        return ResponseEntity.ok(issuerService.findGlobalIdentities());
    }

    @GetMapping("/{id}")
    public ResponseEntity<IssuerDefinitionResponse> getIssuerById(@PathVariable int id) {
        Optional<IssuerDefinitionResponse> issuer = issuerService.findIssuerById(id);
        return issuer.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Endpoint to create a new issuer and generate a certificate
     *
     * @param issuerDefinition the new issuer details
     * @return ResponseEntity with the created issuer
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<IssuerDefinitionResponse> createIssuer(
            @Valid @RequestBody IssuerDefinitionRequest issuerDefinition) {
        // Create issuer and generate certificate for it
        IssuerDefinitionResponse createdIssuer = issuerService.createIssuer(issuerDefinition);
        return ResponseEntity.ok(createdIssuer);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<IssuerDefinitionResponse> updateIssuer(
            @PathVariable int id, @Valid @RequestBody IssuerDefinitionRequest issuer) {
        Optional<IssuerDefinitionResponse> existingIssuer = issuerService.findIssuerById(id);
        if (existingIssuer.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(issuerService.updateIssuer(id, issuer));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteIssuer(@PathVariable int id) {
        if (!issuerService.deleteIssuerById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tenants/{tenantId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<IssuerDefinitionResponse> createTenantIdentity(
            @PathVariable String tenantId,
            @Valid @RequestBody IssuerDefinitionRequest identity) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        if (tenantService.getTenant(tenantId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var created = issuerService.createIssuer(identity, tenantId);
        tenantService.addIdentity(tenantId, created.id());
        return ResponseEntity.ok(created);
    }

    @PutMapping("/tenants/{tenantId}/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<IssuerDefinitionResponse> updateTenantIdentity(
            @PathVariable String tenantId,
            @PathVariable int id,
            @Valid @RequestBody IssuerDefinitionRequest identity) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        return ResponseEntity.ok(issuerService.updateTenantIdentity(tenantId, id, identity));
    }

    @DeleteMapping("/tenants/{tenantId}/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<Void> deleteTenantIdentity(
            @PathVariable String tenantId, @PathVariable int id) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        issuerService.deleteTenantIdentity(tenantId, id);
        tenantService.removeIdentity(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tenants/{tenantId}/{id}/slots")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<List<IdentityKeySlotResponse>> listIdentitySlots(
            @PathVariable String tenantId, @PathVariable int id) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        return ResponseEntity.ok(requireSlotService().list(tenantId, id));
    }

    @GetMapping("/{id}/slots")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<List<IdentityKeySlotResponse>> listGlobalIdentitySlots(
            @PathVariable int id) {
        return ResponseEntity.ok(requireSlotService().list(null, id));
    }

    @GetMapping("/tenants/{tenantId}/{id}/grants")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<List<IdentityGrantStatus>> listIdentityGrants(
            @PathVariable String tenantId, @PathVariable int id) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        return ResponseEntity.ok(issuerService.identityGrants(tenantId, id));
    }

    @GetMapping("/{id}/grants")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<List<IdentityGrantStatus>> listGlobalIdentityGrants(
            @PathVariable int id) {
        return ResponseEntity.ok(issuerService.identityGrants(null, id));
    }

    @PutMapping("/tenants/{tenantId}/{id}/slots")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<IdentityKeySlotResponse> saveIdentitySlot(
            @PathVariable String tenantId,
            @PathVariable int id,
            @Valid @RequestBody IdentityKeySlotRequest request) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        var response = requireSlotService().save(tenantId, id, request);
        issuerService.reconcileSigningGrants();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/slots")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<IdentityKeySlotResponse> saveGlobalIdentitySlot(
            @PathVariable int id, @Valid @RequestBody IdentityKeySlotRequest request) {
        var response = requireSlotService().save(null, id, request);
        issuerService.reconcileSigningGrants();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/tenants/{tenantId}/{id}/slots/{slotId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteIdentitySlot(
            @PathVariable String tenantId,
            @PathVariable int id,
            @PathVariable java.util.UUID slotId) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        requireSlotService().delete(tenantId, id, slotId);
        issuerService.reconcileSigningGrants();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/slots/{slotId}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteGlobalIdentitySlot(
            @PathVariable int id, @PathVariable java.util.UUID slotId) {
        requireSlotService().delete(null, id, slotId);
        issuerService.reconcileSigningGrants();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/key-integrity-report")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<IdentityKeyIntegrityReport> keyIntegrityReport() {
        if (identityKeyIntegrityService == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(identityKeyIntegrityService.scan());
    }

    private IdentityKeySlotService requireSlotService() {
        if (identityKeySlotService == null) {
            throw new IllegalStateException("Identity key slots are not configured");
        }
        return identityKeySlotService;
    }

    @GetMapping("/credential-encryption/supported")
    public ResponseEntity<SupportedCredentialEncryption> supportedCredentialEncryption() {
        return ResponseEntity.ok(new SupportedCredentialEncryption(
                CredentialEncryptionAlgorithms.KEY_MANAGEMENT,
                CredentialEncryptionAlgorithms.CONTENT_ENCRYPTION,
                CredentialEncryptionAlgorithms.COMPRESSION));
    }

    @GetMapping("/tenants/{tenantId}/{id}/credential-encryption")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<IssuerCredentialEncryption> getCredentialEncryption(
            @PathVariable String tenantId, @PathVariable int id) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        return ResponseEntity.ok(issuerService.getTenantCredentialEncryption(tenantId, id));
    }

    @PutMapping("/tenants/{tenantId}/{id}/credential-encryption")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<IssuerCredentialEncryption> updateCredentialEncryption(
            @PathVariable String tenantId,
            @PathVariable int id,
            @RequestBody IssuerCredentialEncryption request) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        return ResponseEntity.ok(
                issuerService.updateCredentialEncryption(tenantId, id, request));
    }

    @GetMapping("/{id}/credential-encryption")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<IssuerCredentialEncryption> getGlobalCredentialEncryption(
            @PathVariable int id) {
        return ResponseEntity.ok(issuerService.getGlobalCredentialEncryption(id));
    }

    @PutMapping("/{id}/credential-encryption")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<IssuerCredentialEncryption> updateGlobalCredentialEncryption(
            @PathVariable int id, @RequestBody IssuerCredentialEncryption request) {
        return ResponseEntity.ok(issuerService.updateGlobalCredentialEncryption(id, request));
    }

    @GetMapping("/tenants/{tenantId}/{id}/federation")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<IssuerFederationResponse> getFederation(
            @PathVariable String tenantId, @PathVariable int id) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        return ResponseEntity.ok(federationService.get(tenantId, id));
    }

    @PutMapping("/tenants/{tenantId}/{id}/federation")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<IssuerFederationResponse> updateFederation(
            @PathVariable String tenantId,
            @PathVariable int id,
            @RequestBody IssuerFederation request) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        return ResponseEntity.ok(federationService.update(tenantId, id, request));
    }

    @GetMapping("/{id}/federation")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<IssuerFederationResponse> getGlobalFederation(@PathVariable int id) {
        return ResponseEntity.ok(federationService.get(null, id));
    }

    @PutMapping("/{id}/federation")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<IssuerFederationResponse> updateGlobalFederation(
            @PathVariable int id, @RequestBody IssuerFederation request) {
        return ResponseEntity.ok(federationService.update(null, id, request));
    }

    /** Authorities on this platform, offered as authority hints. */
    @GetMapping("/federation/authorities")
    public ResponseEntity<List<FederationAuthority>> getFederationAuthorities() {
        return ResponseEntity.ok(federationService.authorities());
    }

    /** What an issuer may choose from; Cockpit renders the selection from this. */
    public record SupportedCredentialEncryption(
            java.util.List<String> algValues,
            java.util.List<String> encValues,
            java.util.List<String> zipValues) {}
}
