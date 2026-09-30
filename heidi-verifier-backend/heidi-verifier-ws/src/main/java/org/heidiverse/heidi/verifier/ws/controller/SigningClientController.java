// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.ws.controller;

import org.heidiverse.heidi.verifier.service.IdentitySigningService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public half of this verifier's signing-protocol credential, read by the platform so an
 * operator can add it to a signing service. The key differs per provider scheme, so the caller
 * says which signing service it is asking about. Never the seed.
 */
@RestController
public class SigningClientController {
    private final IdentitySigningService identitySigningService;

    public SigningClientController(IdentitySigningService identitySigningService) {
        this.identitySigningService = identitySigningService;
    }

    @GetMapping("/internal/verifier/v1/signing-client")
    public ResponseEntity<IdentitySigningService.ClientKey> getClientKey(
            @RequestParam String provider) {
        var key = identitySigningService.clientPublicKey(provider);
        return key == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(key);
    }
}
