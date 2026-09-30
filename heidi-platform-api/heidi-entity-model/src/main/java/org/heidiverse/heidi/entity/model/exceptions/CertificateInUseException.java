// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.exceptions;

public class CertificateInUseException extends RuntimeException {
    public CertificateInUseException(String message) {
        super(message);
    }
}
