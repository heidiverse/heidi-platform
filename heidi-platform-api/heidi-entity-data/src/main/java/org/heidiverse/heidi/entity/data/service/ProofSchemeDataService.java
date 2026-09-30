// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.service;

import org.heidiverse.heidi.entity.data.repository.*;
import org.heidiverse.heidi.entity.model.entity.*;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ProofSchemeDataService {

    private final ProofSchemeRepository proofSchemeRepository;
    private final ProofSchemeCredentialSchemeRequestedAttributeRepository
            proofSchemeCredentialSchemeRequestedAttributeRepository;
    private final ProofSchemeCredentialSchemeRepository proofSchemeCredentialSchemeRepository;

    public ProofSchemeDataService(
            final ProofSchemeRepository proofSchemeRepository,
            final ProofSchemeCredentialSchemeRequestedAttributeRepository
                    proofSchemeCredentialSchemeRequestedAttributeRepository,
            final ProofSchemeCredentialSchemeRepository proofSchemeCredentialSchemeRepository) {
        this.proofSchemeRepository = proofSchemeRepository;
        this.proofSchemeCredentialSchemeRequestedAttributeRepository =
                proofSchemeCredentialSchemeRequestedAttributeRepository;
        this.proofSchemeCredentialSchemeRepository = proofSchemeCredentialSchemeRepository;
    }

    @Transactional
    public ProofSchemeEntity insertProofScheme(final ProofSchemeEntity entity) {
        return proofSchemeRepository.save(entity);
    }

    @Transactional
    public Optional<ProofSchemeEntity> findByIdAndArchivedFalse(final UUID id) {
        return proofSchemeRepository.findByUuidAndArchivedFalse(id);
    }

    @Transactional
    public List<ProofSchemeEntity> findAllByArchivedFalse() {
        return proofSchemeRepository.findAllByArchivedFalse();
    }

    @Transactional
    public List<ProofSchemeEntity> findAllByTenantIdAndArchivedFalse(String tenantId) {
        return proofSchemeRepository.findAllByTenantIdAndArchivedFalse(tenantId);
    }

    /** Identity ids used by active proof schemas, including the legacy first-credential fallback. */
    @Transactional
    public Set<Integer> findActiveVerifierIdentityIds() {
        var identityIds = new java.util.HashSet<Integer>();
        for (var proof : proofSchemeRepository.findAllByArchivedFalse()) {
            var identity = proof.getVerifierIdentity();
            if (identity == null) {
                identity = findAllByProofScheme(proof).stream()
                        .sorted(java.util.Comparator.comparingInt(
                                ProofSchemeCredentialSchemeEntity::getCredentialPosition))
                        .map(ProofSchemeCredentialSchemeEntity::getCredentialSchemeEntity)
                        .map(CredentialSchemeEntity::getIssuerDefinition)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
            }
            if (identity != null && identity.getId() != null) identityIds.add(identity.getId());
        }
        return Set.copyOf(identityIds);
    }

    @Transactional
    public Optional<ProofSchemeEntity> findById(final UUID id) {
        return proofSchemeRepository.findByUuid(id);
    }

    @Transactional
    public void insertRequestedAttributes(
            final List<ProofSchemeCredentialSchemeRequestedAttributeEntity> requestedAttributes) {
        proofSchemeCredentialSchemeRequestedAttributeRepository.saveAll(requestedAttributes);
    }

    @Transactional
    public List<ProofSchemeCredentialSchemeRequestedAttributeEntity>
            findAllRequestedAttributesForProofSchemeCredentialScheme(
                    final ProofSchemeCredentialSchemeEntity proofSchemeCredentialSchemeEntity) {
        return proofSchemeCredentialSchemeRequestedAttributeRepository
                .findAllByProofSchemeCredentialSchemeEntity(proofSchemeCredentialSchemeEntity);
    }

    @Transactional
    public Optional<ProofSchemeCredentialSchemeEntity> findByProofSchemeAndCredentialScheme(
            final ProofSchemeEntity proofSchemeEntity,
            final CredentialSchemeEntity credentialSchemeEntity) {
        return proofSchemeCredentialSchemeRepository
                .findByProofSchemeEntityAndCredentialSchemeEntity(
                        proofSchemeEntity, credentialSchemeEntity);
    }

    @Transactional
    public ProofSchemeCredentialSchemeEntity insertProofSchemeCredentialScheme(
            final ProofSchemeCredentialSchemeEntity proofSchemeCredentialSchemeEntity) {
        return proofSchemeCredentialSchemeRepository.save(proofSchemeCredentialSchemeEntity);
    }

    @Transactional
    public List<ProofSchemeCredentialSchemeEntity> findAllByProofScheme(
            final ProofSchemeEntity proofSchemeEntity) {
        return proofSchemeCredentialSchemeRepository.findAllByProofSchemeEntity(proofSchemeEntity);
    }

    @Transactional
    public void deleteAllProofSchemeCredentialSchemeById(final List<Integer> ids) {
        proofSchemeCredentialSchemeRepository.deleteAllById(ids);
    }

    @Transactional
    public void deleteAllRequestedAttributesByProofSchemeCredentialSchemes(
            final List<ProofSchemeCredentialSchemeEntity> proofSchemeCredentialSchemeEntities) {
        proofSchemeCredentialSchemeEntities.forEach(
                proofSchemeCredentialSchemeRequestedAttributeRepository
                        ::deleteAllByProofSchemeCredentialSchemeEntity);
    }
}
