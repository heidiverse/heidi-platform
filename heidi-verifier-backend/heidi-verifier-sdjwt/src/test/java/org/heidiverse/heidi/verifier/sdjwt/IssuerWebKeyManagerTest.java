// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

class IssuerWebKeyManagerTest {

    @Test
    void jwtVcIssuerMetadataUriKeepsLocalHttpScheme() throws URISyntaxException {
        assertEquals(
                "http://192.0.2.10:8082/.well-known/jwt-vc-issuer",
                IssuerWebKeyManager.jwtVcIssuerMetadataUri("http://192.0.2.10:8082")
                        .toString());
    }

    @Test
    void jwtVcIssuerMetadataUriInsertsWellKnownBeforeIssuerPath()
            throws URISyntaxException {
        assertEquals(
                "http://192.0.2.10:8082/.well-known/jwt-vc-issuer/local/c/test-lphee/1.0.0",
                IssuerWebKeyManager.jwtVcIssuerMetadataUri(
                        "http://192.0.2.10:8082/local/c/test-lphee/1.0.0")
                        .toString());
    }

    @Test
    void signatureKeyUseAllowsMissingUse() throws Exception {
        final var jwkWithoutUse = new ECKeyGenerator(Curve.P_256).generate();
        final var signingJwk =
                new ECKeyGenerator(Curve.P_256).keyUse(KeyUse.SIGNATURE).generate();
        final var encryptionJwk =
                new ECKeyGenerator(Curve.P_256).keyUse(KeyUse.ENCRYPTION).generate();

        assertTrue(IssuerWebKeyManager.signatureKeyUseMatches(jwkWithoutUse));
        assertTrue(IssuerWebKeyManager.signatureKeyUseMatches(signingJwk));
        assertFalse(IssuerWebKeyManager.signatureKeyUseMatches(encryptionJwk));
    }
}
