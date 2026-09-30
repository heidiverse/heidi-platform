// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

public class I14yClientException extends RuntimeException {

    public I14yClientException(String message) {
        super(message);
    }

    public I14yClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
