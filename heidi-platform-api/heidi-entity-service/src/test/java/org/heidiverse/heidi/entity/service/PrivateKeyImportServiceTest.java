// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.List;
import org.heidiverse.heidi.entity.model.issuer.PrivateKeyFormat;
import org.heidiverse.heidi.shared.testing.TestCertificates;
import org.junit.jupiter.api.Test;

class PrivateKeyImportServiceTest {
    private final PrivateKeyImportService service = new PrivateKeyImportService();

    @Test
    void readsPrivateJwkFromSet() throws Exception {
        var keyPair = TestCertificates.ecKeyPair();
        var key = new ECKey.Builder(Curve.P_256, (ECPublicKey) keyPair.getPublic())
                .privateKey((ECPrivateKey) keyPair.getPrivate())
                .keyID("selected")
                .build();
        var publicKey = new ECKey.Builder(Curve.P_256,
                (ECPublicKey) TestCertificates.ecKeyPair().getPublic()).build();

        var imported = service.read(PrivateKeyFormat.JWKS,
                new JWKSet(List.of(publicKey, key)).toString(false), null, "ES256", "platform-key");

        assertThat(JWK.parse(imported.privateJwk()).isPrivate()).isTrue();
        assertThat(imported.certificateChain()).isEmpty();
    }

    @Test
    void readsPkcs8Pem() throws Exception {
        var keyPair = TestCertificates.ecKeyPair();
        var pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        var imported = service.read(
                PrivateKeyFormat.PEM, pem, null, "ES256", "platform-key");

        var jwk = JWK.parse(imported.privateJwk());
        assertThat(jwk.isPrivate()).isTrue();
        assertThat(jwk.getKeyID()).isEqualTo("platform-key");
        assertThat(jwk.getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
    }

    @Test
    void readsPkcs12AndItsCertificateChain() throws Exception {
        var pkcs12 = TestCertificates.pkcs12("Acme");

        var imported = service.read(PrivateKeyFormat.PKCS12, pkcs12.base64(),
                pkcs12.password(), "ES256", "platform-key");

        assertThat(JWK.parse(imported.privateJwk()).isPrivate()).isTrue();
        assertThat(imported.certificateChain()).isEqualTo(pkcs12.encodedChain());
    }
}
