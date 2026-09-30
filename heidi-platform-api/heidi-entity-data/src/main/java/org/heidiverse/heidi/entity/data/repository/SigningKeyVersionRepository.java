// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SigningKeyVersionRepository extends JpaRepository<SigningKeyVersionEntity, UUID> {
    List<SigningKeyVersionEntity> findAllByKeyIdOrderByVersion(UUID keyId);
    Optional<SigningKeyVersionEntity> findByKeyIdAndVersion(UUID keyId, int version);
}
