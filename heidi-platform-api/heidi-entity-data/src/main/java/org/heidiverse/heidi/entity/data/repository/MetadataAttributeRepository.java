// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import org.heidiverse.heidi.entity.model.entity.MetadataAttributeEntity;

import org.heidiverse.heidi.entity.model.entity.MetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface MetadataAttributeRepository
        extends JpaRepository<MetadataAttributeEntity, Integer>,
                JpaSpecificationExecutor<MetadataAttributeEntity> {
    List<MetadataAttributeEntity> findAllByMetadataEntity(final MetadataEntity metadataEntity);
}
