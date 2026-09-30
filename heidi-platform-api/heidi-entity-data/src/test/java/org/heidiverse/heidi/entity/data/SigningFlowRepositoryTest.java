// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.entity.data;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import org.heidiverse.heidi.entity.data.repository.SigningFlowRepository;
import org.heidiverse.heidi.shared.signing.SigningPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SigningFlowRepositoryTest extends BaseDataServiceTest {
    private SigningFlowRepository flows;
    @Autowired private JdbcTemplate jdbc;
    private UUID key;
    private UUID version;
    private String uri;
    private final Instant expiry = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS).plus(Duration.ofMinutes(5));

    @BeforeEach
    void createVersion() {
        flows = new SigningFlowRepository(jdbc);
        key = UUID.randomUUID();
        version = UUID.randomUUID();
        uri = "software://kc/" + key + "/" + version;
        var provider = jdbc.queryForObject("""
                INSERT INTO t_signing_provider(name, encryption_salt, encrypted_configuration)
                VALUES (?, ?, 'test') RETURNING pk_signing_provider_id
                """, Integer.class, key.toString(), key.toString());
        jdbc.update("INSERT INTO t_signing_key(pk_key_id, logical_key_id, provider_id) VALUES (?, ?, ?)",
                key, key.toString(), provider);
        jdbc.update("""
                INSERT INTO t_signing_key_version(pk_key_version_id, key_id, version,
                    provider_key_id, key_uri, algorithm, public_jwk, status)
                VALUES (?, ?, 1, ?, ?, 'ES256', '{}', 'ACTIVE')
                """, version, key, version.toString(), uri);
    }

    @Test
    void retainsUntilLastFlowEnds() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        flows.retain(first, "verifier", uri, SigningPurpose.SIGNING, expiry);
        flows.retain(second, "verifier", uri, SigningPurpose.SIGNING, expiry);
        jdbc.update("""
                UPDATE t_signing_key_version SET status = 'PREVIOUS',
                    previous_until = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE pk_key_version_id = ?
                """, version);
        assertEquals(2, flows.active().size());
        assertTrue(flows.referencesKey(key));
        flows.release(first, "verifier");
        assertEquals(1, flows.active().size());
        flows.release(second, "verifier");
        assertTrue(flows.active().isEmpty());
        assertFalse(flows.referencesKey(key));
    }

    @Test
    void revocationOverridesFlow() {
        flows.retain(UUID.randomUUID(), "verifier", uri, SigningPurpose.SIGNING, expiry);
        jdbc.update("UPDATE t_signing_key_version SET status = 'REVOKED' WHERE pk_key_version_id = ?", version);
        assertTrue(flows.active().isEmpty());
        assertTrue(flows.referencesKey(key));
        assertThrows(IllegalArgumentException.class,
                () -> flows.retain(UUID.randomUUID(), "verifier", uri, SigningPurpose.SIGNING, expiry));
    }

    @Test
    void retriesCannotExtendLifetime() {
        var flow = UUID.randomUUID();
        flows.retain(flow, "verifier", uri, SigningPurpose.SIGNING, expiry);
        flows.retain(flow, "verifier", uri, SigningPurpose.SIGNING, expiry.plus(Duration.ofDays(1)));
        var saved = jdbc.queryForObject("SELECT expires_at FROM t_signing_flow_reference WHERE flow_id = ?",
                java.sql.Timestamp.class, flow).toInstant();
        assertEquals(expiry.truncatedTo(java.time.temporal.ChronoUnit.MICROS), saved);
        jdbc.update("UPDATE t_signing_flow_reference SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'");
        assertTrue(flows.active().isEmpty());
        flows.expire();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM t_signing_flow_reference", Integer.class));
    }
}
