// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import org.springframework.http.HttpStatus;

/** A protocol-level error that can be safely exposed to a signing client. */
public final class SigningProtocolException extends RuntimeException {
    private final HttpStatus status;

    public SigningProtocolException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
