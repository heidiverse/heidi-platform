// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.heidiverse.heidi.shared.testing.TestCertificates;
import org.heidiverse.heidi.shared.trustframework.TrustConfiguration;
import org.heidiverse.heidi.shared.trustframework.TrustFrameworkType;
import org.heidiverse.heidi.verifier.model.validation.ValidationMode;
import org.heidiverse.heidi.verifier.model.vp.VerificationRequestData;
import org.heidiverse.heidi.verifier.sdjwt.model.exception.InvalidSdJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.List;

class RequestTrustVerifierFactoryTest {

    private final RequestTrustVerifierFactory factory =
            new RequestTrustVerifierFactory(RestClient.builder(), new ObjectMapper());

    @Test
    void buildsEudiVerifierFromTheIdentityRoots() throws Exception {
        var ca = TestCertificates.ca("identity-root");
        var leaf =
                TestCertificates.leaf(
                        ca,
                        TestCertificates.ecKeyPair().getPublic(),
                        "issuer.example",
                        List.of("issuer.example"));
        var chain = List.of(leaf.certificate(), ca.certificate());
        var request =
                request(
                        TrustFrameworkType.DE,
                        List.of(Base64.getEncoder().encodeToString(ca.certificate().getEncoded())),
                        null,
                        null);

        assertTrue(factory.x509Verifier(request).validCertChain(chain));
    }

    @Test
    void swissVerificationFailsClosedWithoutItsConfiguredAnchor() {
        var verifier = factory.didTrustVerifier(request(TrustFrameworkType.CH, null, null, null));

        assertThrows(
                InvalidSdJwtException.class,
                () -> verifier.verifyTrustedDid("did:webvh:credential-issuer.example"));
    }

    private VerificationRequestData request(
            TrustFrameworkType trustFramework,
            List<String> eudiAnchors,
            String swissAnchor,
            String registryUrl) {
        return new VerificationRequestData(
                "nonce",
                "client",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                ValidationMode.DISABLED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new TrustConfiguration(trustFramework, eudiAnchors, swissAnchor, registryUrl),
                null,
                false, null, "CUSTOM_PRESENTATION_2026_1");
    }
}
