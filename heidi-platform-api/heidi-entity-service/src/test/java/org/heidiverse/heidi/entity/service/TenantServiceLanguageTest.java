// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.heidiverse.heidi.entity.data.repository.IssuerDefinitionRepository;
import org.heidiverse.heidi.entity.data.repository.TenantRepository;
import org.heidiverse.heidi.entity.data.service.TrustRegistryDataService;
import org.heidiverse.heidi.entity.model.entity.TenantEntity;
import org.heidiverse.heidi.entity.model.tenant.TenantRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TenantServiceLanguageTest {

    @Test
    void appliesDeploymentLanguageToNewTenant() {
        var repository = mock(TenantRepository.class);
        when(repository.findById("tenant-a")).thenReturn(Optional.empty());
        var service = service(repository, "de-ch");

        service.upsertTenant("tenant-a", new TenantRequest());

        var saved = ArgumentCaptor.forClass(TenantEntity.class);
        verify(repository).save(saved.capture());
        assertEquals(List.of("de-CH"), saved.getValue().getTranslations());
        assertEquals("de-CH", saved.getValue().getDefaultLanguage());
    }

    @Test
    void preservesExistingTenantLanguage() {
        var tenant = new TenantEntity();
        tenant.setTranslations(List.of("fr"));
        tenant.setDefaultLanguage("fr");
        var repository = mock(TenantRepository.class);
        when(repository.findById("tenant-a")).thenReturn(Optional.of(tenant));
        var service = service(repository, "de");

        service.upsertTenant("tenant-a", new TenantRequest());

        assertEquals(List.of("fr"), tenant.getTranslations());
        assertEquals("fr", tenant.getDefaultLanguage());
    }

    private static TenantService service(TenantRepository repository, String defaultLanguage) {
        return new TenantService(
                repository,
                mock(IssuerDefinitionRepository.class),
                mock(RPRegistrarService.class),
                mock(CertificateService.class),
                mock(TrustRegistryDataService.class),
                mock(SigningProviderService.class),
                mock(SigningKeyService.class),
                defaultLanguage);
    }
}
