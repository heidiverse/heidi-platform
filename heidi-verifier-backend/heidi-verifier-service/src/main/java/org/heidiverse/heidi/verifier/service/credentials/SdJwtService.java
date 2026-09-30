// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.heidiverse.heidi.verifier.model.vp.VerificationRequestData;
import org.heidiverse.heidi.verifier.sdjwt.SdJwtVerifier;
import org.heidiverse.heidi.verifier.sdjwt.model.X509CertVerifier;
import org.heidiverse.heidi.verifier.sdjwt.model.exception.InvalidSdJwtException;
import org.kapunsdk.presentation.request.model.OID4VPVersion;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SdJwtService implements CredentialFormatService {

    public static final String FORMAT_IDENTIFIER_DRAFT_21 = "vc+sd-jwt";

    public static final String FORMAT_IDENTIFIER = "dc+sd-jwt";

    private final SdJwtVerifier sdJwtVerifier;
    private final RequestTrustVerifierFactory trustVerifierFactory;
    private final StatusListVerifier statusListVerifier;

    public SdJwtService(
            SdJwtVerifier sdJwtVerifier,
            RequestTrustVerifierFactory trustVerifierFactory,
            StatusListVerifier statusListVerifier) {
        this.sdJwtVerifier = sdJwtVerifier;
        this.trustVerifierFactory = trustVerifierFactory;
        this.statusListVerifier = statusListVerifier;
    }

    @Override
    public String getFormatIdentifier() {
        // This is mainly used for PEX input_descriptor. Since they will be deprecated in the
        // future, we just return Draft version 21 format identifier.
        return FORMAT_IDENTIFIER_DRAFT_21;
    }

    @Override
    public Map<String, Object> getVpFormatObject(OID4VPVersion OID4VPVersion) {
        if (OID4VPVersion.getVersion() > OID4VPVersion.DRAFT_21.getVersion()) {
            return Map.of(
                    FORMAT_IDENTIFIER,
                    Map.of(
                            "sd-jwt_alg_values",
                            sdJwtVerifier.getSdJwtAlgValues(),
                            "kb-jwt_alg_values",
                            sdJwtVerifier.getKbJwtAlgValues()));
        } else {
            return Map.of(
                    FORMAT_IDENTIFIER_DRAFT_21,
                    Map.of(
                            "sd-jwt_alg_values",
                            sdJwtVerifier.getSdJwtAlgValues(),
                            "kb-jwt_alg_values",
                            sdJwtVerifier.getKbJwtAlgValues()));
        }
    }

    @Override
    public Map<String, Object> getFormatObject() {
        return Map.of(FORMAT_IDENTIFIER_DRAFT_21, Map.of());
    }

    @Override
    public Map<String, Object> parseAndVerify(
            String vpToken,
            Boolean requireCryptographicKeyBinding,
            String nonce,
            String mdocGeneratedNonce,
            String clientId,
            List<String> transactionData,
            String responseUri)
            throws VpVerificationException {
        try {
            var claims = sdJwtVerifier.parseAndVerify(
                    vpToken, requireCryptographicKeyBinding, nonce, clientId, transactionData);
            statusListVerifier.verify(vpToken, claims);
            return claims;
        } catch (InvalidSdJwtException e) {
            throw new VpVerificationException("Failed to verify vp token: " + e.getMessage());
        }
    }

    public Map<String, Object> parseAndVerify(
            String vpToken,
            Boolean requireCryptographicKeyBinding,
            String nonce,
            String clientId,
            List<String> transactionData,
            VerificationRequestData request,
            X509CertVerifier fallbackX509CertVerifier)
            throws VpVerificationException {
        try {
            var claims = sdJwtVerifier.parseAndVerify(
                    vpToken, requireCryptographicKeyBinding, nonce, clientId, transactionData,
                    trustVerifierFactory.x509Verifier(request),
                    trustVerifierFactory.didTrustVerifier(request),
                    fallbackX509CertVerifier);
            statusListVerifier.verify(vpToken, claims);
            return claims;
        } catch (InvalidSdJwtException | IllegalArgumentException e) {
            throw new VpVerificationException("Failed to verify vp token: " + e.getMessage());
        }
    }
}
