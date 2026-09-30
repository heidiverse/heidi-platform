// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.exceptions;

import java.util.UUID;

public class ProofSchemeNotFoundException extends RuntimeException {
    private final UUID id;

    public ProofSchemeNotFoundException(final UUID id) {
        this.id = id;
    }

    @Override
    public String getMessage() {
        return "ProofScheme " + id + " not found";
    }
}
