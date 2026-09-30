// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.heidiverse.heidi.entity.model.issuer.IssuerOperationConfigurationResponse;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.service.IdentityFederationService;
import org.heidiverse.heidi.entity.service.IssuerOperationConfigurationService;
import org.heidiverse.heidi.entity.service.IssuerService;
import org.heidiverse.heidi.entity.service.StatusListService;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class PlatformInternalIdentityControllerTest {
    @Test
    void forwardsIssuanceProfileToOperationConfigurationService() {
        var issuerService = mock(IssuerService.class);
        var operationService = mock(IssuerOperationConfigurationService.class);
        var response = new IssuerOperationConfigurationResponse(
                7, IssuerTrustSystem.EUDI, "operation", 1,
                new ObjectMapper().createObjectNode().put("policy", "eudi"));
        when(operationService.getForIssuer(
                        "issuer", IssuerTrustSystem.EUDI, "operation", "EUDI_ISSUANCE_2026_1"))
                .thenReturn(Optional.of(response));
        var controller = new PlatformInternalIdentityController(
                issuerService, operationService, mock(StatusListService.class),
                mock(IdentityFederationService.class));

        var result = controller.operationConfiguration(
                "issuer", "operation", IssuerTrustSystem.EUDI, "EUDI_ISSUANCE_2026_1");

        assertEquals(response, result.getBody());
        verify(operationService).getForIssuer(
                "issuer", IssuerTrustSystem.EUDI, "operation", "EUDI_ISSUANCE_2026_1");
    }
}
