// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.data.service;

import org.heidiverse.heidi.coordinator.data.repository.ProtocolProcessRepository;
import org.heidiverse.heidi.coordinator.model.entity.ProtocolProcessEntity;

import tools.jackson.databind.JsonNode;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

@Service
public class ProtocolProcessDataService {

    private final ProtocolProcessRepository processRepository;

    public ProtocolProcessDataService(final ProtocolProcessRepository processRepository) {
        this.processRepository = processRepository;
    }

    @Transactional
    public void createProcess(String connectionId, JsonNode processData, String protocolType) {
        var processEntity = new ProtocolProcessEntity();
        processEntity.setConnectionId(connectionId);
        processEntity.setProcessPayload(processData);
        processEntity.setProtocolType(protocolType);
        processRepository.save(processEntity);
    }

    @Transactional
    public ProtocolProcessEntity getProcessData(String connectionId) {
        return processRepository.findByConnectionId(connectionId);
    }

    @Transactional
    public String getProtocolType(String connectionId) {
        return processRepository.findByConnectionId(connectionId).getProtocolType();
    }

    @Transactional
    public void deleteProcess(String connectionId) {
        processRepository.deleteByConnectionId(connectionId);
    }
}
