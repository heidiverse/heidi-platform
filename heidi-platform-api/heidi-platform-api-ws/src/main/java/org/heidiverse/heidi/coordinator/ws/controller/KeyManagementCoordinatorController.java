// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.ws.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyEntity;
import org.heidiverse.heidi.entity.model.issuer.PlatformKeyRequest;
import org.heidiverse.heidi.entity.model.issuer.PlatformKeyResponse;
import org.heidiverse.heidi.entity.model.issuer.KeyCertificateRequest;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateSource;
import org.heidiverse.heidi.entity.service.SigningKeyService;
import org.heidiverse.heidi.entity.service.IssuerService;
import org.heidiverse.heidi.entity.service.PrivateKeyImportService;
import org.heidiverse.heidi.entity.service.utils.JwtUtils;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UI-facing tenant boundary for neutral key lifecycle operations. */
@RestController
@RequestMapping("/management/v1/keys")
@CrossOrigin(originPatterns = "*")
@PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
public class KeyManagementCoordinatorController {
    private final SigningKeyService service;
    private final IssuerService identities;
    private final PrivateKeyImportService privateKeys;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.heidiverse.heidi.entity.data.repository.IssuingSubcaRepository issuingSubcas;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.heidiverse.heidi.entity.service.LocalDevelopmentTrustSeed developmentSeed;

