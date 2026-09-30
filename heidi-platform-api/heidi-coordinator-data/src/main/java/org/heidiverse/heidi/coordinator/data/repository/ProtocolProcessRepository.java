// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.data.repository;

import org.heidiverse.heidi.coordinator.model.entity.ProtocolProcessEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ProtocolProcessRepository
        extends JpaRepository<ProtocolProcessEntity, Integer>,
                JpaSpecificationExecutor<ProtocolProcessEntity> {

    ProtocolProcessEntity findByConnectionId(String connectionId);

    void deleteByConnectionId(String connectionId);
}
