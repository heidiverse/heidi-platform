// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

/** A signing key operation failed. Unchecked, because no caller can meaningfully recover. */
public class SigningKeyException extends RuntimeException {

    public SigningKeyException(String message) {
        super(message);
    }

    public SigningKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
