// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.model.exception;

public class InvalidSdJwtException extends Exception {
    public InvalidSdJwtException(String msg) {
        super(msg);
    }

    public InvalidSdJwtException(String msg, Exception e) {
        super(msg, e);
    }
}
