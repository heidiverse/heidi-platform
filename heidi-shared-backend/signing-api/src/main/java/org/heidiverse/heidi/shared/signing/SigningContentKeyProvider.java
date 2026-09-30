// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

import java.util.List;

/** Optional provider operation that derives or unwraps one JWE content-encryption key. */
public interface SigningContentKeyProvider {
    /** JWE key-management algorithms accepted by the provider. */
    List<String> contentKeyAlgorithms();

    /** Derives the content key without exposing the provider's private key. */
    byte[] contentKey(SigningKeyRef ref, SigningContentKeyRequest request);
}
