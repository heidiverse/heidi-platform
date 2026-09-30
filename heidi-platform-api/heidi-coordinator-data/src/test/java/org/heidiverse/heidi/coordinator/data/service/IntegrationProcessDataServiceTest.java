// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.data.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.heidiverse.heidi.coordinator.data.repository.IntegrationProcessRepository;
import org.heidiverse.heidi.coordinator.model.entity.IntegrationProcessEntity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class IntegrationProcessDataServiceTest {

    private static final UUID PROCESS_ID = UUID.randomUUID();

    @Mock private IntegrationProcessRepository repository;

    @InjectMocks private IntegrationProcessDataService service;

    @Test
    void saveRequiresTenantOwnership() {
        var process = new IntegrationProcessEntity();

        assertThatThrownBy(() -> service.save(process))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Integration process tenantId is required");

        verify(repository, org.mockito.Mockito.never()).save(process);
    }

    @Test
    void saveDelegatesTenantOwnedProcess() {
        var process = new IntegrationProcessEntity();
        process.setTenantId("tenant-1");
        when(repository.save(process)).thenReturn(process);

        assertThat(service.save(process)).isSameAs(process);

        verify(repository).save(process);
    }

    @Test
    void getByIdReturnsProcess() {
        var process = new IntegrationProcessEntity();
        when(repository.findById(PROCESS_ID)).thenReturn(Optional.of(process));

        assertThat(service.getById(PROCESS_ID)).isSameAs(process);
    }

    @Test
    void getByIdForUpdateUsesLockedLookup() {
        var process = new IntegrationProcessEntity();
        when(repository.findByProcessId(PROCESS_ID)).thenReturn(Optional.of(process));

        assertThat(service.getByIdForUpdate(PROCESS_ID)).isSameAs(process);
    }

    @Test
    void getByClientInteractionTokenHashReturnsProcess() {
        var process = new IntegrationProcessEntity();
        when(repository.findByClientInteractionTokenHash("hash"))
                .thenReturn(Optional.of(process));

        assertThat(service.getByClientInteractionTokenHash("hash")).isSameAs(process);
    }

    @Test
    void missingProcessIsNotFound() {
        when(repository.findById(PROCESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(PROCESS_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void missingInteractionIsNotFound() {
        when(repository.findByClientInteractionTokenHash("hash"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByClientInteractionTokenHash("hash"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(404);
    }
}
