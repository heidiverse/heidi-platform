// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import jakarta.validation.Valid;

import org.heidiverse.heidi.entity.model.statuslist.StatusListEntryRequest;
import org.heidiverse.heidi.entity.model.statuslist.StatusListEntryResponse;
import org.heidiverse.heidi.entity.model.statuslist.StatusListRequest;
import org.heidiverse.heidi.entity.model.statuslist.StatusListResponse;
import org.heidiverse.heidi.entity.service.StatusListService;
import org.heidiverse.heidi.entity.service.utils.JwtUtils;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/management/v1/status-lists")
@CrossOrigin(originPatterns = "*")
public class StatusListController {
    private static final String EDIT_ROLES =
            "hasAnyAuthority('EDITOR', 'ADMIN', 'MANAGER', 'SUPER_ADMIN')";

    private final StatusListService service;

    public StatusListController(StatusListService service) {
        this.service = service;
    }

    @GetMapping
    public List<StatusListResponse> findAll(@RequestParam(required = false) String tenantId) {
        return service.findAll(JwtUtils.validateAndResolveTenantId(tenantId));
    }

    @GetMapping("/{id}")
    public StatusListResponse find(
            @PathVariable UUID id, @RequestParam(required = false) String tenantId) {
        return service.find(JwtUtils.validateAndResolveTenantId(tenantId), id);
    }

    @PostMapping
    @PreAuthorize(EDIT_ROLES)
    public StatusListResponse create(
            @Valid @RequestBody StatusListRequest request,
            @RequestParam(required = false) String tenantId) {
        return service.create(JwtUtils.validateAndResolveTenantId(tenantId), request);
    }

    @GetMapping("/{id}/entries/{index}")
    public StatusListEntryResponse entry(
            @PathVariable UUID id,
            @PathVariable int index,
            @RequestParam(required = false) String tenantId) {
        return service.entry(JwtUtils.validateAndResolveTenantId(tenantId), id, index);
    }

    @PutMapping("/{id}/entries")
    @PreAuthorize(EDIT_ROLES)
    public StatusListEntryResponse setEntry(
            @PathVariable UUID id,
            @Valid @RequestBody StatusListEntryRequest request,
            @RequestParam(required = false) String tenantId) {
        return service.setEntry(
                JwtUtils.validateAndResolveTenantId(tenantId),
                id,
                request.index(),
                request.status());
    }

    @PutMapping("/{id}/publish")
    @PreAuthorize(EDIT_ROLES)
    public StatusListResponse publish(
            @PathVariable UUID id, @RequestParam(required = false) String tenantId) {
        return service.publish(JwtUtils.validateAndResolveTenantId(tenantId), id);
    }

    @PutMapping("/{id}/publish/switzerland")
    @PreAuthorize(EDIT_ROLES)
    public StatusListResponse publishToSwitzerland(
            @PathVariable UUID id, @RequestParam(required = false) String tenantId) {
        return service.publishSwiss(JwtUtils.validateAndResolveTenantId(tenantId), id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(EDIT_ROLES)
    public ResponseEntity<Void> delete(
            @PathVariable UUID id, @RequestParam(required = false) String tenantId) {
        service.delete(JwtUtils.validateAndResolveTenantId(tenantId), id);
        return ResponseEntity.noContent().build();
    }
}
