// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import static org.heidiverse.heidi.platformapi.ws.config.ApiDocumentationConfig.TAG_CAPABILITIES;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.heidiverse.heidi.entity.model.integration.IntegrationAuthorizedCredentialsResponse;
import org.heidiverse.heidi.entity.service.IntegrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** API-key-scoped credential access for integrating backends. */
@RestController
@Tag(
        name = TAG_CAPABILITIES,
        description =
                "Lets an integrating backend inspect which credential schemas and scopes its API"
                        + " key may use before starting a process.")
public class IntegrationCredentialController {

    private final IntegrationService service;

    public IntegrationCredentialController(IntegrationService service) {
        this.service = service;
    }

    @Operation(
            summary = "List credential schemas allowed for an API key",
            description =
                    "Returns the organisation, scopes, and published credential schemas assigned"
                            + " to the supplied `X-API-KEY`. Use these identifiers when building an"
                            + " issuance initialization request. This endpoint accepts only the"
                            + " `X-API-KEY` header form.")
    @GetMapping("/integration/v1/credentials")
    public ResponseEntity<IntegrationAuthorizedCredentialsResponse> get(
            @RequestHeader("X-API-KEY") String apiKey) {
        var credentials = service.findCredentialsByApiKey(apiKey);
        var scopes = service.findScopesByApiKey(apiKey);
        var tenantId = service.findTenantIdByApiKey(apiKey);
        if (credentials.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(
                new IntegrationAuthorizedCredentialsResponse(credentials, scopes, tenantId));
    }
}
