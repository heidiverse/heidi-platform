// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import org.heidiverse.heidi.entity.model.entity.TrustRegistryEntity;
import org.heidiverse.heidi.entity.model.tenant.TrustRegistryType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrustRegistryRepository
        extends JpaRepository<TrustRegistryEntity, Integer>,
                JpaSpecificationExecutor<TrustRegistryEntity> {

    Optional<TrustRegistryEntity> findByTrustRegistry(TrustRegistryType trustRegistry);
}
