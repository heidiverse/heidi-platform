// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.heidiverse.heidi.entity.data.repository.IntegrationRepository;
import org.heidiverse.heidi.entity.model.entity.IntegrationEntity;
import org.heidiverse.heidi.entity.model.exceptions.IntegrationNotFoundException;
import org.heidiverse.heidi.entity.model.integration.IntegrationPayload;
import org.heidiverse.heidi.entity.model.integration.IntegrationScope;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class IntegrationServiceTest {

    @Mock private IntegrationRepository integrationRepository;

    @InjectMocks private IntegrationService integrationService;

    @Test
    void createIntegrationReturnsPersistedEntityId() {
        IntegrationPayload payload =
                new IntegrationPayload(
                        "Test integration",
                        List.of("credential-a"),
                        List.of(IntegrationScope.ISSUE),
                        false);

        UUID persistedId = UUID.randomUUID();
        ArgumentCaptor<IntegrationEntity> entityCaptor =
                ArgumentCaptor.forClass(IntegrationEntity.class);

        when(integrationRepository.save(any(IntegrationEntity.class)))
                .thenAnswer(
                        invocation -> {
                            IntegrationEntity entity = invocation.getArgument(0);
                            entity.setId(persistedId);
                            return entity;
                        });

        var result = integrationService.createIntegration(payload, "ubique");

        verify(integrationRepository).save(entityCaptor.capture());
        IntegrationEntity savedEntity = entityCaptor.getValue();

        assertEquals(persistedId, result.id());
        assertEquals("ubique", result.tenantId());
        assertFalse(result.isPublic());
        assertTrue(savedEntity.getApiKey() != null && !savedEntity.getApiKey().isBlank());
        assertEquals("Test integration", savedEntity.getDisplayName());
        assertEquals(List.of("credential-a"), savedEntity.getCredentialIdentifiers());
    }

    @Test
    void findByIdThrowsIntegrationNotFoundExceptionWhenEntityIsMissing() {
        UUID missingId = UUID.randomUUID();
        when(integrationRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(
                IntegrationNotFoundException.class, () -> integrationService.findById(missingId));
    }
}
