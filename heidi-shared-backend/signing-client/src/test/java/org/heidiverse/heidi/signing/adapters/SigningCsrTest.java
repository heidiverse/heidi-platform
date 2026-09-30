// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.signing.adapters;

import static org.junit.jupiter.api.Assertions.*;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.io.StringReader;
import java.security.Signature;
import java.util.List;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.heidiverse.heidi.shared.signing.*;
import org.junit.jupiter.api.Test;

class SigningCsrTest {
    @Test
    void csrProvesPossession() throws Exception {
        var key = new ECKeyGenerator(Curve.P_256).generate();
        var ref = new SigningKeyRef("software://key", key.toPublicJWK().toJSONString(), "ES256");
        var provider = new SigningKeyProvider() {
            public String scheme() { return "software"; }
            public List<String> supportedAlgorithms() { return List.of("ES256"); }
            public SigningKeyRef resolve(String uri) { return ref; }
            public ProviderHealth health() { return ProviderHealth.up(0); }
            public byte[] sign(SigningKeyRef signingKey, byte[] message) {
                try {
                    var signature = Signature.getInstance("SHA256withECDSAinP1363Format");
                    signature.initSign(key.toECPrivateKey());
                    signature.update(message);
                    return signature.sign();
                } catch (Exception exception) { throw new IllegalStateException(exception); }
            }
        };
        var pem = SigningCsr.create(provider, ref, new SigningCsrRequest("CN=Issuer",
                List.of("issuer.example"), List.of("https://issuer.example")));
        try (var parser = new PEMParser(new StringReader(pem))) {
            var csr = (PKCS10CertificationRequest) parser.readObject();
            assertEquals("CN=Issuer", csr.getSubject().toString());
            assertTrue(csr.isSignatureValid(new JcaContentVerifierProviderBuilder().build(key.toECPublicKey())));
            assertArrayEquals(key.toECPublicKey().getEncoded(), csr.getSubjectPublicKeyInfo().getEncoded());
            var extensions = Extensions.getInstance(csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest)[0].getAttrValues().getObjectAt(0));
            var names = GeneralNames.fromExtensions(extensions, Extension.subjectAlternativeName).getNames();
            assertEquals("issuer.example", names[0].getName().toString());
            assertEquals("https://issuer.example", names[1].getName().toString());
        }
    }
}
