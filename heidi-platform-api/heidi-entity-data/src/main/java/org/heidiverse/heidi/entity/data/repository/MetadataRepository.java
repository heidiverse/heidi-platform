// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import org.heidiverse.heidi.entity.model.entity.CredentialSchemeEntity;
import org.heidiverse.heidi.entity.model.entity.MetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface MetadataRepository
        extends JpaRepository<MetadataEntity, Integer>, JpaSpecificationExecutor<MetadataEntity> {
    Optional<MetadataEntity> findByCredentialSchemeEntity(
            final CredentialSchemeEntity credentialSchemeEntity);
}
