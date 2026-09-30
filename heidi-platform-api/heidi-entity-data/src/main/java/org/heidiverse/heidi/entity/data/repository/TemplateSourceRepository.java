// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import org.heidiverse.heidi.entity.model.entity.LibrarySourceEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TemplateSourceRepository extends JpaRepository<LibrarySourceEntity, UUID> {}