    @GetMapping("/capabilities")
    public java.util.Map<String, Boolean> capabilities() {
        return java.util.Map.of("developmentCertificates", developmentSeed != null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public KeyManagementCoordinatorController(
            SigningKeyService service,
            IssuerService identities,
            PrivateKeyImportService privateKeys) {
        this.service = service;
        this.identities = identities;
        this.privateKeys = privateKeys;
    }

    /** Test and embedded callers using the former constructor keep JWK-only behavior. */
    public KeyManagementCoordinatorController(SigningKeyService service, IssuerService identities) {
        this(service, identities, new PrivateKeyImportService());
    }

    @GetMapping
    public List<PlatformKeyResponse> findAll(@RequestParam(required = false) String tenantId) {
        var resolvedTenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        return responses(resolvedTenantId);
    }

    @GetMapping("/global")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public List<PlatformKeyResponse> findAllGlobal() {
        return responses(null);
    }

    @PostMapping
    public PlatformKeyResponse create(
            @RequestParam(required = false) String tenantId,
            @Valid @RequestBody PlatformKeyRequest request) {
        return createInScope(JwtUtils.validateAndResolveTenantId(tenantId), request);
    }

    @PostMapping("/global")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public PlatformKeyResponse createGlobal(@Valid @RequestBody PlatformKeyRequest request) {
        return createInScope(null, request);
    }

    private PlatformKeyResponse createInScope(String resolvedTenantId, PlatformKeyRequest request) {
        var usage = request.usage();
        if (usage == SigningKeyUsage.KEY_AGREEMENT && !"ES256".equals(request.algorithm())) {
            throw new IllegalArgumentException("Key agreement requires a P-256 key (ES256)");
        }
        if (usage == SigningKeyUsage.UNWRAP && !request.algorithm().startsWith("RS")) {
            throw new IllegalArgumentException("Key unwrapping requires an RSA key");
        }
        var usages = java.util.Set.of(usage);
        var material = request.privateKey();
        var format = request.privateKeyFormat();
        if ((material == null || material.isBlank())
                && request.privateJwk() != null && !request.privateJwk().isBlank()) {
            material = request.privateJwk();
            format = org.heidiverse.heidi.entity.model.issuer.PrivateKeyFormat.JWK;
        }
        if (material == null || material.isBlank()) {
            service.create(resolvedTenantId, request.keyId(), usages, request.algorithm(),
                    request.providerId(), null);
        } else {
            var imported = privateKeys.read(
                    format, material, request.privateKeyPassword(), request.algorithm(), request.keyId());
            service.importKey(resolvedTenantId, request.keyId(), usages, request.algorithm(),
                    request.providerId(), imported.privateJwk());
        }
        return findOne(resolvedTenantId, request.keyId());
    }

    private List<PlatformKeyResponse> responses(String tenantId) {
        return service.keys(tenantId).stream()
                .filter(key -> issuingSubcas == null || !issuingSubcas.existsByKeyId(key.getId()))
                .map(this::response).toList();
    }

    @PostMapping("/{keyId}/rotate")
    public PlatformKeyResponse prepareRotation(
            @PathVariable String keyId,
            @RequestParam(required = false) String tenantId) {
        var resolvedTenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        var key = service.keys(resolvedTenantId).stream()
                .filter(candidate -> candidate.getLogicalKeyId().equals(keyId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Platform key not found: " + keyId));
        service.prepareRotation(resolvedTenantId, key.getId());
        return findOne(resolvedTenantId, keyId);
    }

    @PostMapping("/global/{keyId}/rotate")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public PlatformKeyResponse prepareGlobalRotation(@PathVariable String keyId) {
        var key = findKey(null, keyId);
        service.prepareRotation(null, key.getId());
        return findOne(null, keyId);
    }

    @PostMapping("/{keyId}/versions/{versionId}/activate")
    public PlatformKeyResponse activate(
            @PathVariable String keyId,
            @PathVariable UUID versionId,
            @RequestParam(required = false) String tenantId,
            @RequestBody(required = false) ActivationRequest request) {
        var resolvedTenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        var key = service.keys(resolvedTenantId).stream()
                .filter(candidate -> candidate.getLogicalKeyId().equals(keyId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Platform key not found: " + keyId));
        service.activate(resolvedTenantId, key.getId(), versionId,
                request == null || request.certificatesBySlot() == null
                        ? java.util.Map.of() : request.certificatesBySlot());
        // Publish consumer access before returning the newly active version.
        identities.reconcileSigningGrants();
        return findOne(resolvedTenantId, keyId);
    }

    @PostMapping("/global/{keyId}/versions/{versionId}/activate")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public PlatformKeyResponse activateGlobal(
            @PathVariable String keyId,
            @PathVariable UUID versionId,
            @RequestBody(required = false) ActivationRequest request) {
        var key = findKey(null, keyId);
        service.activate(null, key.getId(), versionId,
                request == null || request.certificatesBySlot() == null
                        ? java.util.Map.of() : request.certificatesBySlot());
        identities.reconcileSigningGrants();
        return findOne(null, keyId);
    }

    public record ActivationRequest(java.util.Map<UUID, UUID> certificatesBySlot) {}

    @PostMapping("/{keyId}/versions/{versionId}/retire")
    public PlatformKeyResponse retire(@PathVariable String keyId, @PathVariable UUID versionId,
            @RequestParam(required = false) String tenantId) {
        var tenant = JwtUtils.validateAndResolveTenantId(tenantId);
        service.retire(tenant, findOne(tenant, keyId).id(), versionId);
        identities.reconcileSigningGrants();
        return findOne(tenant, keyId);
    }

    @PostMapping("/global/{keyId}/versions/{versionId}/retire")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public PlatformKeyResponse retireGlobal(
            @PathVariable String keyId, @PathVariable UUID versionId) {
        var key = findKey(null, keyId);
        service.retire(null, key.getId(), versionId);
        identities.reconcileSigningGrants();
        return findOne(null, keyId);
    }

    @PostMapping("/{keyId}/versions/{versionId}/revoke")
    public PlatformKeyResponse revoke(@PathVariable String keyId, @PathVariable UUID versionId,
            @RequestParam(required = false) String tenantId) {
        var tenant = JwtUtils.validateAndResolveTenantId(tenantId);
        service.revoke(tenant, findOne(tenant, keyId).id(), versionId);
        identities.reconcileSigningGrants();
        return findOne(tenant, keyId);
    }

    @PostMapping("/global/{keyId}/versions/{versionId}/revoke")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public PlatformKeyResponse revokeGlobal(
            @PathVariable String keyId, @PathVariable UUID versionId) {
        var key = findKey(null, keyId);
        service.revoke(null, key.getId(), versionId);
        identities.reconcileSigningGrants();
        return findOne(null, keyId);
    }

    @GetMapping("/{keyId}/versions/{versionId}/activation-slots")
    public List<SigningKeyService.ActivationSlot> activationSlots(
            @PathVariable String keyId, @PathVariable UUID versionId,
            @RequestParam(required = false) String tenantId) {
        var tenant = JwtUtils.validateAndResolveTenantId(tenantId);
        return service.activationSlots(tenant, findOne(tenant, keyId).id(), versionId);
    }

    @GetMapping("/global/{keyId}/versions/{versionId}/activation-slots")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public List<SigningKeyService.ActivationSlot> globalActivationSlots(
            @PathVariable String keyId, @PathVariable UUID versionId) {
        return service.activationSlots(null, findOne(null, keyId).id(), versionId);
    }

    @org.springframework.web.bind.annotation.PutMapping("/{keyId}/rotation-policy")
    public PlatformKeyResponse updateRotationPolicy(
            @PathVariable String keyId, @RequestParam(required = false) String tenantId,
            @RequestBody org.heidiverse.heidi.entity.model.issuer.KeyRotationPolicy policy) {
        var tenant = JwtUtils.validateAndResolveTenantId(tenantId);
        service.updateRotationPolicy(tenant, findOne(tenant, keyId).id(), policy);
        return findOne(tenant, keyId);
    }

    @org.springframework.web.bind.annotation.PutMapping("/global/{keyId}/rotation-policy")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public PlatformKeyResponse updateGlobalRotationPolicy(
            @PathVariable String keyId,
            @RequestBody org.heidiverse.heidi.entity.model.issuer.KeyRotationPolicy policy) {
        var key = findKey(null, keyId);
        service.updateRotationPolicy(null, key.getId(), policy);
        return findOne(null, keyId);
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> delete(
            @PathVariable String keyId,
            @RequestParam(required = false) String tenantId) {
        var resolvedTenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        var key = service.keys(resolvedTenantId).stream()
                .filter(candidate -> candidate.getLogicalKeyId().equals(keyId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Platform key not found: " + keyId));
        service.delete(resolvedTenantId, key.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/global/{keyId}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteGlobal(@PathVariable String keyId) {
        service.delete(null, findKey(null, keyId).getId());
        return ResponseEntity.noContent().build();
    }

    private PlatformKeyResponse findOne(String tenantId, String keyId) {
        return service.keys(tenantId).stream()
                .filter(candidate -> candidate.getLogicalKeyId().equals(keyId))
                .findFirst()
                .map(this::response)
                .orElseThrow(() -> new IllegalStateException("Created platform key is missing: " + keyId));
    }

    private SigningKeyEntity findKey(String tenantId, String keyId) {
        return service.keys(tenantId).stream()
                .filter(candidate -> candidate.getLogicalKeyId().equals(keyId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Platform key not found: " + keyId));
    }

    private PlatformKeyResponse response(SigningKeyEntity key) {
        var versions = service.allVersions(key.getId()).stream().map(version -> version(key, version)).toList();
        return new PlatformKeyResponse(
                key.getId(), key.getLogicalKeyId(), key.getProviderId(), key.getActiveVersionId(),
                versions, key.getCreatedAt(), new org.heidiverse.heidi.entity.model.issuer.KeyRotationPolicy(
                        key.getRotationMode(), key.getRotationIntervalSeconds(),
                        key.getRotationGracePeriodSeconds()));
    }

    @PostMapping("/{keyId}/versions/{versionId}/certificates")
    public PlatformKeyResponse importCertificate(
            @PathVariable String keyId, @PathVariable UUID versionId,
            @RequestParam(required = false) String tenantId,
            @Valid @RequestBody KeyCertificateRequest request) {
        var tenant = JwtUtils.validateAndResolveTenantId(tenantId);
        var key = findOne(tenant, keyId);
        service.setCertificateChain(tenant, key.id(), versionId, request.certificateChain(),
                request.profile(), SigningCertificateSource.IMPORTED, request.trustSystem());
        return findOne(tenant, keyId);
    }

    @PostMapping("/global/{keyId}/versions/{versionId}/certificates")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public PlatformKeyResponse importGlobalCertificate(
            @PathVariable String keyId, @PathVariable UUID versionId,
            @Valid @RequestBody KeyCertificateRequest request) {
        var key = findOne(null, keyId);
        service.setCertificateChain(null, key.id(), versionId, request.certificateChain(),
                request.profile(), SigningCertificateSource.IMPORTED, request.trustSystem());
        return findOne(null, keyId);
    }

    @io.swagger.v3.oas.annotations.Operation(
            summary = "Remove a signing certificate",
            description = "Rejects assigned certificates. Never-used certificates are deleted; previously used certificates are retained as retired audit records.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Certificate removed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Certificate is assigned")
    @DeleteMapping("/{keyId}/versions/{versionId}/certificates/{certificateId}")
    public ResponseEntity<Void> removeCertificate(
            @PathVariable String keyId,
            @PathVariable UUID versionId,
            @PathVariable UUID certificateId,
            @RequestParam(required = false) String tenantId) {
        var tenant = JwtUtils.validateAndResolveTenantId(tenantId);
        service.removeCertificate(tenant, findOne(tenant, keyId).id(), versionId, certificateId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/global/{keyId}/versions/{versionId}/certificates/{certificateId}")
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Remove a global signing certificate",
            description = "Rejects assigned certificates. Never-used certificates are deleted; previously used certificates are retained as retired audit records.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Certificate removed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Certificate is assigned")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<Void> removeGlobalCertificate(
            @PathVariable String keyId,
            @PathVariable UUID versionId,
            @PathVariable UUID certificateId) {
        service.removeCertificate(null, findOne(null, keyId).id(), versionId, certificateId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{keyId}/versions/{versionId}/csr", produces = org.springframework.http.MediaType.TEXT_PLAIN_VALUE)
    public String createCsr(@PathVariable String keyId, @PathVariable UUID versionId,
            @RequestParam(required = false) String tenantId,
            @RequestBody org.heidiverse.heidi.shared.signing.SigningCsrRequest request) {
        var tenant = JwtUtils.validateAndResolveTenantId(tenantId);
        return service.createCsr(tenant, findOne(tenant, keyId).id(), versionId, request);
    }

    @PostMapping(value = "/global/{keyId}/versions/{versionId}/csr", produces = org.springframework.http.MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public String createGlobalCsr(@PathVariable String keyId, @PathVariable UUID versionId,
            @RequestBody org.heidiverse.heidi.shared.signing.SigningCsrRequest request) {
        return service.createCsr(null, findOne(null, keyId).id(), versionId, request);
    }

    /** A direct navigation preserves the browser's download user gesture. */
    @GetMapping(value = "/{keyId}/versions/{versionId}/csr", produces = org.springframework.http.MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> downloadCsr(@PathVariable String keyId, @PathVariable UUID versionId,
            @RequestParam(required = false) String tenantId, @RequestParam String subject,
            @RequestParam(required = false) List<String> dnsName,
            @RequestParam(required = false) List<String> uriName) {
        var tenant = JwtUtils.validateAndResolveTenantId(tenantId);
        var request = new org.heidiverse.heidi.shared.signing.SigningCsrRequest(
                subject, dnsName == null ? List.of() : dnsName, uriName == null ? List.of() : uriName);
        var pem = service.createCsr(tenant, findOne(tenant, keyId).id(), versionId, request);
        var disposition = org.springframework.http.ContentDisposition.attachment()
                .filename(keyId + ".csr.pem").build();
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pem);
    }

    @GetMapping(value = "/global/{keyId}/versions/{versionId}/csr", produces = org.springframework.http.MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<String> downloadGlobalCsr(
            @PathVariable String keyId, @PathVariable UUID versionId,
            @RequestParam String subject,
            @RequestParam(required = false) List<String> dnsName,
            @RequestParam(required = false) List<String> uriName) {
        var request = new org.heidiverse.heidi.shared.signing.SigningCsrRequest(
                subject, dnsName == null ? List.of() : dnsName, uriName == null ? List.of() : uriName);
        var pem = service.createCsr(null, findOne(null, keyId).id(), versionId, request);
        var disposition = org.springframework.http.ContentDisposition.attachment()
                .filename(keyId + ".csr.pem").build();
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pem);
    }

    private PlatformKeyResponse.Version version(SigningKeyEntity key, SigningKeyVersionEntity version) {
        var certificates = service.certificates(key.getTenantId(), key.getId(), version.getId()).stream()
                .map(certificate -> new PlatformKeyResponse.Certificate(certificate.getId(),
                        certificate.getProfile(), certificate.getSource(), certificate.getTrustSystem(),
                        certificate.getCertificateChain(), certificate.getNotBefore(), certificate.getNotAfter(),
                        certificate.getFirstUsedAt(), certificate.getRetiredAt(), certificate.getEudiLeafProfile()))
                .toList();
        return new PlatformKeyResponse.Version(
                version.getId(), version.getVersion(), version.getAlgorithm(), version.getPublicJwk(),
                version.getStatus(), certificates, version.getCreatedAt(), version.getUsages(), version.getPreviousUntil());
    }
}
