// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.exceptions;

public class DuplicateCatalogException extends RuntimeException {
    public DuplicateCatalogException(String message) {
        super(message);
    }
}
