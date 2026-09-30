// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.ws.controller;

import jakarta.validation.Valid;
import org.heidiverse.heidi.entity.model.signing.SigningProviderConnectionResponse;
import org.heidiverse.heidi.entity.model.signing.SigningProviderRequest;
import org.heidiverse.heidi.entity.model.signing.SigningProviderResponse;
import org.heidiverse.heidi.entity.service.SigningProviderService;
import org.heidiverse.heidi.entity.service.utils.JwtUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** UI-facing tenant boundary for signing backend configuration. */
@RestController
@RequestMapping("/management/v1/signing-providers")
@CrossOrigin(originPatterns = "*")
@PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
public class SigningProviderCoordinatorController {
    private final SigningProviderService service;

    public SigningProviderCoordinatorController(SigningProviderService service) {
        this.service = service;
    }

    @GetMapping
    public List<SigningProviderResponse> findAll(@RequestParam(required = false) String tenantId) {
        return service.findAll(JwtUtils.validateAndResolveTenantId(tenantId));
    }

    @GetMapping("/global")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public List<SigningProviderResponse> findGlobal() {
        return service.findAll(null);
    }

    @PostMapping
    public SigningProviderResponse create(
            @RequestParam(required = false) String tenantId,
            @Valid @RequestBody SigningProviderRequest request) {
        return service.create(JwtUtils.validateAndResolveTenantId(tenantId), request);
    }

    @PostMapping("/check-connection")
    public SigningProviderConnectionResponse checkConnection(
            @Valid @RequestBody SigningProviderRequest request) {
        return service.checkConnection(request);
    }

    @PostMapping("/register-client/{client}")
    public SigningProviderConnectionResponse registerClient(
            @PathVariable String client,
            @Valid @RequestBody SigningProviderRequest request) {
        return service.registerClient(request, client);
    }

    @GetMapping("/{providerId}/connection")
    public SigningProviderConnectionResponse connection(
            @PathVariable int providerId,
            @RequestParam(required = false) String tenantId) {
        return service.connection(JwtUtils.validateAndResolveTenantId(tenantId), providerId);
    }

    @PostMapping("/{providerId}/register-client/{client}")
    public SigningProviderConnectionResponse registerPersistedClient(
            @PathVariable int providerId,
            @PathVariable String client,
            @RequestParam(required = false) String tenantId) {
        return service.registerClient(
                JwtUtils.validateAndResolveTenantId(tenantId), providerId, client);
    }

    @PostMapping("/{providerId}/refresh")
    public SigningProviderResponse refresh(
            @PathVariable int providerId,
            @RequestParam(required = false) String tenantId) {
        return service.refresh(JwtUtils.validateAndResolveTenantId(tenantId), providerId);
    }

    @PostMapping("/global/{providerId}/refresh")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public SigningProviderResponse refreshGlobal(@PathVariable int providerId) {
        return service.refresh(null, providerId);
    }

    @GetMapping("/global/{providerId}/connection")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public SigningProviderConnectionResponse connectionGlobal(@PathVariable int providerId) {
        return service.connection(null, providerId);
    }

    @PostMapping("/global/{providerId}/register-client/{client}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public SigningProviderConnectionResponse registerClientGlobal(
            @PathVariable int providerId, @PathVariable String client) {
        return service.registerClient(null, providerId, client);
    }

    @PostMapping("/global")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public SigningProviderResponse createGlobal(@Valid @RequestBody SigningProviderRequest request) {
        return service.createGlobal(request);
    }

    @DeleteMapping("/{providerId}")
    public void delete(
            @PathVariable int providerId,
            @RequestParam(required = false) String tenantId) {
        service.delete(JwtUtils.validateAndResolveTenantId(tenantId), providerId);
    }

    @DeleteMapping("/global/{providerId}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public void deleteGlobal(@PathVariable int providerId) {
        service.delete(null, providerId);
    }
}
