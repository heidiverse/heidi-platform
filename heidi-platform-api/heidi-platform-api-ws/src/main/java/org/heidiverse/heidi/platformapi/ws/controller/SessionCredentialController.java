// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.controller;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Lets the Cockpit relay its own already-authenticated session credential to endpoints
 * that must stay reachable without a platform JWT (e.g. the partner-facing integration
 * API), instead of holding a second, independently obtained token in the browser.
 *
 * <p>This path is intentionally left out of {@code DEFAULT_PUBLIC_PATHS}, so Spring
 * Security's {@code anyRequest().authenticated()} rule only lets a request reach this
 * method once the incoming {@code Authorization} header has already been verified as a
 * valid JWT by the OAuth2 resource server filter (or, under the {@code no-security}
 * profile, once {@code PlatformApiNoSecurityConfig} has installed its synthetic one).
 * The value returned here is exactly the credential the caller already presented on this
 * request; no new privilege is created and nothing is minted on the caller's behalf.
 */
@RestController
@RequestMapping("/management/v1/session")
public class SessionCredentialController {

    @Operation(hidden = true)
    @GetMapping("/authorization")
    public Map<String, String> getAuthorization(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "No authenticated session credential present");
        }
        return Map.of("authorization", authorizationHeader);
    }
}
