// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import org.heidiverse.heidi.entity.model.entity.SigningProviderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SigningProviderRepository extends JpaRepository<SigningProviderEntity, Integer> {
    List<SigningProviderEntity> findAllByTenantIdOrderByName(String tenantId);
    List<SigningProviderEntity> findAllByTenantIdIsNullOrderByName();
    Optional<SigningProviderEntity> findFirstByTenantIdAndDefaultProviderTrue(String tenantId);
    Optional<SigningProviderEntity> findFirstByTenantIdIsNullAndDefaultProviderTrue();
}
