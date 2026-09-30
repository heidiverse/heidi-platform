// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.ws;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.heidiverse.heidi.issuer.model.FlowVariant;
import org.heidiverse.heidi.issuer.model.api.CredentialOfferResponse;
import org.heidiverse.heidi.issuer.model.entity.IssuanceSessionEntity;
import org.heidiverse.heidi.issuer.service.IssuanceService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CoordinatorIntegrationControllerTest {

    @Test
    void servesOnlyCanonicalCredentialOfferPath() throws Exception {
        var service = mock(IssuanceService.class);
        when(service.createOffer(eq("issuer"), eq(FlowVariant.C), any()))
                .thenReturn(mock(CredentialOfferResponse.class));
        when(service.status("connection", FlowVariant.C))
                .thenReturn(mock(IssuanceSessionEntity.class));
        var mvc = MockMvcBuilders.standaloneSetup(
                        new CoordinatorIntegrationController(service))
                .build();
        var request = post("/internal/issuer/v1/issuer/c/credential-offer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"token\"}");

        mvc.perform(request).andExpect(status().isOk());
        mvc.perform(post("/issuer/c/credential-offer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/issuer/v1/c/connection/connectionStatus"))
                .andExpect(status().isOk());
        mvc.perform(get("/c/connection/connectionStatus"))
                .andExpect(status().isNotFound());
    }
}
