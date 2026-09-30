// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.exceptions;

import java.util.UUID;

public class IntegrationNotFoundException extends RuntimeException {
    private final UUID id;

    public IntegrationNotFoundException(final UUID id) {
        this.id = id;
    }

    @Override
    public String getMessage() {
        return "Integration " + id + " not found";
    }
}
