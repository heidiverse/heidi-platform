// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.credential;

import org.heidiverse.heidi.entity.model.credentialscheme.AttributeType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CredentialData(
        SchemaIdentifier schemaIdentifier, Map<String, AttributeDefinition> attributes) {
    public record SchemaIdentifier(String credentialIdentifier, String version) {}

    public record AttributeDefinition(
            String value,
            AttributeType attributeType,
            Boolean isArray,
            Map<String, String> attributeNameOverrides) {}
}
