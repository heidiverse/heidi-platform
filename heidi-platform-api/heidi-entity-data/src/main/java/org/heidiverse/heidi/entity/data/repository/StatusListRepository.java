// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import org.heidiverse.heidi.entity.model.entity.StatusListEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StatusListRepository extends JpaRepository<StatusListEntity, UUID> {
    List<StatusListEntity> findAllByTenantIdOrderByName(String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select statusList from StatusListEntity statusList where statusList.id = :id")
    Optional<StatusListEntity> findByIdForUpdate(@Param("id") UUID id);
}
