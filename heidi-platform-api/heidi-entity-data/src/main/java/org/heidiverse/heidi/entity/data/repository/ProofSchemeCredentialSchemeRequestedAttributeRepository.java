// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import org.heidiverse.heidi.entity.model.entity.CredentialSchemeEntity;
import org.heidiverse.heidi.entity.model.entity.ProofSchemeCredentialSchemeEntity;
import org.heidiverse.heidi.entity.model.entity.ProofSchemeCredentialSchemeRequestedAttributeEntity;
import org.heidiverse.heidi.entity.model.entity.ProofSchemeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProofSchemeCredentialSchemeRequestedAttributeRepository
        extends JpaRepository<ProofSchemeCredentialSchemeRequestedAttributeEntity, Integer>,
                JpaSpecificationExecutor<ProofSchemeCredentialSchemeRequestedAttributeEntity> {
    List<ProofSchemeCredentialSchemeRequestedAttributeEntity>
            findAllByProofSchemeCredentialSchemeEntity(
                    final ProofSchemeCredentialSchemeEntity proofSchemeCredentialSchemeEntity);

    void deleteAllByProofSchemeCredentialSchemeEntity(
            final ProofSchemeCredentialSchemeEntity proofSchemeCredentialSchemeEntity);
}
