// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "t_proof_scheme_credential_scheme")
public class ProofSchemeCredentialSchemeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_proof_scheme_credential_scheme_id", nullable = false)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "fk_proof_scheme_id", nullable = false)
    private ProofSchemeEntity proofSchemeEntity;

    @ManyToOne
    @JoinColumn(name = "fk_credential_scheme_id", nullable = false)
    private CredentialSchemeEntity credentialSchemeEntity;

    @Column(name = "credential_position", nullable = false)
    private int credentialPosition;

    @OneToMany(mappedBy = "proofSchemeCredentialSchemeEntity", fetch = FetchType.EAGER)
    private List<ProofSchemeCredentialSchemeRequestedAttributeEntity>
            proofSchemeCredentialSchemeRequestedAttributeEntityList = new ArrayList<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public ProofSchemeEntity getProofSchemeEntity() {
        return proofSchemeEntity;
    }

    public void setProofSchemeEntity(ProofSchemeEntity proofSchemeEntity) {
        this.proofSchemeEntity = proofSchemeEntity;
    }

    public CredentialSchemeEntity getCredentialSchemeEntity() {
        return credentialSchemeEntity;
    }

    public void setCredentialSchemeEntity(CredentialSchemeEntity credentialSchemeEntity) {
        this.credentialSchemeEntity = credentialSchemeEntity;
    }

    public int getCredentialPosition() {
        return credentialPosition;
    }

    public void setCredentialPosition(int credentialPosition) {
        this.credentialPosition = credentialPosition;
    }

    public List<ProofSchemeCredentialSchemeRequestedAttributeEntity>
            getProofSchemeCredentialSchemeRequestedAttributeEntityList() {
        return proofSchemeCredentialSchemeRequestedAttributeEntityList;
    }

    public void setProofSchemeCredentialSchemeRequestedAttributeEntityList(
            List<ProofSchemeCredentialSchemeRequestedAttributeEntity>
                    proofSchemeCredentialSchemeRequestedAttributeEntityList) {
        this.proofSchemeCredentialSchemeRequestedAttributeEntityList =
                proofSchemeCredentialSchemeRequestedAttributeEntityList;
    }
}
