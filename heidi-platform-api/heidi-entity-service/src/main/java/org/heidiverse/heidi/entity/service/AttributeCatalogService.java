// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import org.heidiverse.heidi.entity.data.repository.AttributeCatalogRepository;
import org.heidiverse.heidi.entity.data.repository.CatalogAttributeRepository;
import org.heidiverse.heidi.entity.model.catalog.AttributeCatalog;
import org.heidiverse.heidi.entity.model.catalog.CatalogAttribute;
import org.heidiverse.heidi.entity.model.entity.AttributeCatalogEntity;
import org.heidiverse.heidi.entity.model.entity.CatalogAttributeEntity;
import org.heidiverse.heidi.entity.model.exceptions.DuplicateAttributeException;
import org.heidiverse.heidi.entity.model.exceptions.DuplicateCatalogException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AttributeCatalogService {

    private final AttributeCatalogRepository catalogRepository;
    private final CatalogAttributeRepository attributeRepository;

    public AttributeCatalogService(
            AttributeCatalogRepository catalogRepository,
            CatalogAttributeRepository attributeRepository) {
        this.catalogRepository = catalogRepository;
        this.attributeRepository = attributeRepository;
    }

    // Catalog CRUD
    @Transactional
    public AttributeCatalog createCatalog(AttributeCatalog catalog) {
        boolean exists =
                catalogRepository.existsByCatalogDisplayName(catalog.getCatalogDisplayName());
        if (exists) {
            throw new DuplicateCatalogException(
                    "Catalog with display name '"
                            + catalog.getCatalogDisplayName()
                            + "' already exists.");
        }

        AttributeCatalogEntity entity = new AttributeCatalogEntity();
        entity.updateFromModel(catalog);
        return AttributeCatalogEntity.toModel(catalogRepository.save(entity));
    }

    public List<AttributeCatalog> getAllCatalogs() {
        return catalogRepository.findAllWithAttributes().stream()
                .map(AttributeCatalogEntity::toModel)
                .collect(Collectors.toList());
    }

    public Optional<AttributeCatalog> getCatalogById(Integer id) {
        return catalogRepository.findById(id).map(AttributeCatalogEntity::toModel);
    }

    @Transactional
    public AttributeCatalog updateCatalog(Integer id, AttributeCatalog catalog) {
        AttributeCatalogEntity entity =
                catalogRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("Catalog not found"));
        entity.updateFromModel(catalog);
        return AttributeCatalogEntity.toModel(catalogRepository.save(entity));
    }

    @Transactional
    public void deleteCatalog(Integer id) {
        catalogRepository.deleteById(id);
    }

    // Attribute CRUD
    @Transactional
    public CatalogAttribute createAttribute(Integer catalogId, CatalogAttribute attribute) {
        AttributeCatalogEntity catalog =
                catalogRepository
                        .findById(catalogId)
                        .orElseThrow(() -> new RuntimeException("Catalog not found"));

        boolean exists =
                attributeRepository.findByCatalogId(catalogId).stream()
                        .anyMatch(
                                attr -> attr.getAttributeKey().equals(attribute.getAttributeKey()));

        if (exists) {
            throw new DuplicateAttributeException(
                    "Attribute with key '"
                            + attribute.getAttributeKey()
                            + "' already exists in catalog ID "
                            + catalogId);
        }

        CatalogAttributeEntity entity = new CatalogAttributeEntity();
        entity.updateFromModel(attribute);
        entity.setCatalog(catalog);
        return CatalogAttributeEntity.toModel(attributeRepository.save(entity));
    }

    public List<CatalogAttribute> getAttributesByCatalogId(Integer catalogId) {
        return attributeRepository.findByCatalogId(catalogId).stream()
                .map(CatalogAttributeEntity::toModel)
                .collect(Collectors.toList());
    }

    public Optional<CatalogAttribute> getAttributeById(Integer id) {
        return attributeRepository.findById(id).map(CatalogAttributeEntity::toModel);
    }

    @Transactional
    public CatalogAttribute updateAttribute(Integer id, CatalogAttribute attribute) {
        CatalogAttributeEntity entity =
                attributeRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("Attribute not found"));
        entity.updateFromModel(attribute);
        return CatalogAttributeEntity.toModel(attributeRepository.save(entity));
    }

    @Transactional
    public void deleteAttribute(Integer id) {
        attributeRepository.deleteById(id);
    }
}
