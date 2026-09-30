// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "t_metadata_attribute")
public class MetadataAttributeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_metadata_attribute_id", nullable = false)
    private Integer id;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "fk_metadata_id", nullable = false)
    private MetadataEntity metadataEntity;

    @NotNull
    @Column(name = "attribute_key", nullable = false, length = Integer.MAX_VALUE)
    private String attributeKey;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "t_metadata_attribute_detail",
            joinColumns = @JoinColumn(name = "fk_metadata_attribute_id"))
    @MapKeyColumn(name = "language")
    private Map<String, String> displayName = new HashMap<>();

    public String getAttributeKey() {
        return attributeKey;
    }

    public void setAttributeKey(String attributeKey) {
        this.attributeKey = attributeKey;
    }

    public MetadataEntity getMetadataEntity() {
        return metadataEntity;
    }

    public void setMetadataEntity(MetadataEntity fkMetadata) {
        this.metadataEntity = fkMetadata;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Map<String, String> getDisplayName() {
        return displayName;
    }

    public void setDisplayName(Map<String, String> displayName) {
        this.displayName = displayName;
    }
}
