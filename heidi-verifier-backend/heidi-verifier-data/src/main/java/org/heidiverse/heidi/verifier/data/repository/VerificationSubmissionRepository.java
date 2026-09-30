// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.data.repository;

import org.heidiverse.heidi.verifier.model.entity.VerificationSubmissionEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VerificationSubmissionRepository
        extends JpaRepository<VerificationSubmissionEntity, Integer> {

    Optional<VerificationSubmissionEntity> findByTransactionId(String transactionId);

    boolean existsByTransactionId(String transactionId);
}
