// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.profile;

import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.proofscheme.VerifierClientIdScheme;

/** Immutable protocol policy resolved from one versioned ecosystem profile. */
public record EcosystemProfilePolicy(
        IssuerTrustSystem trustSystem,
        VerifierClientIdScheme clientIdScheme,
        String responseMode,
        AlgorithmConstraints signingAlgorithms,
        EncryptionAlgorithmConstraints encryptionAlgorithms) {
    public EcosystemProfilePolicy {
        if (responseMode == null || responseMode.isBlank()) {
            throw new IllegalArgumentException("Response mode is required");
        }
        if (signingAlgorithms == null || encryptionAlgorithms == null) {
            throw new IllegalArgumentException("Algorithm constraints are required");
        }
    }
}
