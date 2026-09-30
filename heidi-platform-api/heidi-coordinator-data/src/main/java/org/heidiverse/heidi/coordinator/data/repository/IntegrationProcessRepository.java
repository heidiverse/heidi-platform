// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.data.repository;

import org.heidiverse.heidi.coordinator.model.entity.IntegrationProcessEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IntegrationProcessRepository
        extends JpaRepository<IntegrationProcessEntity, UUID> {

    Optional<IntegrationProcessEntity> findByClientInteractionTokenHash(
            String clientInteractionTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IntegrationProcessEntity> findByProcessId(UUID processId);
}
