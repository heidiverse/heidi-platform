// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.data.service;

import org.heidiverse.heidi.coordinator.data.repository.IntegrationProcessRepository;
import org.heidiverse.heidi.coordinator.model.entity.IntegrationProcessEntity;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.http.HttpStatus;

import java.util.UUID;

@Service
public class IntegrationProcessDataService {

    private final IntegrationProcessRepository repository;

    public IntegrationProcessDataService(IntegrationProcessRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public IntegrationProcessEntity save(IntegrationProcessEntity process) {
        if (process.getTenantId() == null || process.getTenantId().isBlank()) {
            throw new IllegalArgumentException("Integration process tenantId is required");
        }
        return repository.save(process);
    }

    @Transactional
    public IntegrationProcessEntity getById(UUID processId) {
        return repository
                .findById(processId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Process not found"));
    }

    @Transactional
    public IntegrationProcessEntity getByIdForUpdate(UUID processId) {
        return repository
                .findByProcessId(processId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Process not found"));
    }

    @Transactional
    public IntegrationProcessEntity getByClientInteractionTokenHash(String tokenHash) {
        return repository
                .findByClientInteractionTokenHash(tokenHash)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Interaction not found"));
    }
}
