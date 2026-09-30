// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.problem;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OAuthErrorResponse {

    public static final String INVALID_REQUEST = "invalid_request";
    public static final String SERVER_ERROR = "server_error";

    private OAuthErrorResponse() {}

    public static ResponseEntity<Map<String, Object>> invalidRequest(final String description) {
        return error(HttpStatus.BAD_REQUEST, INVALID_REQUEST, description);
    }

    public static ResponseEntity<Map<String, Object>> serverError(final String description) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, SERVER_ERROR, description);
    }

    private static ResponseEntity<Map<String, Object>> error(
            final HttpStatus status, final String code, final String description) {
        final Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", code);
        error.put("error_description", description);

        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(error);
    }
}
