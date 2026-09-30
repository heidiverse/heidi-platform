// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeOverview;
import org.heidiverse.heidi.entity.model.entity.TenantEntity;
import org.heidiverse.heidi.entity.service.CredentialSchemeService;
import org.heidiverse.heidi.entity.service.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiRouteCompatibilityTest {

    @Test
    void servesOnlyCanonicalCredentialSchemaRoute() throws Exception {
        var service = mock(CredentialSchemeService.class);
        when(service.getOverview(any(), anyBoolean(), anyBoolean(), any(), anyBoolean()))
                .thenReturn(new CredentialSchemeOverview(List.of()));
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new CredentialSchemaController(service))
                .build();

        mvc.perform(get("/management/v1/credential-schemas/overview"))
                .andExpect(status().isOk());
        mvc.perform(get("/management/v1/issuance-schemas/overview"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v2/schema/overview"))
                .andExpect(status().isNotFound());
    }

    @Test
    void managementCredentialSchemaRequiresTenantAccess() throws Exception {
        var service = mock(CredentialSchemeService.class);
        var id = UUID.randomUUID();

        new CredentialSchemaController(service).getCredentialScheme(id);

        verify(service).findById(id, true);
    }

    @Test
    void exposesTenantWithoutLegacySigningConfiguration() throws Exception {
        var service = mock(TenantService.class);
        when(service.getTenant("local")).thenReturn(Optional.of(new TenantEntity()));
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new PlatformInternalTenantController(service))
                .build();

        mvc.perform(get("/internal/platform/v1/tenants/local"))
                .andExpect(status().isOk());
        mvc.perform(get("/public/v1/tenants/local"))
                .andExpect(status().isOk());
        mvc.perform(get("/public/v1/tenants/getVerifierSigningConfiguration")
                        .param("tenantId", "local"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/platform/v1/tenants/getVerifierSigningConfiguration")
                        .param("tenantId", "local"))
                .andExpect(status().isNotFound());
    }
}
