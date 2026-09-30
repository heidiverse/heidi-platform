// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import org.heidiverse.heidi.entity.model.catalog.AttributeCatalog;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "t_attribute_catalog")
public class AttributeCatalogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_attribute_catalog_id", nullable = false)
    private Integer id;

    @Column(name = "catalog_display_name", nullable = false)
    private String catalogDisplayName;

    @Column(name = "specification_display_name", nullable = false)
    private String specificationDisplayName;

    @Column(name = "specification_url")
    private String specificationUrl;

    @OneToMany(mappedBy = "catalog", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CatalogAttributeEntity> attributes = new ArrayList<>();

    public AttributeCatalogEntity() {}

    public AttributeCatalogEntity(
            Integer id,
            String catalogDisplayName,
            String specificationDisplayName,
            String specificationUrl,
            List<CatalogAttributeEntity> attributes) {
        this.id = id;
        this.catalogDisplayName = catalogDisplayName;
        this.specificationDisplayName = specificationDisplayName;
        this.specificationUrl = specificationUrl;
        this.attributes = attributes != null ? attributes : new ArrayList<>();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCatalogDisplayName() {
        return catalogDisplayName;
    }

    public void setCatalogDisplayName(String catalogDisplayName) {
        this.catalogDisplayName = catalogDisplayName;
    }

    public String getSpecificationDisplayName() {
        return specificationDisplayName;
    }

    public void setSpecificationDisplayName(String specificationDisplayName) {
        this.specificationDisplayName = specificationDisplayName;
    }

    public String getSpecificationUrl() {
        return specificationUrl;
    }

    public void setSpecificationUrl(String specificationUrl) {
        this.specificationUrl = specificationUrl;
    }

    public List<CatalogAttributeEntity> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<CatalogAttributeEntity> attributes) {
        this.attributes = attributes != null ? attributes : new ArrayList<>();
    }

    // Helper methods
    public static AttributeCatalog toModel(AttributeCatalogEntity entity) {
        AttributeCatalog catalog =
                new AttributeCatalog(
                        entity.getId(),
                        entity.getCatalogDisplayName(),
                        entity.getSpecificationDisplayName(),
                        entity.getSpecificationUrl());
        catalog.setAttributes(
                entity.getAttributes().stream()
                        .map(CatalogAttributeEntity::toModel)
                        .collect(Collectors.toList()));
        return catalog;
    }

    public void updateFromModel(AttributeCatalog model) {
        this.catalogDisplayName = model.getCatalogDisplayName();
        this.specificationDisplayName = model.getSpecificationDisplayName();
        this.specificationUrl = model.getSpecificationUrl();
    }
}
