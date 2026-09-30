// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.ws.advice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import java.util.NoSuchElementException;
import java.util.List;

import org.heidiverse.heidi.verifier.model.exception.DuplicateSubmissionException;
import org.heidiverse.heidi.verifier.model.exception.VerificationResponseNotFoundException;
import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@Order(1)
@RestControllerAdvice
public class ExceptionHandling {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionHandling.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        logValidationFailure(
                request,
                "request body",
                validationErrors(exception.getBindingResult()));
        return problem(HttpStatus.BAD_REQUEST, "Request validation failed.");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleHandlerMethodValidation(
            HandlerMethodValidationException exception, HttpServletRequest request) {
        logValidationFailure(
                request,
                exception.isForReturnValue() ? "response" : "request",
                validationErrors(exception));
        return problem(
                exception.isForReturnValue()
                        ? HttpStatus.INTERNAL_SERVER_ERROR
                        : HttpStatus.BAD_REQUEST,
                exception.isForReturnValue()
                        ? "Response validation failed."
                        : "Request validation failed.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        logValidationFailure(
                request,
                "constraint",
                exception.getConstraintViolations().stream()
                        .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                        .toList());
        return problem(HttpStatus.BAD_REQUEST, "Request validation failed.");
    }

    @ExceptionHandler(VpVerificationException.class)
    ProblemDetail handleVpVerification(VpVerificationException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(DuplicateSubmissionException.class)
    ProblemDetail handleDuplicateSubmission(DuplicateSubmissionException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(VerificationResponseNotFoundException.class)
    ProblemDetail handleVerificationResponseNotFound(
            VerificationResponseNotFoundException exception) {
        var problem = problem(
                HttpStatus.NOT_FOUND,
                "No verification response found for transactionId. The transaction may have"
                        + " expired or already been cleaned up.");
        problem.setTitle("Verification response not found");
        problem.setProperty("error", VerificationResponseNotFoundException.ERROR_CODE);
        problem.setProperty("transactionId", exception.getTransactionId());
        return problem;
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail handleNoSuchElement(NoSuchElementException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException exception) {
        return problem(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        var problem = ProblemDetail.forStatus(status);
        if (detail != null && !detail.isBlank()) {
            problem.setDetail(detail);
        }
        return problem;
    }

    private static List<String> validationErrors(BindingResult bindingResult) {
        return bindingResult.getAllErrors().stream()
                .map(error -> {
                    var name = error instanceof FieldError fieldError
                            ? fieldError.getField()
                            : error.getObjectName();
                    return name + ": " + String.valueOf(error.getDefaultMessage());
                })
                .toList();
    }

    private static List<String> validationErrors(HandlerMethodValidationException exception) {
        return exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> parameterName(result) + ": " + error.getDefaultMessage()))
                .toList();
    }

    private static String parameterName(ParameterValidationResult result) {
        var name = result.getMethodParameter().getParameterName();
        return name != null
                ? name
                : "parameter[" + result.getMethodParameter().getParameterIndex() + "]";
    }

    private static void logValidationFailure(
            HttpServletRequest request, String subject, List<String> errors) {
        LOGGER.warn(
                "{} validation failed for {} {}: {}",
                subject,
                request.getMethod(),
                request.getRequestURI(),
                errors);
    }
}
