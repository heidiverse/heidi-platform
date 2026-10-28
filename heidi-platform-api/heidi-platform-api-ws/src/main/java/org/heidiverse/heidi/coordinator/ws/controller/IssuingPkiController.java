// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.ws.controller;

import java.util.Base64;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import org.heidiverse.heidi.entity.model.entity.IssuingSubcaEntity;
import org.heidiverse.heidi.entity.service.IssuingPkiService;
import org.heidiverse.heidi.entity.service.SigningKeyService;
import org.heidiverse.heidi.entity.service.utils.JwtUtils;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/management/v1/issuing-pki")
@PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
public class IssuingPkiController {
    private final IssuingPkiService service;
    private final SigningKeyService keys;

    public IssuingPkiController(IssuingPkiService service, SigningKeyService keys) {
        this.service = service;
        this.keys = keys;
    }

    @GetMapping
    public List<IssuingSubcaEntity> list(@RequestParam(required = false) String tenantId) {
        return service.list(JwtUtils.validateAndResolveTenantId(tenantId));
    }

    @PostMapping
    public IssuingSubcaEntity create(@RequestParam(required = false) String tenantId,
            @RequestBody CreateRequest request) {
        return service.create(JwtUtils.validateAndResolveTenantId(tenantId), request.subjectDn(),
                request.algorithm(), request.providerId(), request.parentCaId());
    }

    @GetMapping(value = "/{id}/csr", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> csr(@RequestParam(required = false) String tenantId,
            @PathVariable UUID id) {
        var pem = service.csr(JwtUtils.validateAndResolveTenantId(tenantId), id);
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=subca-" + id + ".csr.pem")
                .body(pem);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a managed CA branch",
            description = "Hides the selected CA and its descendants. Issued certificates, keys, trust anchors and public certificate URLs remain available.")
    public void deleteChain(@RequestParam(required = false) String tenantId,
            @PathVariable UUID id) {
        service.deleteChain(JwtUtils.validateAndResolveTenantId(tenantId), id);
    }

    @PostMapping("/{id}/self-sign")
    public IssuingSubcaEntity selfSign(@RequestParam(required = false) String tenantId,
            @PathVariable UUID id, @RequestBody SelfSignRequest request) {
        return service.selfSign(JwtUtils.validateAndResolveTenantId(tenantId), id,
                request.pathLengthMode(), request.pathLength());
    }

    @PostMapping("/{id}/certify")
    public IssuingSubcaEntity certify(@RequestParam(required = false) String tenantId,
            @PathVariable UUID id) {
        return service.certify(JwtUtils.validateAndResolveTenantId(tenantId), id);
    }

    @PostMapping("/{id}/certificate-chain")
    public IssuingSubcaEntity importChain(@RequestParam(required = false) String tenantId,
            @PathVariable UUID id, @RequestBody ChainRequest request) {
        return service.importChain(JwtUtils.validateAndResolveTenantId(tenantId), id,
                request.certificateChain());
    }

    @PostMapping("/{id}/leaves")
    public UUID issueLeaf(@RequestParam(required = false) String tenantId,
            @PathVariable UUID id, @RequestBody LeafRequest request) {
        var tenant = JwtUtils.validateAndResolveTenantId(tenantId);
        var key = keys.keys(tenant).stream()
                .filter(candidate -> candidate.getLogicalKeyId().equals(request.keyId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Key not found"));
        return service.issueLeaf(tenant, id, key.getId(), request.versionId(),
                request.subjectDn(), request.profile(), request.purpose(), request.csr());
    }

    public record CreateRequest(String subjectDn, String algorithm, Integer providerId, UUID parentCaId) {}
    public record ChainRequest(List<String> certificateChain) {}
    public record SelfSignRequest(IssuingPkiService.PathLengthMode pathLengthMode, Integer pathLength) {}
    public record LeafRequest(String keyId, UUID versionId, String subjectDn,
            IssuingPkiService.LeafProfile profile,
            org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile purpose, String csr) {}
}
