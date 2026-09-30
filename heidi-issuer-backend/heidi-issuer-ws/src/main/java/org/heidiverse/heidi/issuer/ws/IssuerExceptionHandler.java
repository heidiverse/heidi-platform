// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.ws;

import org.heidiverse.heidi.issuer.service.DpopException;
import org.heidiverse.heidi.issuer.service.Oid4vciProtocolException;
import org.heidiverse.heidi.shared.signing.SigningKeyException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

/** OAuth-shaped errors for the OpenID4VCI endpoints. */
@RestControllerAdvice(assignableTypes = Oid4vciController.class)
public class IssuerExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(IssuerExceptionHandler.class);

    @ExceptionHandler({IllegalArgumentException.class, NoSuchElementException.class})
    ResponseEntity<Map<String, String>> badRequest(RuntimeException exception) {
        LOGGER.warn("Issuer rejected a request: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "invalid_request",
                        "error_description", String.valueOf(exception.getMessage())));
    }

    @ExceptionHandler(SigningKeyException.class)
    ResponseEntity<Map<String, String>> signingKey(SigningKeyException exception) {
        LOGGER.error("Issuer signing key is unavailable: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "signing_key_unavailable",
                        "error_description", String.valueOf(exception.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> serverError(Exception exception) {
        LOGGER.error("Issuer request failed", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "server_error",
                        "error_description", String.valueOf(exception.getMessage())));
    }

    @ExceptionHandler(DpopException.class)
    ResponseEntity<Map<String, String>> dpop(DpopException exception) {
        LOGGER.warn("Issuer rejected a DPoP proof: {}", exception.getMessage());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(
                exception.getUnauthorized() ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST);
        if (exception.getNonce() != null) response.header("DPoP-Nonce", exception.getNonce());
        if (exception.getUnauthorized()) {
            // Only the fixed error code goes into the header; the free-form description stays in the
            // body so that it can never inject header syntax. 'algs' advertises what we will accept.
            response.header(
                    "WWW-Authenticate",
                    "DPoP error=\"" + exception.getErrorCode() + "\", algs=\"ES256\"");
        }
        return response.body(Map.of(
                "error", exception.getErrorCode(),
                "error_description", exception.getMessage()));
    }

    @ExceptionHandler(Oid4vciProtocolException.class)
    ResponseEntity<Map<String, String>> oid4vci(Oid4vciProtocolException exception) {
        LOGGER.warn("Issuer rejected an OpenID4VCI request: {}", exception.getMessage());
        return ResponseEntity.badRequest()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of(
                        "error", exception.getErrorCode(),
                        "error_description", exception.getMessage()));
    }
}
