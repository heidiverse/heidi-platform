// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import org.heidiverse.heidi.entity.model.issuer.IssuerDefinitionResponse;
import org.heidiverse.heidi.entity.model.issuer.IssuerOverview;
import org.heidiverse.heidi.entity.service.IssuerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Browser-facing identity configuration. */
@RestController
@RequestMapping("/public/v1/identity")
@CrossOrigin(originPatterns = "*")
public class PublicIdentityController {

    private final IssuerService issuerService;

    public PublicIdentityController(IssuerService issuerService) {
        this.issuerService = issuerService;
    }

    @GetMapping("/overview")
    public ResponseEntity<IssuerOverview> getAllIssuers(
            @RequestParam(required = false, defaultValue = "true") boolean includeLogos) {
        IssuerOverview overview = issuerService.findAllIssuers();
        if (includeLogos) return ResponseEntity.ok(overview);

        var definitions = overview.issuerDefinitions().stream()
                .map(issuer -> new IssuerDefinitionResponse(
                        issuer.id(), issuer.slug(), null, issuer.displayName(), issuer.tenantId(),
                        issuer.defaultTrustSystem(), issuer.trustSystems(), issuer.customProfileName()))
                .toList();

        return ResponseEntity.ok(new IssuerOverview(definitions));
    }
}
