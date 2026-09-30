// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.exception;

public class VpVerificationException extends RuntimeException {
    public VpVerificationException(final String reason) {
        super(reason);
    }
}
