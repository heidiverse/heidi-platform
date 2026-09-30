// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.data.repository;

import org.heidiverse.heidi.verifier.model.entity.VerificationRequestEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface VerificationRequestRepository
        extends JpaRepository<VerificationRequestEntity, Integer> {

    Optional<VerificationRequestEntity> findByRequestId(String requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from VerificationRequestEntity request where request.requestId = :requestId")
    Optional<VerificationRequestEntity> findByRequestIdForUpdate(@Param("requestId") String requestId);

    List<VerificationRequestEntity> findByTransactionId(String transactionId);

    List<VerificationRequestEntity> findByExpiresAtAfter(Instant now);

    @Query("""
            select distinct r.transactionId from VerificationRequestEntity r
            where r.signingSnapshot is not null and r.signingFlowReleased = false and r.status = 'SUCCESS'
            """)
    List<String> finishedSigningFlows();

    long deleteByExpiresAtBefore(Instant cutoff);
}
