// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model;

import java.util.List;

import tools.jackson.databind.JsonNode;

public record TenantResponse(
        String tenantId,
        String thumbnail,
        List<String> translations,
        String defaultLanguage,
        List<Integer> issuerIds,
        List<TrustRegistryType> trustRegistries,
        JsonNode clientConfiguration) {
    public enum TrustRegistryType {
        CH,
        DE
    }
}
