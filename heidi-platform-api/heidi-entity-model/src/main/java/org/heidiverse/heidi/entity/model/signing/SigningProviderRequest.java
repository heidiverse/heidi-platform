// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.signing;

import jakarta.validation.constraints.NotBlank;

/** Connection settings for a tenant signing provider. Secrets are accepted only on write. */
public record SigningProviderRequest(
        @NotBlank String name,
        String endpoint,
        String bearerToken,
        Boolean defaultProvider,
        String authenticationMode) {}
