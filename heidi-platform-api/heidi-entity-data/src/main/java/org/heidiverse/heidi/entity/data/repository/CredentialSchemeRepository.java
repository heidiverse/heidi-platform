// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeState;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CredentialSchemeRepository
        extends JpaRepository<CredentialSchemeEntity, Integer>,
                JpaSpecificationExecutor<CredentialSchemeEntity> {
    Optional<CredentialSchemeEntity> findByCredentialIdentifierAndVersion(
            final String credentialIdentifier, final String version);

    List<CredentialSchemeEntity> findByCredentialIdentifier(final String credentialIdentifier);

    Optional<CredentialSchemeEntity> findByUuid(final UUID uuid);

    List<CredentialSchemeEntity> findByIssuerDefinitionSlug(final String slug);

    List<CredentialSchemeEntity> findByIssuerDefinitionSlugAndStateIn(
            final String slug, final Collection<CredentialSchemeState> state);

    List<CredentialSchemeEntity> findByStateIn(final Collection<CredentialSchemeState> state);

    List<CredentialSchemeEntity> findAllByTenantIdAndStateIn(
            String tenantId, Collection<CredentialSchemeState> state);

    List<CredentialSchemeEntity> findAllByTenantId(String tenantId);

    List<CredentialSchemeEntity> findAllByStatusListId(UUID statusListId);
}
