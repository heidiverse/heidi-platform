// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.service;

import org.heidiverse.heidi.entity.data.repository.TrustRegistryRepository;
import org.heidiverse.heidi.entity.model.entity.TrustRegistryEntity;
import org.heidiverse.heidi.entity.model.tenant.TrustRegistryType;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TrustRegistryDataService {

    private final TrustRegistryRepository trustRegistryRepository;

    public TrustRegistryDataService(final TrustRegistryRepository trustRegistryRepository) {
        this.trustRegistryRepository = trustRegistryRepository;
    }

    @Transactional
    public Optional<TrustRegistryEntity> findByTrustRegistry(TrustRegistryType trustRegistry) {
        return trustRegistryRepository.findByTrustRegistry(trustRegistry);
    }

    @Transactional
    public List<TrustRegistryEntity> findAll() {
        return trustRegistryRepository.findAll();
    }
}
