// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

/** A remote signing provider could not complete a request. */
public class SigningProviderException extends SigningKeyException {

    public SigningProviderException(String message) {
        super(message);
    }

    public SigningProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
