// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nimbusds.jose.jwk.JWK;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

class IdentitySigningServiceTest {
    @Test
    void hashesTheDerEncodedLeafCertificateForX509Hash() throws Exception {
        try (var stream = getClass().getResourceAsStream("/issuer.json")) {
            var jwk = JWK.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));

            assertEquals(
                    "zQ2dPxHdIvyuDZ3i3QgHYJucMqpCsWd8X1dSNdPYJJA",
                    IdentitySigningService.x509CertificateHash(jwk));
        }
    }

    @Test
    void addsExportedCertificateChainToResolvedPublicJwk() throws Exception {
        try (var stream = getClass().getResourceAsStream("/issuer.json")) {
            var certifiedJwk = JWK.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            var publicJwk = new HashMap<String, Object>(certifiedJwk.toJSONObject());
            publicJwk.remove("x5c");

            var resolvedJwk = IdentitySigningService.withCertificateChain(
                    JWK.parse(publicJwk),
                    certifiedJwk.getX509CertChain().stream().map(Object::toString).toList());

            assertEquals(certifiedJwk.getX509CertChain(), resolvedJwk.getX509CertChain());
            assertEquals(
                    "zQ2dPxHdIvyuDZ3i3QgHYJucMqpCsWd8X1dSNdPYJJA",
                    IdentitySigningService.x509CertificateHash(resolvedJwk));
        }
    }

    @Test
    void omitsRootFromMultiCertificateJoseChain() throws Exception {
        try (var stream = getClass().getResourceAsStream("/issuer.json")) {
            var certifiedJwk = JWK.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            var certificate = certifiedJwk.getX509CertChain().getFirst().toString();
            var publicJwk = new HashMap<String, Object>(certifiedJwk.toJSONObject());
            publicJwk.remove("x5c");

            var resolvedJwk = IdentitySigningService.withCertificateChain(
                    JWK.parse(publicJwk), java.util.List.of(certificate, certificate));

            assertEquals(1, resolvedJwk.getX509CertChain().size());
        }
    }
}
