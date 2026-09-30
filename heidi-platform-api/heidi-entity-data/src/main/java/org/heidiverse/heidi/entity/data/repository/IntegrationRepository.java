// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import org.heidiverse.heidi.entity.model.entity.IntegrationEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IntegrationRepository extends JpaRepository<IntegrationEntity, UUID> {
    List<IntegrationEntity> findAllByTenantId(String tenantId);

    Optional<IntegrationEntity> findByApiKey(String apiKey);

    @Query("select i from IntegrationEntity i where i.tenantId = ?1 and i.isPublic = ?2")
    List<IntegrationEntity> findAllByTenantIdAndIsPublic(String tenantId, boolean isPublic);

    @Query("select i from IntegrationEntity i where i.isPublic = ?1")
    List<IntegrationEntity> findAllByIsPublic(boolean isPublic);
}
