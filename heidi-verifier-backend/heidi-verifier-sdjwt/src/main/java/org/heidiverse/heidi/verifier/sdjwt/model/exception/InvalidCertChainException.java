// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.model.exception;

public class InvalidCertChainException extends RuntimeException {
    public InvalidCertChainException(String msg) {
        super(msg);
    }

    public InvalidCertChainException(String msg, Exception e) {
        super(msg, e);
    }
}
