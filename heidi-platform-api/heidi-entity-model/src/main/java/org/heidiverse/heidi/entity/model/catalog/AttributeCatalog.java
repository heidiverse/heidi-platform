// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.catalog;

import java.util.List;

public class AttributeCatalog {
    private Integer id;
    private String catalogDisplayName;
    private String specificationDisplayName;
    private String specificationUrl;
    private List<CatalogAttribute> attributes;

    // Constructors
    public AttributeCatalog() {}

    public AttributeCatalog(
            Integer id,
            String catalogDisplayName,
            String specificationDisplayName,
            String specificationUrl) {
        this.id = id;
        this.catalogDisplayName = catalogDisplayName;
        this.specificationDisplayName = specificationDisplayName;
        this.specificationUrl = specificationUrl;
    }

    // Getters and setters
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

    public List<CatalogAttribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<CatalogAttribute> attributes) {
        this.attributes = attributes;
    }
}
