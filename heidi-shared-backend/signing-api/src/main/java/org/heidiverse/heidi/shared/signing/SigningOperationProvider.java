// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

import java.util.List;

/** Optional provider-side operations beyond raw message signing. */
public interface SigningOperationProvider {
    /** Stable operation identifiers implemented by this provider. */
    List<String> supportedOperations();

    /** Operations that do not require a signing key reference. */
    default List<String> keylessOperations() {
        return List.of();
    }

    /** Executes an operation whose input and result schemas are defined by the protocol profile. */
    SigningOperationResult execute(SigningKeyRef ref, SigningOperationRequest request);

    /** Executes a provider-level operation without selecting a signing key. */
    default SigningOperationResult executeKeyless(SigningOperationRequest request) {
        throw new SigningKeyException("Provider cannot execute keyless operations");
    }
}
