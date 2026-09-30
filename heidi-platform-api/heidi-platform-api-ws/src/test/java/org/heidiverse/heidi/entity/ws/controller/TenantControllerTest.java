// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import org.heidiverse.heidi.entity.model.entity.TenantEntity;
import org.heidiverse.heidi.entity.service.TenantService;
import org.heidiverse.heidi.entity.service.WalletCatalogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantControllerTest {
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bootstrapsTenantFromAuthenticatedCompanyId() throws Exception {
        authenticate("company-a");
        var tenants = mock(TenantService.class);
        when(tenants.getTenantIncludingDeleted("company-a")).thenReturn(Optional.empty());
        var mvc = MockMvcBuilders
                .standaloneSetup(new TenantController(tenants, mock(WalletCatalogService.class)))
                .build();

        mvc.perform(post("/management/v1/tenants/bootstrap")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(tenants).upsertTenant(eq("company-a"), any());
    }

    @Test
    void doesNotModifyAnExistingTenant() throws Exception {
        authenticate("company-a");
        var tenants = mock(TenantService.class);
        when(tenants.getTenantIncludingDeleted("company-a"))
                .thenReturn(Optional.of(new TenantEntity()));
        var mvc = MockMvcBuilders
                .standaloneSetup(new TenantController(tenants, mock(WalletCatalogService.class)))
                .build();

        mvc.perform(post("/management/v1/tenants/bootstrap")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(tenants, never()).upsertTenant(any(), any());
    }

    @Test
    void rejectsBootstrapWhenCompanyIdIsMissing() throws Exception {
        authenticate(null);
        var tenants = mock(TenantService.class);
        var mvc = MockMvcBuilders
                .standaloneSetup(new TenantController(tenants, mock(WalletCatalogService.class)))
                .build();

        mvc.perform(post("/management/v1/tenants/bootstrap")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(tenants, never()).getTenantIncludingDeleted(any());
    }

    private static void authenticate(String companyId) {
        var builder = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("admin")
                .claim("permissions", java.util.List.of("SUPER_ADMIN"));
        if (companyId != null) {
            builder.claim("companyId", companyId);
        }
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(builder.build()));
    }
}
