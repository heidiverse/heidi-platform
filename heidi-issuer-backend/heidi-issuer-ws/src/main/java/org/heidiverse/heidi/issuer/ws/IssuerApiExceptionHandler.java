// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.ws;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import java.util.NoSuchElementException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/** RFC 9457 errors for issuer management and integration endpoints. */
@Order(1)
@RestControllerAdvice(assignableTypes = {
    CoordinatorIntegrationController.class,
    DiscoveryController.class,
    OcaController.class,
    PublicHealthController.class
})
public class IssuerApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(IssuerApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail methodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        logValidationFailure(
                request,
                "request body",
                validationErrors(exception.getBindingResult()));
        return problem(HttpStatus.BAD_REQUEST, "Request validation failed.");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handlerMethodValidation(
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
    ProblemDetail constraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        logValidationFailure(
                request,
                "constraint",
                exception.getConstraintViolations().stream()
                        .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                        .toList());
        return problem(HttpStatus.BAD_REQUEST, "Request validation failed.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
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
