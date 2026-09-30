// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.controller;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.heidiverse.heidi.coordinator.ws.controller.IntegrationProcessController;
import org.heidiverse.heidi.coordinator.ws.service.IntegrationProcessService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IntegrationProcessControllerTest {

    private final MockMvc mockMvc =
            MockMvcBuilders.standaloneSetup(
                            new IntegrationProcessController(mock(IntegrationProcessService.class)))
                    .build();

    @Test
    void doesNotExposeClientCompatibilityRoute() throws Exception {
        mockMvc
                .perform(get("/integration/v1/client/interaction"))
                .andExpect(status().isNotFound());
    }
}
