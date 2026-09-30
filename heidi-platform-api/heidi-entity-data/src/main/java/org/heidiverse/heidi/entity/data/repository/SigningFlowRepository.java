// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.entity.data.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.heidiverse.heidi.shared.signing.SigningPurpose;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Expiring references to immutable versions; never a second source of key state. */
@Repository
public class SigningFlowRepository {
    private final JdbcTemplate jdbc;

    public SigningFlowRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean referencesKey(UUID keyId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM t_signing_flow_reference r
                    JOIN t_signing_key_version v ON v.pk_key_version_id = r.key_version_id
                    WHERE v.key_id = ?)
                """, Boolean.class, keyId));
    }

    @Transactional
    public void retain(UUID flowId, String client, String keyUri, SigningPurpose purpose, Instant expiry) {
        // Serialize with rotation/deletion, then re-read version state after acquiring the lock.
        var keys = jdbc.queryForList("""
                SELECT k.pk_key_id FROM t_signing_key k
                JOIN t_signing_key_version v ON v.key_id = k.pk_key_id
                WHERE v.key_uri = ? FOR UPDATE OF k
                """, UUID.class, keyUri);
        if (keys.size() != 1) throw new IllegalArgumentException("Signing version not found");
        var inserted = jdbc.update("""
                INSERT INTO t_signing_flow_reference(flow_id, client, key_version_id, purpose, expires_at)
                SELECT ?, ?, v.pk_key_version_id, ?, ? FROM t_signing_key_version v
                WHERE v.key_uri = ? AND (v.status = 'ACTIVE'
                    OR (v.status = 'PREVIOUS' AND v.previous_until > CURRENT_TIMESTAMP))
                ON CONFLICT (flow_id, client, key_version_id, purpose) DO UPDATE
                    SET expires_at = t_signing_flow_reference.expires_at
                """, flowId, client, purpose.name(), Timestamp.from(expiry), keyUri);
        if (inserted != 1) throw new IllegalArgumentException("Signing version is no longer usable; resolve it again");
    }

    public List<Reference> active() {
        // Revocation wins even if an unfinished flow still references the version.
        return jdbc.query("""
                SELECT k.tenant_id, k.provider_id, v.pk_key_version_id, v.key_uri, r.client, r.purpose
                FROM t_signing_flow_reference r
                JOIN t_signing_key_version v ON v.pk_key_version_id = r.key_version_id
                JOIN t_signing_key k ON k.pk_key_id = v.key_id
                WHERE r.expires_at > CURRENT_TIMESTAMP AND v.status IN ('ACTIVE', 'PREVIOUS')
                """, (row, index) -> new Reference(row.getString("tenant_id"), row.getInt("provider_id"),
                        row.getObject("pk_key_version_id", UUID.class), row.getString("key_uri"),
                        row.getString("client"), SigningPurpose.valueOf(row.getString("purpose"))));
    }

    @Transactional
    public void release(UUID flowId, String client) {
        jdbc.update("DELETE FROM t_signing_flow_reference WHERE flow_id = ? AND client = ?", flowId, client);
    }

    @Transactional
    public void expire() {
        jdbc.update("DELETE FROM t_signing_flow_reference WHERE expires_at <= CURRENT_TIMESTAMP");
    }

    public record Reference(String tenantId, int providerId, UUID versionId, String keyUri,
                            String client, SigningPurpose purpose) {}
}
