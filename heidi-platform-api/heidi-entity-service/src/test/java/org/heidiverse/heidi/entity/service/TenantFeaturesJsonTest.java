// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.heidiverse.heidi.entity.model.tenant.TenantFeatures;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class TenantFeaturesJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void preservesExtensionFeatureFlagsAsTopLevelProperties() throws Exception {
        var features =
                objectMapper.readValue(
                        "{"
                                + "\"credentialSchemas\":true,"
                                + "\"proofSchemas\":true,"
                                + "\"integrations\":true,"
                                + "\"testing\":true,"
                                + "\"apiDocs\":true,"
                                + "\"extensionFeatureA\":true,"
                                + "\"extensionFeatureB\":true,"
                                + "\"user\":true,"
                                + "\"tenant\":true,"
                                + "\"settings\":true,"
                                + "\"extensionFeature\":false"
                                + "}",
                        TenantFeatures.class);

        assertFalse(features.getExtensionFeatures().get("extensionFeature"));
        assertTrue(features.getExtensionFeatures().get("testing"));
        assertTrue(features.getExtensionFeatures().get("extensionFeatureA"));
        assertTrue(features.getExtensionFeatures().get("extensionFeatureB"));
        var json = objectMapper.valueToTree(features);
        assertTrue(json.has("credentialSchemas"));
        assertFalse(json.get("extensionFeature").asBoolean());
    }
}
