// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.ws.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.heidiverse.heidi.entity.model.issuer.CertificateChainRequest;
import org.heidiverse.heidi.entity.model.issuer.IssuerOperationConfigurationRequest;
import org.heidiverse.heidi.entity.model.issuer.IssuerOperationConfigurationResponse;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustConfigurationRequest;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustConfigurationResponse;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.issuer.PublicKeyFormat;
import org.heidiverse.heidi.entity.model.issuer.SwissVerificationQuery;
import org.heidiverse.heidi.entity.service.IssuerOperationConfigurationService;
import org.heidiverse.heidi.entity.service.IssuerService;
import org.heidiverse.heidi.entity.service.TenantService;
import org.heidiverse.heidi.entity.service.utils.JwtUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UI-facing identity boundary for trust and operation configuration. */
@RestController
@RequestMapping("/management/v1/identities")
@CrossOrigin(originPatterns = "*")
@PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
public class IdentityConfigurationCoordinatorController {
    private final IssuerService issuerService;
    private final TenantService tenantService;
    private final IssuerOperationConfigurationService operationConfigurationService;

    public IdentityConfigurationCoordinatorController(
            IssuerService issuerService,
            TenantService tenantService,
            IssuerOperationConfigurationService operationConfigurationService) {
        this.issuerService = issuerService;
        this.tenantService = tenantService;
        this.operationConfigurationService = operationConfigurationService;
    }

