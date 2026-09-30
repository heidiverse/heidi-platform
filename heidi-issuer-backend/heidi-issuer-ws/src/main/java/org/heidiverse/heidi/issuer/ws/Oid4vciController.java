// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.ws;

import org.heidiverse.heidi.issuer.model.FlowVariant;
import org.heidiverse.heidi.issuer.model.TrustSystem;
import org.heidiverse.heidi.issuer.model.api.AuthorizationServerMetadata;
import org.heidiverse.heidi.issuer.model.api.CredentialOfferResponse;
import org.heidiverse.heidi.issuer.model.api.JwtVcIssuerMetadata;
import org.heidiverse.heidi.issuer.model.api.NonceResponse;
import org.heidiverse.heidi.issuer.model.api.PushedAuthorizationResponse;
import org.heidiverse.heidi.issuer.model.api.TokenResponse;
import org.heidiverse.heidi.issuer.service.DpopException;
import org.heidiverse.heidi.issuer.service.IssuerAuthorizationExtension;
import org.heidiverse.heidi.issuer.service.FederationClient;
import org.heidiverse.heidi.issuer.service.IssuanceService;
import org.heidiverse.heidi.issuer.service.IssuerProperties;
import org.heidiverse.heidi.issuer.service.CredentialEndpointResult;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.util.MultiValueMap;

@RestController
@RequestMapping("/{issuerSlug}/{variantPath}/{credentialIdentifier}/{credentialVersion}")
public class Oid4vciController {
    private final IssuanceService issuanceService;
    private final List<IssuerAuthorizationExtension> authorizationExtensions;
    private final FederationClient federationClient;
    private final IssuerProperties properties;

    public Oid4vciController(
            IssuanceService issuanceService,
            List<IssuerAuthorizationExtension> authorizationExtensions,
            FederationClient federationClient,
            IssuerProperties properties) {
        this.issuanceService = issuanceService;
        this.authorizationExtensions = authorizationExtensions;
        this.federationClient = federationClient;
        this.properties = properties;
    }

    /**
     * The credential issuer's OpenID Federation entity configuration. Its entity identifier is the
     * credential issuer identifier, which is also the {@code iss} a trust chain is resolved from.
     */
    @GetMapping(value = "/.well-known/openid-federation", produces = "application/entity-statement+jwt")
    public ResponseEntity<String> federationEntityConfiguration(
            @PathVariable String issuerSlug,
            @PathVariable String variantPath,
            @PathVariable String credentialIdentifier,
            @PathVariable String credentialVersion) {
        var variant = FlowVariant.fromPath(variantPath);
        var entityId = String.join("/",
                properties.getPublicUrl().replaceAll("/+$", ""),
                issuerSlug, variant.path(), credentialIdentifier, credentialVersion);
        var metadata = issuanceService.credentialMetadata(
                issuerSlug, variant, credentialIdentifier, credentialVersion,
                issuanceService.issuanceProfileFor(
                        issuerSlug, credentialIdentifier, credentialVersion));
        var configuration = federationClient.entityConfiguration(issuerSlug, entityId, metadata);
        return configuration == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/entity-statement+jwt"))
                        .body(configuration);
    }

