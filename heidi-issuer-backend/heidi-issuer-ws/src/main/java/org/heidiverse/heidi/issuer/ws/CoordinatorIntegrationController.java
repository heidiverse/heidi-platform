// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.ws;

import org.heidiverse.heidi.issuer.model.FlowVariant;
import org.heidiverse.heidi.issuer.model.IssuanceStatus;
import org.heidiverse.heidi.issuer.model.api.*;
import org.heidiverse.heidi.issuer.service.IssuanceService;

import org.springframework.web.bind.annotation.*;

@RestController
public class CoordinatorIntegrationController {
    private final IssuanceService issuanceService;

    public CoordinatorIntegrationController(IssuanceService issuanceService) {
        this.issuanceService = issuanceService;
    }

    @PostMapping("/internal/issuer/v1/{issuerSlug}/{variantPath}/credential-offer")
    public CredentialOfferResponse credentialOffer(
            @PathVariable String issuerSlug,
            @PathVariable String variantPath,
            @RequestBody CredentialOfferRequest request) {
        return issuanceService.createOffer(
                issuerSlug, FlowVariant.fromPath(variantPath), request);
    }

    @GetMapping("/internal/issuer/v1/{variantPath}/{connectionId}/connectionStatus")
    public ConnectionStatus connectionStatus(
            @PathVariable String variantPath, @PathVariable String connectionId) {
        var session = issuanceService.status(connectionId, FlowVariant.fromPath(variantPath));
        return new ConnectionStatus(
                true,
                session.getStatus() == IssuanceStatus.ACCESS_TOKEN_ISSUED
                        || session.getStatus() == IssuanceStatus.CREDENTIAL_ISSUED);
    }

}
