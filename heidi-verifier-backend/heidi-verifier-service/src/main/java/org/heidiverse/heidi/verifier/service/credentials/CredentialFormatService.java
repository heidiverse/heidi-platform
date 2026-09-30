// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.kapunsdk.presentation.request.model.OID4VPVersion;

import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

public interface CredentialFormatService {
    String getFormatIdentifier();

    /** Object to be stored in the client_metadata.vp_formats object in an authorization request */
    Map<String, Object> getVpFormatObject(OID4VPVersion OID4VPVersion);

    /** Object to be stored in the format object in a presentation definition */
    Map<String, Object> getFormatObject();

    /**
     * Parses and verifies a Verifiable Presentation token
     *
     * <p>Note that the implementation does not verify that a signing belongs to JWT's clientId.
     * This is left to the caller for now.
     *
     * @param vpToken Verifiable Presentation token as sent to the verifier
     * @param nonce
     * @param mdocGeneratedNonce
     * @param clientId
     * @return a generic JSON object representing the disclosed credentials
     */
    Map<String, Object> parseAndVerify(
            final String vpToken,
            Boolean requireCryptographicKeyBinding,
            String nonce,
            String mdocGeneratedNonce,
            String clientId,
            List<String> transactionData,
            String responseUri)
            throws VpVerificationException, NoSuchAlgorithmException;
}
