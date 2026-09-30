// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.exception;

public class DuplicateSubmissionException extends RuntimeException {
    public DuplicateSubmissionException(String requestId) {
        super(
                String.format(
                        "Presentation request %s has already been fulfilled", requestId));
    }
}
