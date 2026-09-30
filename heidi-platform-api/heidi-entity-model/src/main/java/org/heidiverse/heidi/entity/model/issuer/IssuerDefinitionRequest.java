// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import org.heidiverse.heidi.shared.localized.LocalizedValue;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record IssuerDefinitionRequest(
        @NotBlank String slug,
        String logo,
        LocalizedValue<String> displayName,
        IssuerTrustSystem defaultTrustSystem,
        Set<IssuerTrustSystem> trustSystems,
        String customProfileName) {
    public IssuerDefinitionRequest(
            String slug, String logo, LocalizedValue<String> displayName) {
        this(slug, logo, displayName, null, null, null);
    }
}
