// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.ws.controller;

import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import java.util.Objects;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.AsymmetricJWK;
import org.heidiverse.heidi.entity.model.issuer.*;
import org.heidiverse.heidi.entity.service.LocalDevelopmentTrustSeed;
import org.heidiverse.heidi.entity.service.SigningKeyService;
import org.heidiverse.heidi.entity.service.utils.JwtUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Development certificates are unavailable outside the local profile. */
@RestController
@Profile("local")
@RequestMapping("/management/v1/keys")
@CrossOrigin(originPatterns = "*")
@PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
public class DevelopmentKeyController {
    private final SigningKeyService keys;
    private final LocalDevelopmentTrustSeed seed;

    public DevelopmentKeyController(SigningKeyService keys, LocalDevelopmentTrustSeed seed) {
        this.keys = keys;
        this.seed = seed;
    }

    @PostMapping("/{keyId}/versions/{versionId}/development-certificate")
    public Map<String, UUID> create(
            @PathVariable String keyId, @PathVariable UUID versionId,
            @RequestParam(required = false) String tenantId,
            @jakarta.validation.Valid @RequestBody DevelopmentCertificate request) throws Exception {
        return createInScope(JwtUtils.validateAndResolveTenantId(tenantId), keyId, versionId, request);
    }

    @PostMapping("/global/{keyId}/versions/{versionId}/development-certificate")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Map<String, UUID> createGlobal(
            @PathVariable String keyId, @PathVariable UUID versionId,
            @jakarta.validation.Valid @RequestBody DevelopmentCertificate request) throws Exception {
        return createInScope(null, keyId, versionId, request);
    }

    private Map<String, UUID> createInScope(
            String tenant, String keyId, UUID versionId, DevelopmentCertificate request) throws Exception {
        var key = keys.keys(tenant).stream().filter(candidate -> candidate.getLogicalKeyId().equals(keyId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Key not found"));
        var version = keys.version(tenant, key.getId(), versionId);
        var existing = keys.certificates(tenant, key.getId(), versionId).stream()
                .filter(certificate -> certificate.getSource() == SigningCertificateSource.DEVELOPMENT)
                .filter(certificate -> certificate.getProfile() == request.profile())
                .filter(certificate -> Objects.equals(certificate.getTrustSystem(), request.trustSystem()))
                .filter(DevelopmentKeyController::isValid)
                .findFirst();
        if (existing.isPresent()) return Map.of("certificateId", existing.get().getId());

        var jwk = JWK.parse(version.getPublicJwk());
        if (!(jwk instanceof AsymmetricJWK asymmetric)) {
            throw new IllegalArgumentException("This key cannot carry an X.509 certificate");
        }
        var chain = seed.certificateChain(keyId, seed.issuerIdentifier(), asymmetric.toPublicKey().getEncoded());
        var id = keys.setCertificateChain(tenant, key.getId(), versionId, chain,
                request.profile(), SigningCertificateSource.DEVELOPMENT, request.trustSystem());
        return Map.of("certificateId", id);
    }

    private static boolean isValid(
            org.heidiverse.heidi.entity.model.entity.SigningCertificateEntity certificate) {
        var now = Instant.now();
        return !certificate.getCertificateChain().isEmpty()
                && certificate.getNotBefore() != null
                && !now.isBefore(certificate.getNotBefore())
                && certificate.getNotAfter() != null
                && now.isBefore(certificate.getNotAfter());
    }

    public record DevelopmentCertificate(@jakarta.validation.constraints.NotNull SigningCertificateProfile profile, IssuerTrustSystem trustSystem) {}
}
