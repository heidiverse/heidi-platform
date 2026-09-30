// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.heidiverse.heidi.coordinator.data.service.ProtocolProcessDataService;
import org.heidiverse.heidi.coordinator.model.oid4vci.Action;
import org.heidiverse.heidi.coordinator.model.oid4vci.ActionPayload;
import org.heidiverse.heidi.coordinator.model.oid4vci.PresentationData;
import org.heidiverse.heidi.coordinator.model.oid4vci.SignedData;
import org.heidiverse.heidi.coordinator.model.issuance.SignatureToken;
import org.heidiverse.heidi.coordinator.model.oidc4vp.AuthorizationRequestObject;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;

class ProcessOrchestrationServiceTest {

    @Test
    void presentationQrUsesAuthorizationRequestClientId() throws Exception {
        final var clientId = "decentralized_identifier:did:webvh:verifier.example";
        final var presentationData =
                new PresentationData(
                        "proof-scheme", null, false, null, "EUDI_PRESENTATION_2026_1");
        final var tokenSignatureService = mock(TokenSignatureService.class);
        final var oid4vpService = mock(Oid4vpService.class);
        final var processDataService = mock(ProtocolProcessDataService.class);
        final var service =
                new ProcessOrchestrationService(
                        tokenSignatureService,
                        processDataService,
                        new ObjectMapper(),
                        null,
                        oid4vpService,
                        null,
                        null,
                        null,
                        List.of());
        when(tokenSignatureService.verifyAndGetObject(
                        any(SignatureToken.class), eq(SignedData.class)))
                .thenReturn(
                        new SignedData(
                                ZonedDateTime.now(),
                                ZonedDateTime.now().plusMinutes(5),
                                new ActionPayload(Action.PRESENTATION, presentationData, null, false)));
        when(oid4vpService.getAuthorizationRequestObject(presentationData, false))
                .thenReturn(
                        new AuthorizationRequestObject(
                                clientId,
                                "https://verifier.example/v1/wallet/par/cross",
                                "https://verifier.example/v1/wallet/par/same",
                                "vp_token",
                                "direct_post",
                                null,
                                "nonce",
                                "state"));

        final var response = service.start(new SignatureToken("token"));

        final var expectedClientId =
                URLEncoder.encode(clientId, StandardCharsets.UTF_8);
        assertEquals(
                "?client_id=" + expectedClientId
                        + "&request_uri="
                        + URLEncoder.encode(
                                "https://verifier.example/v1/wallet/par/cross",
                                StandardCharsets.UTF_8),
                response.crossDevice().qrCodeDataPath());
    }
}