    @Operation(summary = "Get an identity operation configuration")
    @GetMapping("/issuers/{issuerId}/trust-systems/{trustSystem}/operations/{operation}/config")
    public ResponseEntity<IssuerOperationConfigurationResponse> getOperationConfiguration(
            @PathVariable int issuerId,
            @PathVariable IssuerTrustSystem trustSystem,
            @PathVariable String operation,
            @RequestParam(required = false) String tenantId) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        requireTenant(tenantId);
        return operationConfigurationService.getTenant(tenantId, issuerId, trustSystem, operation)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Update an identity operation configuration")
    @PutMapping("/issuers/{issuerId}/trust-systems/{trustSystem}/operations/{operation}/config")
    public ResponseEntity<IssuerOperationConfigurationResponse> updateOperationConfiguration(
            @PathVariable int issuerId,
            @PathVariable IssuerTrustSystem trustSystem,
            @PathVariable String operation,
            @RequestParam(required = false) String tenantId,
            @Valid @RequestBody IssuerOperationConfigurationRequest request) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        requireTenant(tenantId);
        return ResponseEntity.ok(
                operationConfigurationService.updateTenant(
                        tenantId, issuerId, trustSystem, operation, request));
    }

    @Operation(summary = "Delete an identity operation configuration")
    @DeleteMapping("/issuers/{issuerId}/trust-systems/{trustSystem}/operations/{operation}/config")
    public ResponseEntity<Void> deleteOperationConfiguration(
            @PathVariable int issuerId,
            @PathVariable IssuerTrustSystem trustSystem,
            @PathVariable String operation,
            @RequestParam(required = false) String tenantId) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        requireTenant(tenantId);
        operationConfigurationService.deleteTenant(tenantId, issuerId, trustSystem, operation);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get identity trust-system configuration")
    @GetMapping("/issuers/{issuerId}/trust-systems/{trustSystem}")
    public IssuerTrustConfigurationResponse getTrustConfiguration(
            @PathVariable int issuerId,
            @PathVariable IssuerTrustSystem trustSystem,
            @RequestParam(required = false) String tenantId) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        return issuerService.getIssuerTrustConfiguration(
                tenantId, issuerId, trustSystem, allowedIssuerIds(tenantId));
    }

    @Operation(summary = "Update identity trust-system configuration")
    @PutMapping("/issuers/{issuerId}/trust-systems/{trustSystem}")
    public IssuerTrustConfigurationResponse updateTrustConfiguration(
            @PathVariable int issuerId,
            @PathVariable IssuerTrustSystem trustSystem,
            @RequestParam(required = false) String tenantId,
            @RequestBody IssuerTrustConfigurationRequest request) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        return issuerService.updateIssuerTrustConfiguration(
                tenantId, issuerId, trustSystem, request, allowedIssuerIds(tenantId));
    }

    @Operation(summary = "Refresh Swiss identity statements")
    @PostMapping("/issuers/{issuerId}/trust-systems/Switzerland/refresh")
    public IssuerTrustConfigurationResponse refreshSwissTrustConfiguration(
            @PathVariable int issuerId,
            @RequestParam(required = false) String tenantId) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        return issuerService.refreshSwissTrustConfiguration(
                issuerId, allowedIssuerIds(tenantId));
    }

    @Operation(summary = "List Swiss verification query public statements")
    @GetMapping("/issuers/{issuerId}/trust-systems/Switzerland/vqps")
    public List<SwissVerificationQuery> listSwissVerificationQueries(
            @PathVariable int issuerId,
            @RequestParam(required = false) String tenantId) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        return issuerService.listSwissVerificationQueries(issuerId, allowedIssuerIds(tenantId));
    }

    @GetMapping("/global/issuers/{issuerId}/trust-systems/Switzerland/vqps")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public List<SwissVerificationQuery> listGlobalSwissVerificationQueries(
            @PathVariable int issuerId) {
        return issuerService.listSwissVerificationQueries(issuerId, globalIssuer(issuerId));
    }

    @Operation(summary = "Export an identity key's public key as JWK or PEM")
    @GetMapping("/issuers/{issuerId}/trust-systems/{trustSystem}/keys/{keyId}/public-key")
    public ResponseEntity<byte[]> exportPublicKey(
            @PathVariable int issuerId,
            @PathVariable IssuerTrustSystem trustSystem,
            @PathVariable String keyId,
            @RequestParam(defaultValue = "JWK") PublicKeyFormat format,
            @RequestParam(required = false) String tenantId) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        var body = issuerService.exportIdentityPublicKey(
                        issuerId, trustSystem, keyId, format, allowedIssuerIds(tenantId))
                .getBytes(StandardCharsets.UTF_8);
        var pem = format == PublicKeyFormat.PEM;
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(keyId + (pem ? ".pem" : ".jwk"))
                                .build().toString())
                .contentType(pem ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON)
                .body(body);
    }

    @Operation(summary = "Store an EUDI identity certificate chain")
    @PutMapping("/issuers/{issuerId}/trust-systems/EUDI/keys/{keyId}/certificate-chain")
    public IssuerTrustConfigurationResponse setEudiTrustCertificateChain(
            @PathVariable int issuerId,
            @PathVariable String keyId,
            @RequestParam(required = false) String tenantId,
            @Valid @RequestBody CertificateChainRequest request) {
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);
        return issuerService.setEudiTrustCertificateChain(
                issuerId, keyId, request, allowedIssuerIds(tenantId));
    }

    @GetMapping("/global/issuers/{issuerId}/trust-systems/{trustSystem}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public IssuerTrustConfigurationResponse getGlobalTrustConfiguration(
            @PathVariable int issuerId, @PathVariable IssuerTrustSystem trustSystem) {
        return issuerService.getIssuerTrustConfiguration(
                null, issuerId, trustSystem, globalIssuer(issuerId));
    }

    @PutMapping("/global/issuers/{issuerId}/trust-systems/{trustSystem}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public IssuerTrustConfigurationResponse updateGlobalTrustConfiguration(
            @PathVariable int issuerId,
            @PathVariable IssuerTrustSystem trustSystem,
            @RequestBody IssuerTrustConfigurationRequest request) {
        return issuerService.updateIssuerTrustConfiguration(
                null, issuerId, trustSystem, request, globalIssuer(issuerId));
    }

    @PostMapping("/global/issuers/{issuerId}/trust-systems/Switzerland/refresh")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public IssuerTrustConfigurationResponse refreshGlobalSwissTrustConfiguration(
            @PathVariable int issuerId) {
        return issuerService.refreshSwissTrustConfiguration(issuerId, globalIssuer(issuerId));
    }

    @GetMapping("/global/issuers/{issuerId}/trust-systems/{trustSystem}/keys/{keyId}/public-key")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<byte[]> exportGlobalPublicKey(
            @PathVariable int issuerId,
            @PathVariable IssuerTrustSystem trustSystem,
            @PathVariable String keyId,
            @RequestParam(defaultValue = "JWK") PublicKeyFormat format) {
        var body = issuerService.exportIdentityPublicKey(
                        issuerId, trustSystem, keyId, format, globalIssuer(issuerId))
                .getBytes(StandardCharsets.UTF_8);
        var pem = format == PublicKeyFormat.PEM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(keyId + (pem ? ".pem" : ".jwk")).build().toString())
                .contentType(pem ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON)
                .body(body);
    }

    @PutMapping("/global/issuers/{issuerId}/trust-systems/EUDI/keys/{keyId}/certificate-chain")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public IssuerTrustConfigurationResponse setGlobalEudiTrustCertificateChain(
            @PathVariable int issuerId,
            @PathVariable String keyId,
            @Valid @RequestBody CertificateChainRequest request) {
        return issuerService.setEudiTrustCertificateChain(
                issuerId, keyId, request, globalIssuer(issuerId));
    }

    private Set<Integer> globalIssuer(int issuerId) {
        var identity = issuerService.findIssuerById(issuerId)
                .orElseThrow(() -> new IllegalArgumentException("Identity not found: " + issuerId));
        if (identity.tenantId() != null) {
            throw new SecurityException("Identity is not platform-owned");
        }
        return Set.of(issuerId);
    }

    private Set<Integer> allowedIssuerIds(String tenantId) {
        return Set.copyOf(requireTenant(tenantId).getIssuerIds());
    }

    private org.heidiverse.heidi.entity.model.entity.TenantEntity requireTenant(String tenantId) {
        return tenantService.getTenant(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
    }
}
