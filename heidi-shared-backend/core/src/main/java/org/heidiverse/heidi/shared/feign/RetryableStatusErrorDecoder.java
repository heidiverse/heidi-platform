// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.feign;

import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;

import org.springframework.http.HttpStatus;

import java.util.Set;

public class RetryableStatusErrorDecoder implements ErrorDecoder {
    private final Set<HttpStatus> retryableStatuses;
    private final ErrorDecoder fallback;

    public RetryableStatusErrorDecoder(Set<HttpStatus> retryableStatuses) {
        this(retryableStatuses, new ErrorDecoder.Default());
    }

    public RetryableStatusErrorDecoder(Set<HttpStatus> retryableStatuses, ErrorDecoder fallback) {
        this.retryableStatuses = retryableStatuses;
        this.fallback = fallback;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        HttpStatus status = HttpStatus.resolve(response.status());
        if (status != null && retryableStatuses.contains(status)) {
            return new RetryableException(
                    response.status(),
                    "Retryable upstream response " + response.status() + " for " + methodKey,
                    response.request().httpMethod(),
                    (Long) null,
                    response.request());
        }
        return fallback.decode(methodKey, response);
    }
}
