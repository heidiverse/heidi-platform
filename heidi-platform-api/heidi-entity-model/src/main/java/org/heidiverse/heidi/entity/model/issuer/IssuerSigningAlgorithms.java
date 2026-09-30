// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import java.util.List;

/** Algorithms supported by the bundled Heidi signing-provider interface. */
public final class IssuerSigningAlgorithms {
    /** Experimental provider-backed algorithm used by the BBS data-integrity profile. */
    public static final String BBS = "BBS";

    public static final List<String> SUPPORTED =
            List.of(
                    "ES256",
                    "ES384",
                    "ES512",
                    "EdDSA",
                    "PS256",
                    "PS384",
                    "PS512",
                    "RS256",
                    "RS384",
                    "RS512",
                    "ML-DSA-44",
                    "ML-DSA-65",
                    "ML-DSA-87",
                    BBS);

    private IssuerSigningAlgorithms() {}

    public static String requireSupported(String algorithm) {
        if (!SUPPORTED.contains(algorithm)) {
            throw new IllegalArgumentException(
                    "Unsupported signing algorithm. Supported algorithms: " + SUPPORTED);
        }
        return algorithm;
    }
}
