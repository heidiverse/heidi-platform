// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.catalog;

import org.heidiverse.heidi.shared.localized.LocalizedValue;
import org.heidiverse.heidi.entity.model.credentialscheme.AttributeType;

public class CatalogAttribute {
    private Integer id;
    private String attributeKey;
    private AttributeType attributeType;
    private LocalizedValue<String> attributeDisplayName;

    // Constructors
    public CatalogAttribute() {}

    public CatalogAttribute(
            Integer id,
            String attributeKey,
            AttributeType attributeType,
            LocalizedValue<String> attributeDisplayName) {
        this.id = id;
        this.attributeKey = attributeKey;
        this.attributeType = attributeType;
        this.attributeDisplayName = attributeDisplayName;
    }

    // Getters and setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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
}
