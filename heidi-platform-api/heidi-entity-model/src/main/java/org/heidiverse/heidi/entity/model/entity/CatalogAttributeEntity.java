// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import org.heidiverse.heidi.shared.localized.LocalizedValue;
import org.heidiverse.heidi.entity.model.catalog.CatalogAttribute;
import org.heidiverse.heidi.entity.model.credentialscheme.AttributeType;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "t_catalog_attribute")
public class CatalogAttributeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_catalog_attribute_id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_attribute_catalog_id", nullable = false)
    private AttributeCatalogEntity catalog;

    @Column(name = "attribute_key", nullable = false)
    private String attributeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "attribute_type", nullable = false)
    private AttributeType attributeType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attribute_display_name", nullable = false, columnDefinition = "jsonb")
    private LocalizedValue<String> attributeDisplayName;

    public CatalogAttributeEntity() {}

    public CatalogAttributeEntity(
            Integer id,
            AttributeCatalogEntity catalog,
            String attributeKey,
            AttributeType attributeType,
            LocalizedValue<String> attributeDisplayName) {
        this.id = id;
        this.catalog = catalog;
        this.attributeKey = attributeKey;
        this.attributeType = attributeType;
        this.attributeDisplayName = attributeDisplayName;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public AttributeCatalogEntity getCatalog() {
        return catalog;
    }

    public void setCatalog(AttributeCatalogEntity catalog) {
        this.catalog = catalog;
    }

    public String getAttributeKey() {
        return attributeKey;
    }

    public void setAttributeKey(String attributeKey) {
        this.attributeKey = attributeKey;
    }

    public AttributeType getAttributeType() {
        return attributeType;
    }

    public void setAttributeType(AttributeType attributeType) {
        this.attributeType = attributeType;
    }

    public LocalizedValue<String> getAttributeDisplayName() {
        return attributeDisplayName;
    }

    public void setAttributeDisplayName(LocalizedValue<String> attributeDisplayName) {
        this.attributeDisplayName = attributeDisplayName;
    }

    // Helper methods
    public static CatalogAttribute toModel(CatalogAttributeEntity entity) {
        return new CatalogAttribute(
                entity.getId(),
                entity.getAttributeKey(),
                entity.getAttributeType(),
                entity.getAttributeDisplayName());
    }

    public void updateFromModel(CatalogAttribute model) {
        this.attributeKey = model.getAttributeKey();
        this.attributeType = model.getAttributeType();
        this.attributeDisplayName = model.getAttributeDisplayName();
    }
}
