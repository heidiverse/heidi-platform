// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.exception;

public class VerificationResponseNotFoundException extends RuntimeException {

    public static final String ERROR_CODE = "verification_response_not_found";
    private final String transactionId;

    public VerificationResponseNotFoundException(final String transactionId) {
        super("No verification response found for transactionId");
        this.transactionId = transactionId;
    }

    public String getTransactionId() {
        return transactionId;
    }
}
