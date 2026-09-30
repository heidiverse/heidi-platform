// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import java.util.List;
import java.util.UUID;
import org.heidiverse.heidi.entity.model.entity.IssuingSubcaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuingSubcaRepository extends JpaRepository<IssuingSubcaEntity, UUID> {
    List<IssuingSubcaEntity> findAllByTenantIdOrderByCreatedAtDesc(String tenantId);
    boolean existsByKeyId(UUID keyId);
}
