// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.ws.controller;

import org.heidiverse.heidi.verifier.service.FederationService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** A verifier identity's OpenID Federation entity configuration at {verifier}/{identity}. */
@RestController
public class FederationController {
    private static final MediaType STATEMENT = MediaType.valueOf("application/entity-statement+jwt");

    private final FederationService federationService;

    public FederationController(FederationService federationService) {
        this.federationService = federationService;
    }

    @GetMapping("/{identity}/.well-known/openid-federation")
    public ResponseEntity<String> getEntityConfiguration(@PathVariable String identity) {
        return federationService.entityConfiguration(identity)
                .map(statement -> ResponseEntity.ok().contentType(STATEMENT).body(statement))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
