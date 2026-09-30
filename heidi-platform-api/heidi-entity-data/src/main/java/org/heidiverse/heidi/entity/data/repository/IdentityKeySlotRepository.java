// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityKeySlotRepository extends JpaRepository<IdentityKeySlotEntity, UUID> {
    List<IdentityKeySlotEntity> findAllByIdentityIdOrderByTypeAscOrderAsc(Integer identityId);

    List<IdentityKeySlotEntity> findAllByIdentityIdAndTypeOrderByOrderAsc(
            Integer identityId, IdentityKeySlotType type);

    Optional<IdentityKeySlotEntity> findByIdentityIdAndTypeAndTrustSystemAndOperation(
            Integer identityId,
            IdentityKeySlotType type,
            IssuerTrustSystem trustSystem,
            String operation);

    List<IdentityKeySlotEntity> findAllByKeyId(UUID keyId);

    List<IdentityKeySlotEntity> findAllByCertificateId(UUID certificateId);
}
