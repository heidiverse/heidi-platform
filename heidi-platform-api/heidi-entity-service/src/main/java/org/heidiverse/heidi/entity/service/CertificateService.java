// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import org.springframework.stereotype.Service;

import java.security.*;
import java.security.cert.CertificateException;
import java.util.UUID;

/** Obtains the managed access certificate from the RP registrar. */
@Service
public class CertificateService {

    private final RPRegistrarService rpRegistrarService;

    public CertificateService(RPRegistrarService rpRegistrarService) {
        this.rpRegistrarService = rpRegistrarService;
    }

    /** Obtains the registrar certificate for a key already held by a signing provider. */
    public byte[] generateAccessCertificate(
            UUID rpId, PublicKey publicKey) throws CertificateException {
        return rpRegistrarService.addNewAccessCertificate(rpId, publicKey);
    }

}
