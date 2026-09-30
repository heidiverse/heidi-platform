// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.software;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.heidiverse.heidi.shared.signing.SigningKeyException;

/** Volatile store retained for local development and callers that do not configure a database. */
public final class InMemorySoftwareSigningKeyStore implements SoftwareSigningKeyStore {
    private final Map<String, StoredSoftwareSigningKey> keys = new ConcurrentHashMap<>();

    @Override
    public List<StoredSoftwareSigningKey> load() {
        return new ArrayList<>(keys.values());
    }

    @Override
    public Optional<StoredSoftwareSigningKey> find(String keyId) {
        return Optional.ofNullable(keys.get(keyId));
    }

    @Override
    public void save(StoredSoftwareSigningKey key) {
        if (keys.putIfAbsent(key.keyId(), key) != null) {
            throw new SigningKeyException(
                    "A software signing key already exists: software://" + key.keyId());
        }
    }

    @Override
    public void delete(String keyId) {
        var removed = keys.remove(keyId);
        if (removed == null) {
            throw new SigningKeyException("Unknown software signing key: software://" + keyId);
        }
        removed.clearPrivateKey();
    }

    @Override
    public void close() {
        keys.values().forEach(StoredSoftwareSigningKey::clearPrivateKey);
        keys.clear();
    }
}
