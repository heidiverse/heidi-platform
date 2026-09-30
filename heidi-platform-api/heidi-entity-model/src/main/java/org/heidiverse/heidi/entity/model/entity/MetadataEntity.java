// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "t_metadata")
public class MetadataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_metadata_id", nullable = false)
    private Integer id;

    @NotNull
    @OneToOne
    @JoinColumn(name = "fk_credential_scheme_id", nullable = false)
    private CredentialSchemeEntity credentialSchemeEntity;

    public CredentialSchemeEntity getCredentialSchemeEntity() {
        return credentialSchemeEntity;
    }

    public void setCredentialSchemeEntity(CredentialSchemeEntity fkCredentialScheme) {
        this.credentialSchemeEntity = fkCredentialScheme;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}
