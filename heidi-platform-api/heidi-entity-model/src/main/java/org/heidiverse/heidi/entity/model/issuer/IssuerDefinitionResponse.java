// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import org.heidiverse.heidi.shared.localized.LocalizedValue;

import java.util.List;
import java.util.Set;

public record IssuerDefinitionResponse(
        Integer id,
        String slug,
        String logo,
        LocalizedValue<String> displayName,
        String tenantId,
        IssuerTrustSystem defaultTrustSystem,
        Set<IssuerTrustSystem> trustSystems,
        String customProfileName,
        List<IssuerDisplayConfig> issuerDisplay) {
    public IssuerDefinitionResponse(
            Integer id, String slug, String logo, LocalizedValue<String> displayName) {
        this(id, slug, logo, displayName, null, null, Set.of(), null, List.of());
    }

    public IssuerDefinitionResponse(
            Integer id, String slug, String logo, LocalizedValue<String> displayName,
            String tenantId, IssuerTrustSystem defaultTrustSystem,
            Set<IssuerTrustSystem> trustSystems) {
        this(id, slug, logo, displayName, tenantId, defaultTrustSystem, trustSystems, null, List.of());
    }

    public IssuerDefinitionResponse(
            Integer id, String slug, String logo, LocalizedValue<String> displayName,
            String tenantId, IssuerTrustSystem defaultTrustSystem,
            Set<IssuerTrustSystem> trustSystems, String customProfileName) {
        this(id, slug, logo, displayName, tenantId, defaultTrustSystem,
                trustSystems, customProfileName, List.of());
    }
}
