// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.exceptions;

public class InvalidAttributeException extends Exception {
    private final int id;

    public InvalidAttributeException(final int id) {
        this.id = id;
    }

    @Override
    public String getMessage() {
        return "Invalid attribute " + id;
    }
}
