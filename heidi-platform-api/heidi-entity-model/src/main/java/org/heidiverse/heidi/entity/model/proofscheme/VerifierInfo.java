// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.proofscheme;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** Explicit verifier-info entry sent with a Swiss OpenID4VP authorization request. */
public record VerifierInfo(
        @NotBlank String format,
        @NotBlank String data,
        List<@NotBlank String> credentialIds) {
    public VerifierInfo(String format, String data) {
        this(format, data, null);
    }
}
