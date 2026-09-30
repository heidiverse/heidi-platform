// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.credentialscheme;

import org.heidiverse.heidi.shared.localized.LocalizedValue;
import org.heidiverse.heidi.entity.model.CredentialMetadata;

import tools.jackson.databind.JsonNode;

import java.util.Map;

public record SchemaCreationRequestMultipleLanguages(
        Map<String, AttributeDetails> attributes, JsonNode style, CredentialMetadata metadata) {
    public record AttributeDetails(
            LocalizedValue<String> displayName,
            AttributeType fieldType,
            boolean isArray,
            boolean isSensitive,
            boolean isDisclosable) {}
}
