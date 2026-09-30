// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "t_proof_scheme_credential_scheme_requested_attribute")
public class ProofSchemeCredentialSchemeRequestedAttributeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_proof_scheme_requested_attribute_id", nullable = false)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "fk_proof_scheme_credential_scheme_id", nullable = false)
    private ProofSchemeCredentialSchemeEntity proofSchemeCredentialSchemeEntity;

    @ManyToOne
    @JoinColumn(name = "fk_credential_scheme_attribute_id", nullable = false)
    private CredentialSchemeAttributeEntity credentialSchemeAttributeEntity;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public ProofSchemeCredentialSchemeEntity getProofSchemeCredentialSchemeEntity() {
        return proofSchemeCredentialSchemeEntity;
    }

    public void setProofSchemeCredentialSchemeEntity(
            ProofSchemeCredentialSchemeEntity proofSchemeCredentialSchemeEntity) {
        this.proofSchemeCredentialSchemeEntity = proofSchemeCredentialSchemeEntity;
    }

    public CredentialSchemeAttributeEntity getCredentialSchemeAttributeEntity() {
        return credentialSchemeAttributeEntity;
    }

    public void setCredentialSchemeAttributeEntity(
            CredentialSchemeAttributeEntity credentialSchemeAttributeEntity) {
        this.credentialSchemeAttributeEntity = credentialSchemeAttributeEntity;
    }
}
