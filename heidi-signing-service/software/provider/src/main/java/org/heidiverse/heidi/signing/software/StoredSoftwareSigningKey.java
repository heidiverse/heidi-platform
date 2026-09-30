// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.software;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;

/** Key material exchanged between the software provider and its storage implementation. */
public record StoredSoftwareSigningKey(
        String keyId,
        String algorithm,
        byte[] privateKey,
        byte[] publicKey,
        boolean deletable,
        Set<SigningKeyUsage> usages) {
    public StoredSoftwareSigningKey(
            String keyId, String algorithm, byte[] privateKey, byte[] publicKey, boolean deletable) {
        this(keyId, algorithm, privateKey, publicKey, deletable, Set.of(SigningKeyUsage.SIGN));
    }

    public StoredSoftwareSigningKey {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("Stored signing key ID must not be blank");
        }
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("Stored signing key algorithm must not be blank");
        }
        Objects.requireNonNull(privateKey, "Stored signing key private material must not be null");
        Objects.requireNonNull(publicKey, "Stored signing key public material must not be null");
        if (privateKey.length == 0 || publicKey.length == 0) {
            throw new IllegalArgumentException("Stored signing key material must not be empty");
        }
        if (usages == null || usages.isEmpty()) {
            throw new IllegalArgumentException("Stored signing key usages must not be empty");
        }
        usages = Set.copyOf(usages);
        privateKey = privateKey.clone();
        publicKey = publicKey.clone();
    }

    @Override
    public byte[] privateKey() {
        return privateKey.clone();
    }

    @Override
    public byte[] publicKey() {
        return publicKey.clone();
    }

    /** Best-effort clearing for transient copies held by a storage implementation. */
    public void clearPrivateKey() {
        Arrays.fill(privateKey, (byte) 0);
    }
}
