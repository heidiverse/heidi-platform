// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.service;

import org.heidiverse.heidi.entity.data.repository.MetadataAttributeRepository;
import org.heidiverse.heidi.entity.data.repository.MetadataRepository;
import org.heidiverse.heidi.entity.model.entity.*;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MetadataDataService {

    private final MetadataRepository metadataRepository;
    private final MetadataAttributeRepository metadataAttributeRepository;

    public MetadataDataService(
            final MetadataRepository metadataRepository,
            final MetadataAttributeRepository metadataAttributeRepository) {
        this.metadataRepository = metadataRepository;
        this.metadataAttributeRepository = metadataAttributeRepository;
    }

    @Transactional
    public MetadataEntity insertMetadata(final MetadataEntity metadataEntity) {
        return metadataRepository.save(metadataEntity);
    }

    @Transactional
    public void insertMetadataAttributes(
            final List<MetadataAttributeEntity> metadataAttributeEntities) {
        metadataAttributeRepository.saveAll(metadataAttributeEntities);
    }

    @Transactional
    public Optional<MetadataEntity> findByCredentialScheme(
            final CredentialSchemeEntity credentialSchemeEntity) {
        return metadataRepository.findByCredentialSchemeEntity(credentialSchemeEntity);
    }

    @Transactional
    public List<MetadataAttributeEntity> findAllByMetadata(final MetadataEntity metadataEntity) {
        return metadataAttributeRepository.findAllByMetadataEntity(metadataEntity);
    }

    @Transactional
    public void deleteMetadataAttributes(
            final List<MetadataAttributeEntity> metadataAttributeEntities) {
        metadataAttributeRepository.deleteAll(metadataAttributeEntities);
    }
}
