// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.entity.data;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.heidiverse.heidi.entity.data.repository.IdentityKeyPublicationRepository;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class IdentityKeyPublicationTest extends BaseDataServiceTest {
    @Autowired private JdbcTemplate jdbc;

    @Test
    void retainsPublicKeysAfterUnbinding() {
        var publications = new IdentityKeyPublicationRepository(jdbc);
        var identity = jdbc.queryForObject("INSERT INTO t_issuer(slug, logo) VALUES (?, '') RETURNING pk_issuer_id",
                Integer.class, UUID.randomUUID().toString());
        var key = UUID.randomUUID();
        var version = UUID.randomUUID();
        var provider = jdbc.queryForObject("""
                INSERT INTO t_signing_provider(name, encryption_salt, encrypted_configuration)
                VALUES (?, ?, 'test') RETURNING pk_signing_provider_id
                """, Integer.class, key.toString(), key.toString());
        jdbc.update("INSERT INTO t_signing_key(pk_key_id, logical_key_id, provider_id) VALUES (?, ?, ?)",
                key, key.toString(), provider);
        jdbc.update("""
                INSERT INTO t_signing_key_version(pk_key_version_id, key_id, version,
                    provider_key_id, key_uri, algorithm, public_jwk, status)
                VALUES (?, ?, 1, ?, ?, 'ES256', '{"kid":"published-v1"}', 'ACTIVE')
                """, version, key, version.toString(), "software://" + version);
        publications.retain(identity, IdentityKeySlotType.CREDENTIAL_SIGNING, IssuerTrustSystem.Default, version);
        publications.retain(identity, IdentityKeySlotType.CREDENTIAL_SIGNING, IssuerTrustSystem.Default, version);

        // No live slot or signing authority is needed to publish verification material.
        jdbc.update("UPDATE t_signing_key_version SET status = 'REVOKED' WHERE pk_key_version_id = ?", version);
        assertEquals(List.of("{\"kid\":\"published-v1\"}"),
                publications.publicKeys(identity, IdentityKeySlotType.CREDENTIAL_SIGNING));
        assertTrue(publications.publicKeys(identity, IdentityKeySlotType.FEDERATION).isEmpty());
        assertTrue(publications.referencesKey(key));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM t_signing_key_version WHERE pk_key_version_id = ?", version));
    }
}
