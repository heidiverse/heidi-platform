// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.heidiverse.heidi.entity.model.entity.SigningKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SigningKeyRepository extends JpaRepository<SigningKeyEntity, UUID> {
    List<SigningKeyEntity> findAllByTenantIdOrderByLogicalKeyId(String tenantId);
    Optional<SigningKeyEntity> findByTenantIdAndLogicalKeyId(
            String tenantId, String logicalKeyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select key from SigningKeyEntity key where key.id = :id")
    Optional<SigningKeyEntity> findByIdForUpdate(@Param("id") UUID id);
}
