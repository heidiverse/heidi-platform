// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.data;

import org.heidiverse.heidi.issuer.model.FlowVariant;
import org.heidiverse.heidi.issuer.model.entity.IssuanceSessionEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface IssuanceSessionRepository extends JpaRepository<IssuanceSessionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
            select s from IssuanceSessionEntity s
            where s.signingSnapshot is not null and s.signingFlowReleased = false
                and (s.preAuthorizedCode is null or s.offerExpiresAt <= :now)
                and (s.accessTokenExpiresAt is null or s.accessTokenExpiresAt <= :now)
                and (s.refreshTokenExpiresAt is null or s.refreshTokenExpiresAt <= :now)
            """)
    java.util.List<IssuanceSessionEntity> finishedSigningFlows(java.time.Instant now);

    Optional<IssuanceSessionEntity> findByConnectionIdAndVariant(String connectionId, FlowVariant variant);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IssuanceSessionEntity> findByPreAuthorizedCode(String preAuthorizedCode);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IssuanceSessionEntity> findByAccessTokenDigest(String accessTokenDigest);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IssuanceSessionEntity> findByRefreshTokenDigest(String refreshTokenDigest);
}
