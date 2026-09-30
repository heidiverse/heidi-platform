// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.model.api;

import jakarta.validation.constraints.NotBlank;

public record OidcAuthSuccessRequest(
        @NotBlank String connectionId, @NotBlank String processToken) {}
