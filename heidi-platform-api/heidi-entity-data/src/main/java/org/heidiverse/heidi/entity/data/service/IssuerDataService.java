// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.service;

import org.heidiverse.heidi.entity.data.repository.IssuerDefinitionRepository;
import org.heidiverse.heidi.entity.model.entity.*;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class IssuerDataService {

    private final IssuerDefinitionRepository issuerDefinitionRepository;

    public IssuerDataService(final IssuerDefinitionRepository issuerDefinitionRepository) {
        this.issuerDefinitionRepository = issuerDefinitionRepository;
    }

    @Transactional
    public Optional<IssuerDefinitionEntity> findById(final int id) {
        return issuerDefinitionRepository.findByIdAndDeletedFalse(id);
    }

    @Transactional
    public List<IssuerDefinitionEntity> findAll() {
        return issuerDefinitionRepository.findAllByDeletedFalse();
    }

    @Transactional
    public List<IssuerDefinitionEntity> findGlobal() {
        return issuerDefinitionRepository.findAllByDeletedFalseAndTenantIdIsNull();
    }

    @Transactional
    public List<IssuerDefinitionEntity> findAvailableToTenant(String tenantId) {
        return issuerDefinitionRepository
                .findAllByDeletedFalseAndTenantIdIsNullOrDeletedFalseAndTenantId(tenantId);
    }

    public Optional<IssuerDefinitionEntity> findBySlug(String slug) {
        return issuerDefinitionRepository.findBySlug(slug);
    }

    @Transactional
    public IssuerDefinitionEntity save(final IssuerDefinitionEntity issuerDefinitionEntity) {
        return issuerDefinitionRepository.save(issuerDefinitionEntity);
    }

    @Transactional
    public boolean softDeleteById(final int id) {
        return issuerDefinitionRepository
                .findById(id)
                .map(
                        entity -> {
                            entity.setDeleted(true);
                            issuerDefinitionRepository.save(entity);
                            return true;
                        })
                .orElse(false);
    }
}
