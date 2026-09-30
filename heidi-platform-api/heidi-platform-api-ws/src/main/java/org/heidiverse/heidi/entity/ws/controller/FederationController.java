// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import org.heidiverse.heidi.entity.service.IdentityFederationService;
import org.heidiverse.heidi.entity.service.IdentityFederationService.FederationEntityNotFoundException;
import org.heidiverse.heidi.entity.service.IdentityFederationService.FederationInvalidRequestException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Entity endpoints of identities acting as OpenID Federation authorities. */
@RestController
@RequestMapping("/federation/{slug}")
public class FederationController {
    private static final MediaType STATEMENT =
            MediaType.valueOf(IdentityFederationService.STATEMENT_MEDIA_TYPE);

    private final IdentityFederationService federationService;

    public FederationController(IdentityFederationService federationService) {
        this.federationService = federationService;
    }

    @GetMapping("/.well-known/openid-federation")
    public ResponseEntity<String> getEntityConfiguration(@PathVariable String slug) {
        return statement(federationService.authorityConfiguration(slug));
    }

    @GetMapping("/fetch")
    public ResponseEntity<String> getSubordinateStatement(
            @PathVariable String slug, @RequestParam("sub") String subject) {
        return statement(federationService.subordinateStatement(slug, subject));
    }

    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> getSubordinates(@PathVariable String slug) {
        return federationService.subordinates(slug);
    }

    // Error responses as OpenID Federation defines them.
    @ExceptionHandler(FederationEntityNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(FederationEntityNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "not_found", exception.getMessage());
    }

    @ExceptionHandler(FederationInvalidRequestException.class)
    ResponseEntity<Map<String, String>> invalidRequest(FederationInvalidRequestException exception) {
        return error(HttpStatus.BAD_REQUEST, "invalid_request", exception.getMessage());
    }

    private static ResponseEntity<String> statement(String jwt) {
        return ResponseEntity.ok().contentType(STATEMENT).body(jwt);
    }

    private static ResponseEntity<Map<String, String>> error(
            HttpStatus status, String code, String description) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", code, "error_description", description));
    }
}
