// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

import java.util.Set;

/** A provider that can generate key material. Not every backend does. */
public interface SigningKeyCreator {

    /**
     * Generate a key and return a reference to it.
     *
     * @param keyId caller-chosen identifier, used to build the key URI
     * @param algorithm JOSE algorithm name, one of {@link SigningKeyProvider#supportedAlgorithms()}
     */
    SigningKeyRef createKey(String keyId, String algorithm);

    /** Generate a key with an explicit technical usage set. */
    default SigningKeyRef createKey(
            String keyId, String algorithm, Set<SigningKeyUsage> usages) {
        if (usages == null || !Set.of(SigningKeyUsage.SIGN).equals(Set.copyOf(usages))) {
            throw new SigningKeyException("Provider does not support explicit key usages");
        }
        return createKey(keyId, algorithm);
    }
}
