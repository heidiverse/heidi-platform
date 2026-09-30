// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import org.heidiverse.heidi.entity.model.entity.CredentialSchemeEntity;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeStyleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StyleRepository
        extends JpaRepository<CredentialSchemeStyleEntity, Integer>,
                JpaSpecificationExecutor<CredentialSchemeStyleEntity> {
    List<CredentialSchemeStyleEntity> findByCredentialSchemeEntity(
            final CredentialSchemeEntity credentialSchemeEntity);

    @Query("select c from CredentialSchemeStyleEntity c where c.ocaBundleFileName = ?1 order by c.id limit 1")
    Optional<CredentialSchemeStyleEntity> findByOcaBundleFileNameEquals(String ocaBundleFileName);

    @Query("select c from CredentialSchemeStyleEntity c where c.swiyuOcaFileName = ?1 order by c.id limit 1")
    Optional<CredentialSchemeStyleEntity> findSwiyuStyle(String fileName);
}
