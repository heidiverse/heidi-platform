// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import java.time.Instant;
import java.util.Optional;

/** Durable boundary for registered-key credentials and replay protection. */
public interface RegisteredKeyAuthenticationStore {
    void create(String registrationId, byte[] psk, String client);

    Optional<RegistrationRecord> find(String registrationId);

    /** The completed registration of a client that comes back, so registering stays idempotent. */
    Optional<RegistrationRecord> findByClient(String client, byte[] publicKey);

    boolean hasCompletedRegistration(String client);

    /** Records the last authenticated request for a registration. */
    void recordUse(String registrationId, Instant usedAt);

    /** Returns the most recent use of any completed registration for a client. */
    Optional<Instant> lastUse(String client);

    boolean setPublicKeyIfAbsent(String registrationId, byte[] publicKey);

    boolean recordSignatureIfAbsent(String signature, long seenAt);

    void removeSignature(String signature, long seenAt);

    void purgeSignaturesBefore(long cutoff);

    /** Removes registrations that were opened but never completed before the cutoff. */
    default void purgeUncompletedBefore(Instant cutoff) {}

    record RegistrationRecord(String id, byte[] psk, byte[] publicKey, String client) {
        public RegistrationRecord {
            psk = psk.clone();
            publicKey = publicKey == null ? null : publicKey.clone();
        }

        @Override
        public byte[] psk() {
            return psk.clone();
        }

        @Override
        public byte[] publicKey() {
            return publicKey == null ? null : publicKey.clone();
        }
    }
}
