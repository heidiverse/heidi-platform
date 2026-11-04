// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/** Localized issuer branding published in OID4VCI metadata. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IssuerDisplayConfig(
        @NotBlank String locale,
        @NotBlank String name,
        String logoUri,
        String logoAltText) {}
