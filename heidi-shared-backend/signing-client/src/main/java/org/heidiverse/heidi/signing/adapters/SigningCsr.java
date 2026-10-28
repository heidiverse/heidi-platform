// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.signing.adapters;

import org.heidiverse.heidi.shared.signing.*;
import com.nimbusds.jose.jwk.AsymmetricJWK;
import com.nimbusds.jose.jwk.JWK;
import uniffi.heidi_pki_x509.SignatureEncoding;

/** Builds and signs only a PKCS#10 request. */
public final class SigningCsr {
    private SigningCsr() {}

    public static String create(SigningKeyProvider provider, SigningKeyRef key, SigningCsrRequest request) {
        if (!key.usages().contains(SigningKeyUsage.SIGN)) throw new IllegalArgumentException("CSR requires signing capability");
        try {
            var spki = ((AsymmetricJWK) JWK.parse(key.publicJwk())).toPublicKey().getEncoded();
            var subject = ManagedCertificates.subject(request.subject());
            var algorithm = ManagedCertificates.algorithm(key.algorithm());
            // Sign only SDK-generated request bytes; provider keys remain private.
            return ManagedCertificates.use(x509 -> {
                var info = x509.prepareCsr(subject, spki, request.dnsNames(), request.uriNames());
                var csr = x509.completeCsr(info, algorithm, provider.sign(key, info), SignatureEncoding.JOSE_RAW);
                return x509.encodePemCsr(csr);
            });
        } catch (Exception error) {
            throw new SigningKeyException("Could not create certificate request", error);
        }
    }
}
