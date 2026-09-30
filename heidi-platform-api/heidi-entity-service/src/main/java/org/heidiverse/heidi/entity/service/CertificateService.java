// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import java.security.*;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;
import java.util.UUID;

/**
 * Obtains the verifier's access certificate from the RP registrar and packages it as a keystore.
 *
 * <p>Nothing here mints certificates for issuers. An issuer's signing certificate chains to a CA
 * on a national Trusted List, so it is supplied by the operator; whether issuers additionally need
 * an access certificate of their own is open, and if they do it would come from an Access
 * Certificate Authority through a registrar, exactly as this method obtains the verifier's.
 */
@Service
public class CertificateService {

    private final RPRegistrarService rpRegistrarService;

    public CertificateService(RPRegistrarService rpRegistrarService) {
        this.rpRegistrarService = rpRegistrarService;
        Security.addProvider(new BouncyCastleProvider());
    }

    /** Obtains the registrar certificate for a key already held by a signing provider. */
    public java.security.cert.X509Certificate generateAccessCertificate(
            UUID rpId, PublicKey publicKey) throws CertificateException {
        return rpRegistrarService.addNewAccessCertificate(rpId, publicKey);
    }

}
