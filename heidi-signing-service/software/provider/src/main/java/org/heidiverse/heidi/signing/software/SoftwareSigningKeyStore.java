// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.software;

import java.util.List;
import java.util.Optional;

/** Durable storage boundary for software signing keys. */
public interface SoftwareSigningKeyStore extends AutoCloseable {
    /** Load all keys before the provider starts accepting requests. */
    List<StoredSoftwareSigningKey> load();

    /** Load one key that may have been persisted by another provider instance. */
    Optional<StoredSoftwareSigningKey> find(String keyId);

    /** Store a newly created or imported key. */
    void save(StoredSoftwareSigningKey key);

    /** Remove a key by its provider-local key ID. */
    void delete(String keyId);

    /** In-memory stores use this hook to clear their copies of private material. */
    @Override
    default void close() {}
}
