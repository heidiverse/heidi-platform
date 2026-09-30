// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import jakarta.validation.Valid;

import org.heidiverse.heidi.entity.model.context.CredentialContextResponse;
import org.heidiverse.heidi.entity.model.credentialscheme.*;
import org.heidiverse.heidi.entity.model.exceptions.InvalidCredentialSchemeException;
import org.heidiverse.heidi.entity.model.exceptions.InvalidIssuerException;
import org.heidiverse.heidi.entity.model.exceptions.SchemaNotFoundException;
import org.heidiverse.heidi.entity.service.CredentialSchemeService;
import org.heidiverse.heidi.entity.service.utils.JwtUtils;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/management/v1/credential-schemas")
@CrossOrigin(originPatterns = "*")
public class CredentialSchemaController {

    private final CredentialSchemeService credentialSchemeService;

    public CredentialSchemaController(final CredentialSchemeService credentialSchemeService) {
        this.credentialSchemeService = credentialSchemeService;
    }

    @Operation(summary = "Get credential schema overview, optionally filtered by state")
    @GetMapping("/overview")
    public ResponseEntity<CredentialSchemeOverview> getCredentialSchemesOverview(
            @RequestParam(value = "state", defaultValue = "") Set<CredentialSchemeState> states,
            @RequestParam(defaultValue = "true") boolean includeStyle,
            @RequestParam(defaultValue = "true") boolean includeImages,
            @RequestParam(required = false) String credentialIdentifier) {
        return ResponseEntity.ok(
                credentialSchemeService.getOverview(
                        states.stream().toList(),
                        includeStyle,
                        includeImages,
                        credentialIdentifier,
                        true));
    }

    @Operation(summary = "Get credential schema overview, optionally filtered by state")
    @GetMapping("/overview/{slug}")
    public ResponseEntity<CredentialSchemeOverview> getCredentialSchemesOverviewForSlug(
            @PathVariable String slug,
            @RequestParam(value = "state", defaultValue = "") Set<CredentialSchemeState> states,
            @RequestParam(defaultValue = "true") boolean includeStyle,
            @RequestParam(defaultValue = "true") boolean includeImages,
            @RequestParam(required = false) String credentialIdentifier) {
        return ResponseEntity.ok(
                credentialSchemeService.getOverviewForIssuer(
                        slug,
                        states.stream().toList(),
                        includeStyle,
                        includeImages,
                        credentialIdentifier,
                        true));
    }

    @Operation(summary = "Get credential schema detail")
    @GetMapping("{id}")
    public ResponseEntity<CredentialSchemeDetailResponse> getCredentialScheme(
            @PathVariable("id") UUID id) throws SchemaNotFoundException {
        return ResponseEntity.ok(credentialSchemeService.findById(id, true));
    }

    @Operation(summary = "Get credential schema detail by identifier and version")
    @GetMapping("/{identifier}/{version}/detail")
    public ResponseEntity<CredentialSchemeDetailResponse> getCredentialSchemeByIdentifierAndVersion(
            @PathVariable String identifier, @PathVariable String version)
            throws SchemaNotFoundException {
        return ResponseEntity.ok(
                credentialSchemeService.findByCredentialIdentifierAndVersion(
                        identifier, version, true));
    }

    @Operation(summary = "Update credential schema detail")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('EDITOR', 'ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<String> updateCredentialScheme(
            @PathVariable("id") final UUID id,
            @Valid @RequestBody CredentialSchemeDetail credentialScheme)
            throws SchemaNotFoundException, InvalidIssuerException, InvalidCredentialSchemeException {
        credentialSchemeService.update(id, credentialScheme);
        return ResponseEntity.ok("Issuance schema successfully updated.");
    }

    @Operation(summary = "Archive credential schema detail")
    @PutMapping("/{id}/archive")
    @PreAuthorize("hasAnyAuthority('EDITOR', 'ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<String> archiveCredentialScheme(@PathVariable("id") final UUID id)
            throws SchemaNotFoundException {
        credentialSchemeService.archive(id);
        return ResponseEntity.ok("Issuance schema successfully archived.");
    }

    @Operation(summary = "Publish credential schema detail")
    @PutMapping("/{id}/publish")
    @PreAuthorize("hasAnyAuthority('EDITOR', 'ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<String> publishCredentialScheme(
            @PathVariable("id") final UUID id, @RequestParam("version") String version)
            throws SchemaNotFoundException {
        credentialSchemeService.publish(id, version);
        return ResponseEntity.ok("Issuance schema successfully published.");
    }

    @Operation(summary = "Create new credential schema detail")
    @PostMapping("")
    @PreAuthorize("hasAnyAuthority('EDITOR', 'ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<CredentialSchemeResponse> createCredentialScheme(
            @Valid @RequestBody CredentialSchemeDetail credentialScheme,
            @RequestParam(required = false) String tenantId)
            throws InvalidIssuerException, InvalidCredentialSchemeException {
        // Resolve tenant ID
        tenantId = JwtUtils.validateAndResolveTenantId(tenantId);

        // Insert credential schema.
        final var insertedCredentialScheme =
                credentialSchemeService.create(credentialScheme, tenantId);
        return ResponseEntity.ok(
                new CredentialSchemeResponse(
                        insertedCredentialScheme, "Issuance schema successfully created."));
    }

    @Operation(summary = "Get tenant ID for a given credential identifier")
    @GetMapping("/tenant/{credentialIdentifier}")
    public ResponseEntity<CredentialSchemeTenantResponse> getTenantIdForCredentialIdentifier(
            @PathVariable String credentialIdentifier) {
        return credentialSchemeService
                .findTenantIdByCredentialIdentifier(credentialIdentifier)
                .map(
                        tenantId ->
                                ResponseEntity.ok(
                                        new CredentialSchemeTenantResponse(
                                                tenantId, credentialIdentifier)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get Type Metadata document for an credential schema")
    @GetMapping("/{identifier}/{version}")
    public ResponseEntity<TypeMetadata> getTypeMetadata(
            @PathVariable String identifier, @PathVariable String version)
            throws SchemaNotFoundException {
        TypeMetadata typeMetadata = credentialSchemeService.getTypeMetadata(identifier, version);
        return ResponseEntity.ok(typeMetadata);
    }

    @Operation(summary = "Get credential context")
    @GetMapping("/{identifier}/{version}/context")
    public ResponseEntity<CredentialContextResponse> getCredentialContext(
            @PathVariable String identifier, @PathVariable String version)
            throws SchemaNotFoundException {
        CredentialContextResponse context = credentialSchemeService
                .getCredentialContextByCredentialIdentifierAndVersion(identifier, version, false);

        return ResponseEntity.ok(context);
    }

    @Operation(summary = "Get SVG template for an credential schema")
    @GetMapping(value = "/{identifier}/{version}/svg", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getSvgTemplate(
            @PathVariable String identifier, @PathVariable String version)
            throws Exception {
        String svgTemplate = credentialSchemeService.getSvgTemplate(identifier, version);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(svgTemplate);
    }
}
