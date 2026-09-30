// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import jakarta.validation.Valid;
import org.heidiverse.heidi.entity.model.issuer.IssuerOperationConfigurationRequest;
import org.heidiverse.heidi.entity.model.issuer.IssuerOperationConfigurationResponse;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.service.IssuerOperationConfigurationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Platform-owned identity operation settings. */
@RestController
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class GlobalIdentityOperationController {

    private static final String TARGET = "/management/v1/identities/global/issuers/{issuerId}"
            + "/trust-systems/{trustSystem}/operations/{operation}/config";
    private final IssuerOperationConfigurationService operationService;

    public GlobalIdentityOperationController(IssuerOperationConfigurationService operationService) {
        this.operationService = operationService;
    }

    @GetMapping(TARGET)
    public ResponseEntity<IssuerOperationConfigurationResponse> get(
            @PathVariable int issuerId,
            @PathVariable IssuerTrustSystem trustSystem,
            @PathVariable String operation) {
        return operationService.getGlobal(issuerId, trustSystem, operation)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(TARGET)
    public ResponseEntity<IssuerOperationConfigurationResponse> update(
            @PathVariable int issuerId,
            @PathVariable IssuerTrustSystem trustSystem,
            @PathVariable String operation,
            @Valid @RequestBody IssuerOperationConfigurationRequest request) {
        return ResponseEntity.ok(
                operationService.updateGlobal(issuerId, trustSystem, operation, request));
    }

    @DeleteMapping(TARGET)
    public ResponseEntity<Void> delete(
            @PathVariable int issuerId,
            @PathVariable IssuerTrustSystem trustSystem,
            @PathVariable String operation) {
        operationService.deleteGlobal(issuerId, trustSystem, operation);
        return ResponseEntity.noContent().build();
    }
}
