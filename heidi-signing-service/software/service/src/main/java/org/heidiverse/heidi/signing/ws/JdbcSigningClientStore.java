// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.ws;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.heidiverse.heidi.shared.signing.SigningKeyException;
import org.heidiverse.heidi.signing.server.SigningClientStore;
import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL store for accepted signing client keys. */
public final class JdbcSigningClientStore implements SigningClientStore {
    private final JdbcTemplate jdbc;

    public JdbcSigningClientStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template must not be null");
    }

    @Override
    public Map<String, byte[]> findAll() {
        try {
            return jdbc.query(
                    "SELECT client_name, public_key FROM t_signing_auth_client",
                    result -> {
                        var clients = new LinkedHashMap<String, byte[]>();
                        while (result.next()) {
                            clients.put(
                                    result.getString("client_name"),
                                    Base64.getDecoder().decode(result.getString("public_key")));
                        }
                        return Map.copyOf(clients);
                    });
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not load accepted signing clients", exception);
        }
    }

    @Override
    public void put(String client, byte[] publicKey) {
        try {
            jdbc.update(
                    "INSERT INTO t_signing_auth_client(client_name, public_key) VALUES (?, ?) "
                            + "ON CONFLICT (client_name) DO UPDATE SET public_key = EXCLUDED.public_key, "
                            + "updated_at = CURRENT_TIMESTAMP",
                    client,
                    Base64.getEncoder().encodeToString(publicKey));
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not persist accepted signing client", exception);
        }
    }

    @Override
    public void remove(String client) {
        try {
            jdbc.update("DELETE FROM t_signing_auth_client WHERE client_name = ?", client);
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not withdraw accepted signing client", exception);
        }
    }
}
