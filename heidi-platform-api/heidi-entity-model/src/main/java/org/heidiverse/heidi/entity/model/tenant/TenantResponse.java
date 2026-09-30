// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.tenant;

import org.heidiverse.heidi.entity.model.entity.TenantEntity;
import org.heidiverse.heidi.entity.model.entity.TrustRegistryEntity;

import tools.jackson.databind.JsonNode;

import java.util.List;

public record TenantResponse(
        String tenantId,
        String displayName,
        String thumbnail,
        List<String> translations,
        String defaultLanguage,
        List<Integer> issuerIds,
        List<TrustRegistryType> trustRegistries,
        boolean deleted,
        JsonNode clientConfiguration) {

    public static TenantResponse from(TenantEntity entity) {
        return new TenantResponse(
                entity.getTenantId(),
                entity.getDisplayName(),
                entity.getThumbnail(),
                entity.getTranslations(),
                entity.getDefaultLanguage(),
                entity.getIssuerIds(),
                entity.getTrustRegistries().stream()
                        .map(TrustRegistryEntity::getTrustRegistry)
                        .toList(),
                entity.isDeleted(),
                entity.getClientConfiguration());
    }
}
