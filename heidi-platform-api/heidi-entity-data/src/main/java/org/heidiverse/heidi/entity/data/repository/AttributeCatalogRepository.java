// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import org.heidiverse.heidi.entity.model.entity.AttributeCatalogEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AttributeCatalogRepository extends JpaRepository<AttributeCatalogEntity, Integer> {
    @Query("SELECT DISTINCT c FROM AttributeCatalogEntity c LEFT JOIN FETCH c.attributes")
    List<AttributeCatalogEntity> findAllWithAttributes();

    boolean existsByCatalogDisplayName(String catalogDisplayName);
}
