// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.exceptions;

public class InvalidCredentialSchemeException extends Exception {
    // Default constructor with a standard error message
    public InvalidCredentialSchemeException() {
        super("There is already an existing credential schema with this identifier and version.");
    }

    // Constructor for custom error messages
    public InvalidCredentialSchemeException(String message) {
        super(message);
    }
}
