// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.ws.advice;

import org.heidiverse.heidi.verifier.model.exception.DuplicateSubmissionException;
import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.heidiverse.heidi.verifier.service.problem.OAuthErrorResponse;
import org.heidiverse.heidi.verifier.ws.controller.Oid4vpWalletController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.NoSuchElementException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = Oid4vpWalletController.class)
public class WalletExceptionHandling {

    private static final Logger logger = LoggerFactory.getLogger(WalletExceptionHandling.class);
    private static final String GENERIC_SERVER_ERROR = "Failed to process wallet request.";

    @ExceptionHandler({VpVerificationException.class, DuplicateSubmissionException.class})
    public ResponseEntity<Map<String, Object>> handleInvalidWalletRequest(
            final RuntimeException exception) {
        return OAuthErrorResponse.invalidRequest(exception.getMessage());
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        NoSuchElementException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<Map<String, Object>> handleMalformedWalletRequest(
            final Exception exception) {
        return OAuthErrorResponse.invalidRequest(exception.getMessage());
    }

    @ExceptionHandler
    public ResponseEntity<Map<String, Object>> handleUnexpectedWalletException(
            final Exception exception) {
        logger.warn("Failed to process wallet request", exception);
        return OAuthErrorResponse.serverError(GENERIC_SERVER_ERROR);
    }
}
