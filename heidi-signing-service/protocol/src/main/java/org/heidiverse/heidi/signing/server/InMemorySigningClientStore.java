// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Process-local store used when no signing database is configured. */
public final class InMemorySigningClientStore implements SigningClientStore {
    private final Map<String, byte[]> clients = new ConcurrentHashMap<>();

    @Override
    public Map<String, byte[]> findAll() {
        var copy = new LinkedHashMap<String, byte[]>();
        clients.forEach((name, key) -> copy.put(name, key.clone()));
        return Map.copyOf(copy);
    }

    @Override
    public void put(String client, byte[] publicKey) {
        clients.put(client, publicKey.clone());
    }

    @Override
    public void remove(String client) {
        clients.remove(client);
    }
}
