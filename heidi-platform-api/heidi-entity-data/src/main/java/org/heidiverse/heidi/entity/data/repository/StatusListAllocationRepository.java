// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import org.heidiverse.heidi.entity.model.entity.StatusListAllocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StatusListAllocationRepository
        extends JpaRepository<StatusListAllocationEntity, UUID> {
    Optional<StatusListAllocationEntity> findByStatusListIdAndAllocationKey(
            UUID statusListId, String allocationKey);

    @Query("select max(allocation.index) from StatusListAllocationEntity allocation "
            + "where allocation.statusListId = :statusListId")
    Integer maxIndex(@Param("statusListId") UUID statusListId);
}
