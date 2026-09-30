// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.ws;

import org.heidiverse.heidi.issuer.model.FlowVariant;
import org.heidiverse.heidi.issuer.model.TrustSystem;
import org.heidiverse.heidi.issuer.model.api.AuthorizationServerMetadata;
import org.heidiverse.heidi.issuer.model.api.JwtVcIssuerMetadata;
import org.heidiverse.heidi.issuer.service.IssuanceService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Standard well-known URL layout for issuers whose identifier contains a path.
 * The suffix-style mappings remain available for compatibility with existing
 * Heidi wallet deployments.
 */
@RestController
public class DiscoveryController {
    private final IssuanceService issuanceService;

    public DiscoveryController(IssuanceService issuanceService) {
        this.issuanceService = issuanceService;
    }

    @GetMapping("/.well-known/openid-credential-issuer/{issuerSlug}/{variantPath}/{credentialIdentifier}/{credentialVersion}")
    public ResponseEntity<?> credentialIssuer(
            @PathVariable String issuerSlug,
            @PathVariable String variantPath,
            @PathVariable String credentialIdentifier,
            @PathVariable String credentialVersion,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        if (acceptsSignedMetadata(accept)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/jwt"))
                    .body(issuanceService.signedCredentialMetadata(
                            issuerSlug, FlowVariant.fromPath(variantPath), credentialIdentifier,
                            credentialVersion, TrustSystem.Default,
                            issuanceService.issuanceProfileFor(
                                    issuerSlug, credentialIdentifier, credentialVersion)));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(issuanceService.credentialMetadata(
                        issuerSlug, FlowVariant.fromPath(variantPath), credentialIdentifier,
                        credentialVersion,
                        issuanceService.issuanceProfileFor(
                                issuerSlug, credentialIdentifier, credentialVersion)));
    }

    @GetMapping(value = "/.well-known/openid-credential-issuer/{trustFramework}/{issuerSlug}/{variantPath}/{credentialIdentifier}/{credentialVersion}", produces = "application/jwt")
    public ResponseEntity<String> signedCredentialIssuer(
            @PathVariable TrustSystem trustFramework,
            @PathVariable String issuerSlug,
            @PathVariable String variantPath,
            @PathVariable String credentialIdentifier,
            @PathVariable String credentialVersion,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        if (trustFramework == TrustSystem.Default) {
            return ResponseEntity.notFound().build();
        }
        if (!acceptsSignedMetadata(accept)) {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/jwt"))
                .body(issuanceService.signedCredentialMetadata(
                        issuerSlug, FlowVariant.fromPath(variantPath), credentialIdentifier,
                        credentialVersion, trustFramework,
                        issuanceService.issuanceProfileFor(
                                issuerSlug, credentialIdentifier, credentialVersion)));
    }

    private static boolean acceptsSignedMetadata(String accept) {
        if (accept == null) return false;
        return MediaType.parseMediaTypes(accept).stream()
                .anyMatch(type -> "application".equalsIgnoreCase(type.getType())
                        && "jwt".equalsIgnoreCase(type.getSubtype())
                        && type.getQualityValue() > 0);
    }

    @GetMapping({
        "/.well-known/oauth-authorization-server/{issuerSlug}/{variantPath}/{credentialIdentifier}/{credentialVersion}",
        "/.well-known/openid-configuration/{issuerSlug}/{variantPath}/{credentialIdentifier}/{credentialVersion}"
    })
    public ResponseEntity<?> authorizationServer(
            @PathVariable String issuerSlug,
            @PathVariable String variantPath,
            @PathVariable String credentialIdentifier,
            @PathVariable String credentialVersion,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        FlowVariant variant = FlowVariant.fromPath(variantPath);
        AuthorizationServerMetadata metadata = issuanceService.authorizationMetadata(
                issuerSlug,
                variant,
                credentialIdentifier,
                credentialVersion);
        if (acceptsSignedMetadata(accept)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/jwt"))
                    .body(issuanceService.signedAuthorizationMetadata(
                            issuerSlug, credentialIdentifier, credentialVersion, metadata,
                            issuanceService.issuanceProfileFor(
                                    issuerSlug, credentialIdentifier, credentialVersion)));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(metadata);
    }

    @GetMapping("/.well-known/jwt-vc-issuer/{issuerSlug}/{variantPath}/{credentialIdentifier}/{credentialVersion}")
    public JwtVcIssuerMetadata jwtVcIssuer(
            @PathVariable String issuerSlug,
            @PathVariable String variantPath,
            @PathVariable String credentialIdentifier,
            @PathVariable String credentialVersion) {
        return issuanceService.jwtVcIssuerMetadata(
                issuerSlug,
                FlowVariant.fromPath(variantPath),
                credentialIdentifier,
                credentialVersion);
    }
}
