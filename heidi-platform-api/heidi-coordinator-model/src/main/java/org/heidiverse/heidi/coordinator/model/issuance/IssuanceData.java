// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.issuance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IssuanceData(
        SchemaIdentifier schemaIdentifier, Map<String, Object> values) {
    public record SchemaIdentifier(String credentialIdentifier, String version) {}
}
