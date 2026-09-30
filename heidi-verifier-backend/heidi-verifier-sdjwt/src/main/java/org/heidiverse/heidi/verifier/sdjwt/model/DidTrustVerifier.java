// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.model;

import org.heidiverse.heidi.verifier.sdjwt.model.exception.InvalidSdJwtException;

/** Establishes trust in the DID document used for an issuer signature. */
@FunctionalInterface
public interface DidTrustVerifier {
    void verifyTrustedDid(String did) throws InvalidSdJwtException;
}
