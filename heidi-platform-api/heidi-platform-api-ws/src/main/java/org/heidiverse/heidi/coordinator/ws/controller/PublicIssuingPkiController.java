// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.ws.controller;

import java.util.Base64;
import java.util.UUID;
import org.heidiverse.heidi.entity.service.IssuingPkiService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public CA Issuers AIA retrieval for PID leaf certificates. */
@RestController
@RequestMapping("/public/v1/issuing-pki")
public class PublicIssuingPkiController {
    private final IssuingPkiService service;

    public PublicIssuingPkiController(IssuingPkiService service) { this.service = service; }

    @GetMapping(value = "/{id}/certificates/{fingerprint}", produces = "application/pkix-cert")
    public ResponseEntity<byte[]> certificate(@PathVariable UUID id, @PathVariable String fingerprint) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/pkix-cert"))
                .body(Base64.getDecoder().decode(service.publicCertificate(id, fingerprint)));
    }
}
