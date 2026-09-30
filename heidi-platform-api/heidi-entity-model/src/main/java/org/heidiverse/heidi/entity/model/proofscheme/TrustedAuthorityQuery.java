// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.proofscheme;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** OpenID4VP Trusted Authorities Query entry. */
public record TrustedAuthorityQuery(
        @NotBlank String type,
        @NotEmpty List<@NotBlank String> values,
        String credentialId) {}
