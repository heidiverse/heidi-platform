// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.exceptions;

import java.util.UUID;

public class SchemaNotFoundException extends Exception {
    private final UUID schemaId;
    private final String ocaBundleFileNameOrIdentifier;

    public SchemaNotFoundException(UUID schemaId) {
        this.schemaId = schemaId;
        this.ocaBundleFileNameOrIdentifier = null;
    }
    public SchemaNotFoundException(String ocaBundleFileNameOrIdentifier) {
        this.schemaId = null;
        this.ocaBundleFileNameOrIdentifier = ocaBundleFileNameOrIdentifier;
    }

    @Override
    public String getMessage() {
        return "Schema " + (this.schemaId != null? this.schemaId : this.ocaBundleFileNameOrIdentifier) + " not found!";
    }
}
