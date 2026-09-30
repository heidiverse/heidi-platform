// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.heidiverse.heidi.entity.model.context.CredentialContextResponse;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeDetailResponse;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeIssuerResponse;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeOverview;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeState;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeTenantResponse;
import org.heidiverse.heidi.entity.model.credentialscheme.TypeMetadata;
import org.heidiverse.heidi.entity.model.exceptions.SchemaNotFoundException;
import org.heidiverse.heidi.entity.service.CredentialSchemeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/** Wallet-facing issuance metadata. These advertised protocol URLs remain stable. */
@RestController
@RequestMapping("/public/v2")
@CrossOrigin(originPatterns = "*")
public class PublicCredentialSchemaController {

    private final CredentialSchemeService credentialSchemeService;

    public PublicCredentialSchemaController(CredentialSchemeService credentialSchemeService) {
        this.credentialSchemeService = credentialSchemeService;
    }

    @Operation(summary = "Get credential schema overview")
    @GetMapping("/schema/overview")
    public ResponseEntity<CredentialSchemeOverview> getCredentialSchemesOverview(
            @RequestParam(value = "state", defaultValue = "") Set<CredentialSchemeState> states,
            @RequestParam(defaultValue = "true") boolean includeStyle,
            @RequestParam(defaultValue = "true") boolean includeImages,
            @RequestParam(required = false) String credentialIdentifier) {
        return ResponseEntity.ok(credentialSchemeService.getOverview(
                states.stream().toList(), includeStyle, includeImages, credentialIdentifier, false));
    }

    @Operation(summary = "Get OCA bundle for a credential schema")
    @GetMapping("/oca/{ocaBundleFileName}")
    public ResponseEntity<String> getOcaBundleForFileName(
            @PathVariable String ocaBundleFileName,
            @RequestParam(required = false) String format,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent)
            throws SchemaNotFoundException {
        return ResponseEntity.ok()
                .varyBy(HttpHeaders.USER_AGENT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(credentialSchemeService.getOcaBundle(ocaBundleFileName, format, userAgent));
    }

    @Operation(summary = "Get credential schema overview by slug")
    @GetMapping("/schema/overview/{slug}")
    public ResponseEntity<CredentialSchemeOverview> getCredentialSchemesOverviewForSlug(
            @PathVariable String slug,
            @RequestParam(value = "state", defaultValue = "") Set<CredentialSchemeState> states,
            @RequestParam(defaultValue = "true") boolean includeStyle,
            @RequestParam(defaultValue = "true") boolean includeImages,
            @RequestParam(required = false) String credentialIdentifier) {
        return ResponseEntity.ok(credentialSchemeService.getOverviewForIssuer(
                slug, states.stream().toList(), includeStyle, includeImages,
                credentialIdentifier, false));
    }

    @Operation(summary = "Get credential schema detail")
    @GetMapping("/schema/{id}")
    public ResponseEntity<CredentialSchemeDetailResponse> getCredentialScheme(
            @PathVariable UUID id) throws SchemaNotFoundException {
        return ResponseEntity.ok(credentialSchemeService.findById(id, false));
    }

    @Operation(summary = "Get credential schema detail by identifier and version")
    @GetMapping("/schema/{identifier}/{version}/detail")
    public ResponseEntity<CredentialSchemeDetailResponse> getCredentialSchemeByIdentifierAndVersion(
            @PathVariable String identifier, @PathVariable String version)
            throws SchemaNotFoundException {
        return ResponseEntity.ok(credentialSchemeService
                .findByCredentialIdentifierAndVersion(identifier, version, false));
    }

    @Operation(summary = "Get tenant ID for a credential identifier")
    @GetMapping("/schema/tenant/{credentialIdentifier}")
    public ResponseEntity<CredentialSchemeTenantResponse> getTenantIdForCredentialIdentifier(
            @PathVariable String credentialIdentifier) {
        return credentialSchemeService.findTenantIdByCredentialIdentifier(credentialIdentifier)
                .map(tenantId -> ResponseEntity.ok(
                        new CredentialSchemeTenantResponse(tenantId, credentialIdentifier)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get issuer for an credential schema")
    @GetMapping("/schema/{identifier}/{version}/issuer")
    public ResponseEntity<CredentialSchemeIssuerResponse> getIssuerForCredentialScheme(
            @PathVariable String identifier, @PathVariable String version) {
        return credentialSchemeService
                .findIssuerSlugByCredentialIdentifierAndVersion(identifier, version)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get Type Metadata document for an credential schema")
    @GetMapping("/schema/{identifier}/{version}")
    public ResponseEntity<TypeMetadata> getTypeMetadata(
            @PathVariable String identifier,
            @PathVariable String version,
            @RequestParam(required = false) String format,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent)
            throws SchemaNotFoundException {
        var metadata = credentialSchemeService.getTypeMetadata(identifier, version, format, userAgent);
        return ResponseEntity.ok().varyBy(HttpHeaders.USER_AGENT).body(metadata);
    }

    @Operation(summary = "Get credential context")
    @GetMapping("/schema/{identifier}/{version}/context")
    public ResponseEntity<CredentialContextResponse> getCredentialContext(
            @PathVariable String identifier, @PathVariable String version)
            throws SchemaNotFoundException {
        return ResponseEntity.ok(credentialSchemeService
                .getCredentialContextByCredentialIdentifierAndVersion(
                        identifier, version, false));
    }

    @Operation(summary = "Get SVG template for an credential schema")
    @GetMapping(value = "/schema/{identifier}/{version}/svg",
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getSvgTemplate(
            @PathVariable String identifier, @PathVariable String version) throws Exception {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(credentialSchemeService.getSvgTemplate(identifier, version));
    }
}