    @GetMapping("/.well-known/openid-credential-issuer")
    public ResponseEntity<?> credentialMetadata(
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

    @GetMapping("/credential-offer/{connectionId}")
    public ResponseEntity<CredentialOfferResponse> credentialOffer(
            @PathVariable String issuerSlug,
            @PathVariable String variantPath,
            @PathVariable String credentialIdentifier,
            @PathVariable String credentialVersion,
            @PathVariable String connectionId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(issuanceService.credentialOffer(
                        issuerSlug,
                        FlowVariant.fromPath(variantPath),
                        credentialIdentifier,
                        credentialVersion,
                        connectionId));
    }

    @GetMapping(value = "/.well-known/openid-credential-issuer/{trustFramework}", produces = "application/jwt")
    public ResponseEntity<String> signedCredentialMetadata(
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

    @GetMapping({"/.well-known/oauth-authorization-server", "/.well-known/openid-configuration"})
    public ResponseEntity<?> authorizationMetadata(
            @PathVariable String issuerSlug,
            @PathVariable String variantPath,
            @PathVariable String credentialIdentifier,
            @PathVariable String credentialVersion,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        FlowVariant variant = FlowVariant.fromPath(variantPath);
        AuthorizationServerMetadata base = issuanceService.authorizationMetadata(
                issuerSlug,
                variant,
                credentialIdentifier,
                credentialVersion);
        AuthorizationServerMetadata metadata = authorizationExtension(
                        issuerSlug,
                        variantPath,
                        credentialIdentifier,
                        credentialVersion)
                .map(extension -> extension.authorizationMetadata(
                        issuerSlug,
                        variant,
                        credentialIdentifier,
                        credentialVersion,
                        base))
                .orElse(base);
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

    @PostMapping(path = "/par", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<PushedAuthorizationResponse> pushedAuthorizationRequest(
            @PathVariable String issuerSlug,
            @PathVariable String variantPath,
            @PathVariable String credentialIdentifier,
            @PathVariable String credentialVersion,
            @RequestParam MultiValueMap<String, String> params) {
        IssuerAuthorizationExtension extension = authorizationExtension(
                        issuerSlug, variantPath, credentialIdentifier, credentialVersion)
                .orElseThrow(() -> new UnsupportedOperationException("PAR is not enabled"));
        return ResponseEntity.ok(extension.pushAuthorization(params));
    }

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(
            @PathVariable String issuerSlug,
            @PathVariable String variantPath,
            @PathVariable String credentialIdentifier,
            @PathVariable String credentialVersion,
            @RequestParam Map<String, String> params) {
        IssuerAuthorizationExtension extension = authorizationExtension(
                        issuerSlug, variantPath, credentialIdentifier, credentialVersion)
                .orElseThrow(() -> new UnsupportedOperationException("Authorization is not enabled"));
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, extension.authorize(params))
                .build();
    }

    @GetMapping("/.well-known/jwt-vc-issuer")
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

    @PostMapping(path = "/token", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<TokenResponse> token(
            HttpServletRequest request,
            @PathVariable String issuerSlug,
            @PathVariable String variantPath,
            @PathVariable String credentialIdentifier,
            @PathVariable String credentialVersion,
            @RequestParam Map<String, String> params) {
        String dpopProof = dpopProof(request, false);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (dpopProof != null) {
            String nonce = issuanceService.newDpopNonce();
            if (nonce != null) response.header("DPoP-Nonce", nonce);
        }
        TokenResponse tokenResponse;
        if ("authorization_code".equals(params.get("grant_type"))) {
            tokenResponse = authorizationExtension(
                            issuerSlug,
                            variantPath,
                            credentialIdentifier,
                            credentialVersion)
                    .map(extension -> extension.token(params, dpopProof, request.getMethod()))
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported grant_type"));
        } else {
            tokenResponse = issuanceService.token(params, dpopProof, request.getMethod());
        }
        return response.body(tokenResponse);
    }

    private Optional<IssuerAuthorizationExtension> authorizationExtension(
            String issuerSlug,
            String variantPath,
            String credentialIdentifier,
            String credentialVersion) {
        FlowVariant variant = FlowVariant.fromPath(variantPath);
        return authorizationExtensions.stream()
                .filter(extension -> extension.supports(
                        issuerSlug, variant, credentialIdentifier, credentialVersion))
                .findFirst();
    }

    /**
     * Reads the single DPoP proof of a request. RFC 9449 section 4.3 requires rejecting a request
     * that carries more than one DPoP header field rather than silently using the first.
     */
    private static String dpopProof(HttpServletRequest request, boolean resourceRequest) {
        Enumeration<String> values = request.getHeaders("DPoP");
        if (values == null || !values.hasMoreElements()) {
            return null;
        }
        String proof = values.nextElement();
        if (values.hasMoreElements()) {
            throw new DpopException(
                    "invalid_dpop_proof",
                    "A request must not contain more than one DPoP header field",
                    null,
                    resourceRequest);
        }
        return proof;
    }

    @PostMapping(path = "/credential", consumes = {"application/json", "application/jwt"})
    public ResponseEntity<?> credential(
            HttpServletRequest request, @RequestBody String body) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.contains(" ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        int separator = authorization.indexOf(' ');
        String scheme = authorization.substring(0, separator);
        if (!scheme.equalsIgnoreCase("Bearer") && !scheme.equalsIgnoreCase("DPoP")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String dpopProof = dpopProof(request, true);
        CredentialEndpointResult result = issuanceService.issue(
                authorization.substring(separator + 1),
                body,
                scheme,
                dpopProof,
                request.getMethod(),
                isJwt(request),
                request.getHeader(HttpHeaders.USER_AGENT));
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.getStatus())
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(result.getContentType()));
        if (dpopProof != null) {
            String nonce = issuanceService.newDpopNonce();
            if (nonce != null) response.header("DPoP-Nonce", nonce);
        }
        return response.body(result.responseBody());
    }

    @PostMapping(path = "/deferred_credential", consumes = {"application/json", "application/jwt"})
    public ResponseEntity<?> deferredCredential(
            HttpServletRequest request, @RequestBody String body) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.contains(" ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        int separator = authorization.indexOf(' ');
        String scheme = authorization.substring(0, separator);
        if (!scheme.equalsIgnoreCase("Bearer") && !scheme.equalsIgnoreCase("DPoP")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String dpopProof = dpopProof(request, true);
        CredentialEndpointResult result = issuanceService.deferredIssue(
                authorization.substring(separator + 1),
                body,
                scheme,
                dpopProof,
                request.getMethod(),
                isJwt(request),
                request.getHeader(HttpHeaders.USER_AGENT));
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.getStatus())
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(result.getContentType()));
        if (dpopProof != null) {
            String nonce = issuanceService.newDpopNonce();
            if (nonce != null) response.header("DPoP-Nonce", nonce);
        }
        return response.body(result.responseBody());
    }

    private static boolean isJwt(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null
                && MediaType.parseMediaType(contentType)
                        .isCompatibleWith(MediaType.parseMediaType("application/jwt"));
    }

    @PostMapping("/nonce")
    public ResponseEntity<NonceResponse> nonce() {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        String dpopNonce = issuanceService.newDpopNonce();
        if (dpopNonce != null) response.header("DPoP-Nonce", dpopNonce);
        return response.body(new NonceResponse(java.util.UUID.randomUUID().toString(), 300));
    }
}
