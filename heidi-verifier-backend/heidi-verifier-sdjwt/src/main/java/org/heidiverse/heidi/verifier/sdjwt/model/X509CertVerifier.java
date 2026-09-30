// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.model;

import org.jetbrains.annotations.NotNull;

import java.security.cert.X509Certificate;
import java.util.List;

public interface X509CertVerifier {
    boolean validCertChain(@NotNull final List<X509Certificate> x509Chain);

    default void setValidationDate(java.time.Instant instant) {
        // optional; implementations may override
    }
}
