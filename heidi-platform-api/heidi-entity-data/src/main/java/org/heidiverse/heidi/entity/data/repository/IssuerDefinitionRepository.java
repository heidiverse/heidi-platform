// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface IssuerDefinitionRepository
        extends JpaRepository<IssuerDefinitionEntity, Integer>,
                JpaSpecificationExecutor<IssuerDefinitionEntity> {

    List<IssuerDefinitionEntity> findAllByDeletedFalse();

    List<IssuerDefinitionEntity> findAllByDeletedFalseAndTenantIdIsNull();

    List<IssuerDefinitionEntity> findAllByDeletedFalseAndTenantIdIsNullOrDeletedFalseAndTenantId(
            String tenantId);

    Optional<IssuerDefinitionEntity> findByIdAndDeletedFalse(int id);

    Optional<IssuerDefinitionEntity> findBySlug(String slug);
}
