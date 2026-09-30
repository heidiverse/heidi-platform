// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.heidiverse.heidi.signing.server.SigningGrants.Grant;

/** Process-local grants, used where keys themselves are process-local. */
public final class InMemorySigningGrantStore implements SigningGrantStore {
    private final Set<String> revoked = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, List<Grant>> grants = new ConcurrentHashMap<>();

    @Override
    public Optional<List<Grant>> find(String scope) {
        return Optional.ofNullable(grants.get(scope));
    }

    @Override
    public void replace(String scope, List<Grant> replacement) {
        grants.put(scope, List.copyOf(replacement));
    }

    @Override public void revoke(String scope) { revoked.add(scope); }
    @Override public boolean isRevoked(String scope) { return revoked.contains(scope); }

    @Override
    public Set<String> scopes() {
        return Set.copyOf(grants.keySet());
    }
}
