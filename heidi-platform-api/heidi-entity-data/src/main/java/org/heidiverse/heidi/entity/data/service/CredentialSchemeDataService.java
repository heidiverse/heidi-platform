// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.service;

import org.heidiverse.heidi.entity.data.repository.CredentialSchemeAttributeRepository;
import org.heidiverse.heidi.entity.data.repository.CredentialSchemeRepository;
import org.heidiverse.heidi.entity.data.repository.StyleRepository;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeState;
import org.heidiverse.heidi.entity.model.entity.*;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CredentialSchemeDataService {

    private final CredentialSchemeAttributeRepository credentialSchemeAttributeRepository;
    private final CredentialSchemeRepository credentialSchemeRepository;
    private final StyleRepository styleRepository;

    public CredentialSchemeDataService(
            final CredentialSchemeAttributeRepository credentialSchemeAttributeRepository,
            final CredentialSchemeRepository credentialSchemeRepository,
            final StyleRepository styleRepository) {
        this.credentialSchemeAttributeRepository = credentialSchemeAttributeRepository;
        this.credentialSchemeRepository = credentialSchemeRepository;
        this.styleRepository = styleRepository;
    }

    @Transactional
    public List<CredentialSchemeEntity> findAllFilteredByState(
            Collection<CredentialSchemeState> states) {
        if (states != null && !states.isEmpty()) {
            return credentialSchemeRepository.findByStateIn(states);
        }
        return credentialSchemeRepository.findAll();
    }
    @Transactional
    public Optional<String> findBundleByOcaBundleFileName(String ocaBundleFileName) {
        return styleRepository
                .findByOcaBundleFileNameEquals(ocaBundleFileName)
                .map(CredentialSchemeStyleEntity::getOcaBundle);
    }

    public Optional<CredentialSchemeStyleEntity> findSwiyuStyle(String fileName) {
        return styleRepository.findSwiyuStyle(fileName)
                .or(() -> styleRepository.findByOcaBundleFileNameEquals(fileName));
    }

    public List<CredentialSchemeEntity> findAllByTenantIdAndState(
            String tenantId, Collection<CredentialSchemeState> states) {
        if (states != null && !states.isEmpty()) {
            return credentialSchemeRepository.findAllByTenantIdAndStateIn(tenantId, states);
        }
        return credentialSchemeRepository.findAllByTenantId(tenantId);
    }

    /** Identity ids that have at least one published credential schema. */
    @Transactional
    public Set<Integer> findPublishedIssuerIdentityIds() {
        return credentialSchemeRepository.findByStateIn(Set.of(CredentialSchemeState.PUBLISHED)).stream()
                .map(CredentialSchemeEntity::getIssuerDefinition)
                .filter(Objects::nonNull)
                .map(IssuerDefinitionEntity::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Transactional
    public List<CredentialSchemeEntity> findAllByStatusListId(UUID statusListId) {
        return credentialSchemeRepository.findAllByStatusListId(statusListId);
    }

    @Transactional
    public List<CredentialSchemeEntity> findByIssuerDefinitionSlugFilteredByState(
            final String slug, final Collection<CredentialSchemeState> states) {
        if (states != null && !states.isEmpty()) {
            return credentialSchemeRepository.findByIssuerDefinitionSlugAndStateIn(slug, states);
        }
        return credentialSchemeRepository.findByIssuerDefinitionSlug(slug);
    }

    @Transactional
    public List<CredentialSchemeEntity> findByCredentialIdentifier(
            final String credentialIdentifier) {
        return credentialSchemeRepository.findByCredentialIdentifier(credentialIdentifier);
    }

    @Transactional
    public CredentialSchemeEntity insertScheme(
            final CredentialSchemeEntity credentialSchemeEntity) {
        return credentialSchemeRepository.save(credentialSchemeEntity);
    }

    @Transactional
    public void deleteStyles(final List<CredentialSchemeStyleEntity> styleEntities) {
        styleRepository.deleteAll(styleEntities);
    }

    @Transactional
    public void deleteAttributes(final List<CredentialSchemeAttributeEntity> attributeEntities) {
        credentialSchemeAttributeRepository.deleteAll(attributeEntities);
    }

    @Transactional
    public List<CredentialSchemeStyleEntity> findAllStylesByCredentialSchemeEntity(
            final CredentialSchemeEntity credentialSchemeEntity) {
        return styleRepository.findByCredentialSchemeEntity(credentialSchemeEntity);
    }

    @Transactional
    public List<CredentialSchemeAttributeEntity> findAttributesByCredentialScheme(
            final CredentialSchemeEntity credentialScheme) {
        return credentialSchemeAttributeRepository.findAllByCredentialSchemeEntity(
                credentialScheme);
    }

    @Transactional
    public Optional<CredentialSchemeEntity> findByCredentialIdentifierAndVersion(
            final String credentialIdentifier, final String version) {
        return credentialSchemeRepository.findByCredentialIdentifierAndVersion(
                credentialIdentifier, version);
    }

    @Transactional
    public List<CredentialSchemeAttributeEntity> insertCredentialSchemeAttributes(
            final List<CredentialSchemeAttributeEntity> attributes) {
        return credentialSchemeAttributeRepository.saveAll(attributes);
    }

    @Transactional
    public void insertStyles(final List<CredentialSchemeStyleEntity> styles) {
        styleRepository.saveAll(styles);
    }

    @Transactional
    public Optional<CredentialSchemeEntity> findById(final UUID id) {
        return credentialSchemeRepository.findByUuid(id);
    }
}
