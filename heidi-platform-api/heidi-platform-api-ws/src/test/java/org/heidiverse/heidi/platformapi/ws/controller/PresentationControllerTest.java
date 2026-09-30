// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.controller;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.heidiverse.heidi.coordinator.service.Oid4vpService;
import org.heidiverse.heidi.coordinator.ws.controller.PresentationController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PresentationControllerTest {

    private static final String SCHEMA_ID = "2f95f571-53fc-4a3a-96d7-0f1a162d29c9";

    private final MockMvc mockMvc =
            MockMvcBuilders.standaloneSetup(new PresentationController(mock(Oid4vpService.class)))
                    .build();

    @Test
    void exposesOnlyCanonicalRoute() throws Exception {
        mockMvc
                .perform(get("/interaction/v1/presentation/dcqlQuery/{schemaId}", SCHEMA_ID))
                .andExpect(status().isOk());

        mockMvc
                .perform(get("/public/v1/presentation/dcqlQuery/{schemaId}", SCHEMA_ID))
                .andExpect(status().isNotFound());
    }
}
