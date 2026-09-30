// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

import java.util.Set;

/**
 * A provider that accepts externally generated key material.
 *
 * <p>Rare outside software and Vault-style backends: an HSM or a qualified signature service
 * generally insists that keys originate inside it, which is much of the point of using one.
 * The private JWK is the provider-neutral transport form. An implementation may translate it to
 * the backend's native import format; a provider that cannot accept externally generated material
 * must not implement this interface and reports {@code canImport=false} instead.
 */
public interface SigningKeyImporter {

    SigningKeyRef importKey(String keyId, String privateJwk, String algorithm);

    /** Import a key with an explicit technical usage set. */
    default SigningKeyRef importKey(
            String keyId, String privateJwk, String algorithm, Set<SigningKeyUsage> usages) {
        if (usages == null || !Set.of(SigningKeyUsage.SIGN).equals(Set.copyOf(usages))) {
            throw new SigningKeyException("Provider does not support explicit key usages");
        }
        return importKey(keyId, privateJwk, algorithm);
    }
}
