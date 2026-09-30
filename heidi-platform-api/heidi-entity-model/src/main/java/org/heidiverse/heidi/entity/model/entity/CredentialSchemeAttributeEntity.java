// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import org.heidiverse.heidi.entity.model.credentialscheme.AttributeType;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "t_credential_scheme_attribute")
public class CredentialSchemeAttributeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_credential_scheme_attribute_id", nullable = false)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "fk_credential_scheme_id")
    private CredentialSchemeEntity credentialSchemeEntity;

    @NotNull
    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @NotNull
    @Column(name = "field_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private AttributeType fieldType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "t_credential_scheme_attribute_detail",
            joinColumns = @JoinColumn(name = "fk_credential_scheme_attribute_id"))
    @MapKeyColumn(name = "language")
    private Map<String, String> displayName = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "t_credential_scheme_format_specific_attribute_name",
            joinColumns = @JoinColumn(name = "fk_credential_scheme_attribute_id"))
    @MapKeyColumn(name = "credential_format")
    private Map<String, String> attributeNameOverride = new HashMap<>();

    @Column(name = "is_array", nullable = false)
    private boolean isArray = false;

    @Column(name = "is_sensitive", nullable = false)
    private boolean isSensitive = false;

    @Column(name = "is_disclosable", nullable = false)
    private boolean isDisclosable = true;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public @NotNull String getFieldName() {
        return fieldName;
    }

    public void setFieldName(@NotNull String fieldName) {
        this.fieldName = fieldName;
    }

    public @NotNull AttributeType getFieldType() {
        return fieldType;
    }

    public void setFieldType(@NotNull AttributeType fieldType) {
        this.fieldType = fieldType;
    }

    public CredentialSchemeEntity getCredentialSchemeEntity() {
        return credentialSchemeEntity;
    }

    public void setCredentialSchemeEntity(CredentialSchemeEntity credentialSchemeEntity) {
        this.credentialSchemeEntity = credentialSchemeEntity;
    }

    public Map<String, String> getDisplayName() {
        return displayName;
    }

    public void setDisplayName(Map<String, String> displayName) {
        this.displayName = displayName;
    }

    public Map<String, String> getFormatSpecificAttributeName() {
        return attributeNameOverride;
    }

    public void setFormatSpecificAttributeName(Map<String, String> formatSpecificAttributeName) {
        if (formatSpecificAttributeName == null || formatSpecificAttributeName.isEmpty()) {
            this.attributeNameOverride = new HashMap<>();
        } else {
            this.attributeNameOverride = new HashMap<>(formatSpecificAttributeName);
            this.attributeNameOverride.values().removeIf(String::isEmpty);
        }
    }

    public boolean isSensitive() {
        return isSensitive;
    }

    public void setSensitive(boolean sensitive) {
        isSensitive = sensitive;
    }

    public boolean isDisclosable() {
        return isDisclosable;
    }

    public void setDisclosable(boolean disclosable) {
        isDisclosable = disclosable;
    }

    public boolean isArray() {
        return isArray;
    }

    public void setArray(boolean array) {
        isArray = array;
    }
}
