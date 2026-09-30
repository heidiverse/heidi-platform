// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import org.heidiverse.heidi.entity.model.entity.ProofSchemeEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProofSchemeRepository
        extends JpaRepository<ProofSchemeEntity, Integer>,
                JpaSpecificationExecutor<ProofSchemeEntity> {
    Optional<ProofSchemeEntity> findByUuidAndArchivedFalse(UUID uuid);

    Optional<ProofSchemeEntity> findByUuid(UUID uuid);

    List<ProofSchemeEntity> findAllByArchivedFalse();

    List<ProofSchemeEntity> findAllByTenantIdAndArchivedFalse(String tenantId);
}
