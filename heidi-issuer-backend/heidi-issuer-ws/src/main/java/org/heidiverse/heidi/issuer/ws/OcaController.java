// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.ws;

import org.heidiverse.heidi.issuer.service.OcaBundleService;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
public class OcaController {
    private final OcaBundleService ocaBundleService;

    public OcaController(OcaBundleService ocaBundleService) {
        this.ocaBundleService = ocaBundleService;
    }

    @CrossOrigin(origins = "*")
    @GetMapping(path = "/oca/{fileName}.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getOcaBundle(
            @PathVariable String fileName,
            @RequestParam(required = false) String format,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .varyBy(HttpHeaders.USER_AGENT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ocaBundleService.resolve(fileName, format, userAgent));
    }
}
