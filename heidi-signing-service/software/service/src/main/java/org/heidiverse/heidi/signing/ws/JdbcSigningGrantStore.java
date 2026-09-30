// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.ws;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.heidiverse.heidi.shared.signing.SigningKeyException;
import org.heidiverse.heidi.shared.signing.SigningPurpose;
import org.heidiverse.heidi.signing.server.SigningGrantStore;
import org.heidiverse.heidi.signing.server.SigningGrants.Grant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL store for what a client may do with a logical-key scope. */
public class JdbcSigningGrantStore implements SigningGrantStore {
    private static final String PURPOSE_SEPARATOR = ",";

    private final JdbcTemplate jdbc;

    public JdbcSigningGrantStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template must not be null");
    }

    @Override
    public Optional<List<Grant>> find(String scope) {
        try {
            ResultSetExtractor<Boolean> exists = result -> result.next();
            var written = Boolean.TRUE.equals(jdbc.query(
                    "SELECT 1 FROM t_signing_grant_policy WHERE scope = ?", exists, scope));
            if (!written) return Optional.empty();

            return Optional.of(jdbc.query(
                    "SELECT client_name, purposes FROM t_signing_grant WHERE scope = ?",
                    result -> {
                        var grants = new ArrayList<Grant>();
                        while (result.next()) {
                            grants.add(new Grant(
                                    result.getString("client_name"),
                                    purposes(result.getString("purposes"))));
                        }
                        return grants;
                    },
                    scope));
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not load the scope's grants", exception);
        }
    }

    @Override
    @Transactional
    public void replace(String scope, List<Grant> grants) {
        try {
            jdbc.update(
                    "INSERT INTO t_signing_grant_policy(scope) VALUES (?) "
                            + "ON CONFLICT (scope) DO NOTHING",
                    scope);
            jdbc.update("DELETE FROM t_signing_grant WHERE scope = ?", scope);
            for (var grant : grants) {
                jdbc.update(
                        "INSERT INTO t_signing_grant(scope, client_name, purposes) "
                                + "VALUES (?, ?, ?)",
                        scope,
                        grant.client(),
                        grant.purposes().stream()
                                .map(SigningPurpose::wireValue)
                                .sorted()
                                .collect(Collectors.joining(PURPOSE_SEPARATOR)));
            }
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not persist the scope's grants", exception);
        }
    }

    @Override
    public void revoke(String scope) {
        jdbc.update("INSERT INTO t_signing_key_revocation(scope) VALUES (?) ON CONFLICT (scope) DO NOTHING", scope);
    }

    @Override
    public boolean isRevoked(String scope) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM t_signing_key_revocation WHERE scope = ?)", Boolean.class, scope));
    }

    @Override
    public Set<String> scopes() {
        try {
            return Set.copyOf(jdbc.query(
                    "SELECT scope FROM t_signing_grant_policy",
                    (result, row) -> result.getString("scope")));
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not list signing grant scopes", exception);
        }
    }

    private static java.util.Set<SigningPurpose> purposes(String stored) {
        return Arrays.stream(stored.split(PURPOSE_SEPARATOR))
                .map(SigningPurpose::of)
                .collect(Collectors.toSet());
    }
}
