// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import org.heidiverse.heidi.entity.service.StatusListService;
import org.heidiverse.heidi.entity.service.StatusListTokenService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/public/v1/status-lists")
@CrossOrigin(originPatterns = "*")
public class PublicStatusListController {
    private final StatusListService service;

    public PublicStatusListController(StatusListService service) {
        this.service = service;
    }

    @GetMapping(value = "/{id}", produces = StatusListTokenService.MEDIA_TYPE)
    public ResponseEntity<String> get(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(StatusListTokenService.MEDIA_TYPE))
                .cacheControl(CacheControl.noCache())
                .body(service.publicToken(id));
    }
}
