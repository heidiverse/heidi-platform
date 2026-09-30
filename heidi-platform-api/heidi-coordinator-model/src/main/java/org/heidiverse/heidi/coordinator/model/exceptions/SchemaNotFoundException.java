// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.exceptions;

public class SchemaNotFoundException extends Exception {
    private final String schemaId;

    public SchemaNotFoundException(String schemaId) {
        this.schemaId = schemaId;
    }

    @Override
    public String getMessage() {
        return "Schema " + this.schemaId + " not found!";
    }
}
