// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.signing.adapters;

import org.heidiverse.heidi.shared.signing.*;

import com.nimbusds.jose.jwk.AsymmetricJWK;
import com.nimbusds.jose.jwk.JWK;
import java.io.StringWriter;
import java.util.ArrayList;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

/** Builds and signs only a PKCS#10 request; no caller-supplied message is signed. */
public final class SigningCsr {
    private SigningCsr() {}

    public static String create(SigningKeyProvider provider, SigningKeyRef key, SigningCsrRequest request) {
        if (!key.usages().contains(SigningKeyUsage.SIGN)) throw new IllegalArgumentException("CSR requires signing capability");
        var algorithm = switch (key.algorithm()) {
            case "ES256" -> "SHA256withECDSA";
            case "ES384" -> "SHA384withECDSA";
            case "ES512" -> "SHA512withECDSA";
            case "RS256" -> "SHA256withRSA";
            case "RS384" -> "SHA384withRSA";
            case "RS512" -> "SHA512withRSA";
            case "PS256" -> "SHA256withRSAandMGF1";
            case "PS384" -> "SHA384withRSAandMGF1";
            case "PS512" -> "SHA512withRSAandMGF1";
            case "EdDSA" -> "Ed25519";
            default -> throw new IllegalArgumentException("CSR is unsupported for " + key.algorithm());
        };
        try {
            var jwk = (AsymmetricJWK) JWK.parse(key.publicJwk());
            var builder = new JcaPKCS10CertificationRequestBuilder(new X500Name(request.subject()), jwk.toPublicKey());
            var names = new ArrayList<GeneralName>();
            request.dnsNames().forEach(name -> names.add(new GeneralName(GeneralName.dNSName, name)));
            request.uriNames().forEach(name -> names.add(new GeneralName(GeneralName.uniformResourceIdentifier, name)));
            if (!names.isEmpty()) {
                var extensions = new ExtensionsGenerator();
                extensions.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names.toArray(GeneralName[]::new)));
                builder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensions.generate());
            }
            var csr = builder.build(new ProviderContentSigner(provider, key, algorithm, key.algorithm()));
            var output = new StringWriter();
            try (var pem = new JcaPEMWriter(output)) { pem.writeObject(csr); }
            return output.toString();
        } catch (Exception exception) {
            throw new SigningKeyException("Could not create certificate request", exception);
        }
    }
}
