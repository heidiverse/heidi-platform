// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import org.heidiverse.heidi.entity.model.tenant.TenantResponse;
import org.heidiverse.heidi.entity.service.TenantService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform-owned tenant data consumed by coordinator and verifier services. */
@RestController
public class PlatformInternalTenantController {

    private final TenantService tenantService;

    public PlatformInternalTenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping({
        "/internal/platform/v1/tenants/{tenantId}",
        "/public/v1/tenants/{tenantId}"
    })
    public ResponseEntity<TenantResponse> getTenant(@PathVariable String tenantId) {
        return tenantService.getTenant(tenantId)
                .map(TenantResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
