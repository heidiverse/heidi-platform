// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.integration;

import com.fasterxml.jackson.annotation.JsonValue;

public enum IntegrationScope {
    VERIFY("verify"),
    ISSUE("issue");

    private final String value;

    IntegrationScope(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
