// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import org.heidiverse.heidi.entity.model.entity.CatalogAttributeEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogAttributeRepository extends JpaRepository<CatalogAttributeEntity, Integer> {
    List<CatalogAttributeEntity> findByCatalogId(Integer catalogId);
}
