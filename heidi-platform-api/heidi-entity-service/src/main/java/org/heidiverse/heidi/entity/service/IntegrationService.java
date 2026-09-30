// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import org.heidiverse.heidi.entity.data.repository.IntegrationRepository;
import org.heidiverse.heidi.entity.model.entity.IntegrationEntity;
import org.heidiverse.heidi.entity.model.exceptions.IntegrationNotFoundException;
import org.heidiverse.heidi.entity.model.exceptions.UnauthorizedAccessException;
import org.heidiverse.heidi.entity.model.integration.IntegrationDetail;
import org.heidiverse.heidi.entity.model.integration.IntegrationOverviewEntry;
import org.heidiverse.heidi.entity.model.integration.IntegrationPayload;
import org.heidiverse.heidi.entity.model.integration.IntegrationScope;
import org.heidiverse.heidi.entity.model.user.UserRole;
import org.heidiverse.heidi.entity.service.utils.JwtUtils;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class IntegrationService {

    private final IntegrationRepository integrationRepository;

    public IntegrationService(IntegrationRepository integrationRepository) {
        this.integrationRepository = integrationRepository;
    }

    @Transactional
    public IntegrationDetail createIntegration(IntegrationPayload payload, String tenantId) {
        boolean isPublic = payload.isPublic() != null && payload.isPublic();

        IntegrationEntity entity =
                new IntegrationEntity(
                        null,
                        payload.displayName(),
                        payload.credentialIdentifiers(),
                        payload.scopes(),
                        UUID.randomUUID().toString(),
                        tenantId,
                        isPublic);
        IntegrationEntity savedEntity = integrationRepository.save(entity);
        return savedEntity.toDetail();
    }

    @Transactional
    public IntegrationDetail updateIntegration(UUID id, IntegrationPayload payload) {
        IntegrationEntity entity =
                integrationRepository
                        .findById(id)
                        .orElseThrow(() -> new IntegrationNotFoundException(id));
        validateTenantIdAccess(entity);

        entity.setDisplayName(payload.displayName());
        entity.setCredentialIdentifiers(payload.credentialIdentifiers());
        entity.setScopes(payload.scopes());
        if (payload.isPublic() != null) {
            entity.setPublic(payload.isPublic());
        }
        integrationRepository.save(entity);
        return entity.toDetail();
    }

    @Transactional
    public void deleteIntegration(UUID id) {
        IntegrationEntity entity =
                integrationRepository
                        .findById(id)
                        .orElseThrow(() -> new IntegrationNotFoundException(id));
        validateTenantIdAccess(entity);
        integrationRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<IntegrationOverviewEntry> findAllOverviewByTenantId(
            String tenantId, Boolean isPublic) {
        List<IntegrationEntity> entities;
        if (isPublic == null) {
            entities = integrationRepository.findAllByTenantId(tenantId);
        } else {
            entities = integrationRepository.findAllByTenantIdAndIsPublic(tenantId, isPublic);
        }

        return entities.stream().map(IntegrationEntity::toOverviewEntry).toList();
    }

    @Transactional(readOnly = true)
    public List<IntegrationOverviewEntry> findAllOverview(Boolean isPublic) {
        List<IntegrationEntity> entities;
        if (isPublic == null) {
            entities = integrationRepository.findAll();
        } else {
            entities = integrationRepository.findAllByIsPublic(isPublic);
        }

        return entities.stream().map(IntegrationEntity::toOverviewEntry).toList();
    }

    @Transactional(readOnly = true)
    public IntegrationDetail findById(UUID id) {
        IntegrationEntity entity =
                integrationRepository
                        .findById(id)
                        .orElseThrow(() -> new IntegrationNotFoundException(id));
        validateTenantIdAccess(entity);
        return entity.toDetail();
    }

    @Transactional(readOnly = true)
    public List<String> findCredentialsByApiKey(String apiKey) {
        return integrationRepository
                .findByApiKey(apiKey)
                .map(IntegrationEntity::getCredentialIdentifiers)
                .orElse(Collections.emptyList());
    }

    @Transactional(readOnly = true)
    public List<IntegrationScope> findScopesByApiKey(String apiKey) {
        return integrationRepository
                .findByApiKey(apiKey)
                .map(IntegrationEntity::getScopes)
                .orElse(Collections.emptyList());
    }

    @Transactional(readOnly = true)
    public String findTenantIdByApiKey(String apiKey) {
        return integrationRepository
                .findByApiKey(apiKey)
                .map(IntegrationEntity::getTenantId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public IntegrationDetail findPublicById(UUID id) {
        IntegrationEntity entity =
                integrationRepository
                        .findById(id)
                        .orElseThrow(() -> new IntegrationNotFoundException(id));
        if (!entity.isPublic()) {
            throw new UnauthorizedAccessException("Access denied: Integration is not public.");
        }
        return entity.toDetail();
    }

    private void validateTenantIdAccess(IntegrationEntity entity) {
        String tenantId = JwtUtils.getUserProfile().tenantId();
        boolean isSuperAdmin =
                JwtUtils.getUserProfile().permissions().contains(UserRole.SUPER_ADMIN);

        if (!isSuperAdmin
                && entity.getTenantId() != null
                && !entity.getTenantId().equals(tenantId)) {
            throw new UnauthorizedAccessException("Access denied: Tenant ID mismatch.");
        }
    }
}
