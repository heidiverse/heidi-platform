// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.entity.data.repository;

import java.util.List;
import java.util.UUID;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Append-only identity references; the version remains the public material's source of truth. */
@Repository
public class IdentityKeyPublicationRepository {
    private final JdbcTemplate jdbc;
    public IdentityKeyPublicationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void retain(int identityId, IdentityKeySlotType type, IssuerTrustSystem trust, UUID versionId) {
        jdbc.update("""
                INSERT INTO t_identity_key_publication(identity_id, slot_type, trust_system, key_version_id)
                VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, identityId, type.name(), trust == null ? IssuerTrustSystem.Default.name() : trust.name(), versionId);
    }

    public List<String> publicKeys(int identityId, IdentityKeySlotType type) {
        return jdbc.queryForList("""
                SELECT DISTINCT v.public_jwk FROM t_identity_key_publication p
                JOIN t_signing_key_version v ON v.pk_key_version_id = p.key_version_id
                WHERE p.identity_id = ? AND p.slot_type = ?
                """, String.class, identityId, type.name());
    }

    public List<String> publicKeys(
            int identityId, IdentityKeySlotType type, IssuerTrustSystem trustSystem) {
        return jdbc.queryForList("""
                SELECT DISTINCT v.public_jwk FROM t_identity_key_publication p
                JOIN t_signing_key_version v ON v.pk_key_version_id = p.key_version_id
                WHERE p.identity_id = ? AND p.slot_type = ? AND p.trust_system = ?
                """, String.class, identityId, type.name(),
                trustSystem == null ? IssuerTrustSystem.Default.name() : trustSystem.name());
    }

    public boolean referencesKey(UUID keyId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM t_identity_key_publication p
                    JOIN t_signing_key_version v ON v.pk_key_version_id = p.key_version_id
                    WHERE v.key_id = ?)
                """, Boolean.class, keyId));
    }
}
