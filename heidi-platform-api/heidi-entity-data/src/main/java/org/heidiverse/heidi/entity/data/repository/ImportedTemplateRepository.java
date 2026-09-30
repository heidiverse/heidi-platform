// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import org.heidiverse.heidi.entity.model.entity.ImportedTemplateEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportedTemplateRepository extends JpaRepository<ImportedTemplateEntity, UUID> {

    Optional<ImportedTemplateEntity> findBySourceTypeAndSourceIdAndSourceVersion(
            String sourceType, UUID sourceId, String sourceVersion);

    List<ImportedTemplateEntity> findAllByOrderByCreatedAtAsc();
}
