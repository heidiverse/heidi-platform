// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.ws.advice;

import feign.FeignException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.heidiverse.heidi.entity.model.exceptions.DuplicateAttributeException;
import org.heidiverse.heidi.entity.model.exceptions.DuplicateCatalogException;
import org.heidiverse.heidi.entity.model.exceptions.CertificateInUseException;
import org.heidiverse.heidi.entity.model.exceptions.IntegrationNotFoundException;
import org.heidiverse.heidi.entity.model.exceptions.InvalidAttributeException;
import org.heidiverse.heidi.entity.model.exceptions.InvalidCredentialSchemeException;
import org.heidiverse.heidi.entity.model.exceptions.InvalidIssuerException;
import org.heidiverse.heidi.entity.model.exceptions.LibraryNotFoundException;
import org.heidiverse.heidi.entity.model.exceptions.ProofSchemeNotFoundException;
import org.heidiverse.heidi.entity.model.exceptions.SchemaNotFoundException;
import org.heidiverse.heidi.entity.model.exceptions.TemplateNotFoundException;
import org.heidiverse.heidi.entity.model.exceptions.TenantNotFoundException;
import org.heidiverse.heidi.entity.model.exceptions.UnauthorizedAccessException;
import org.heidiverse.heidi.shared.signing.SigningProviderException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;
import java.util.List;

@Order(1)
@RestControllerAdvice
public class WsExceptionHandling {
    private static final Logger LOGGER = LoggerFactory.getLogger(WsExceptionHandling.class);
    private static final ResourceBundleMessageSource MESSAGES = messages();

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentialsException(BadCredentialsException exception) {
        return problem(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        logValidationFailure(
                request,
                "request body",
                validationErrors(exception.getBindingResult()));
        return problem(HttpStatus.BAD_REQUEST, "Request validation failed.");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleHandlerMethodValidation(
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
    public ProblemDetail handleConstraintViolation(
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
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, message(exception.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ProblemDetail handleSecurityException(SecurityException exception) {
        return problem(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(AccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler({
        InvalidAttributeException.class,
        InvalidCredentialSchemeException.class,
        InvalidIssuerException.class
    })
    public ProblemDetail handleBadRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({
        IntegrationNotFoundException.class,
        LibraryNotFoundException.class,
        ProofSchemeNotFoundException.class,
        SchemaNotFoundException.class,
        TemplateNotFoundException.class,
        TenantNotFoundException.class
    })
    public ProblemDetail handleNotFound(Exception exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({CertificateInUseException.class, DuplicateAttributeException.class, DuplicateCatalogException.class})
    public ProblemDetail handleConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataConflict(DataIntegrityViolationException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "A resource with the same unique values already exists.");
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ProblemDetail handleUnauthorizedAccess(UnauthorizedAccessException exception) {
        return problem(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(SigningProviderException.class)
    public ProblemDetail handleSigningProviderException(SigningProviderException exception) {
        return problem(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    @ExceptionHandler(FeignException.class)
    public ProblemDetail handleFeignException(FeignException exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "External service error: " + exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
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

    private static String message(String code) {
        if (code == null) return null;
        return MESSAGES.getMessage(code, null, code, Locale.getDefault());
    }

    private static ResourceBundleMessageSource messages() {
        var source = new ResourceBundleMessageSource();
        source.setBasenames("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }
}
