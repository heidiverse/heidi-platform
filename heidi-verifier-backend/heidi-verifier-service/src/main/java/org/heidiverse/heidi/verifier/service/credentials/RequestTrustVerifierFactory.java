// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import org.heidiverse.heidi.shared.trustframework.TrustFrameworkType;
import org.heidiverse.heidi.verifier.model.vp.VerificationRequestData;
import org.heidiverse.heidi.verifier.sdjwt.model.DidTrustVerifier;
import org.heidiverse.heidi.verifier.sdjwt.model.X509CertVerifier;
import org.heidiverse.heidi.verifier.sdjwt.model.exception.InvalidSdJwtException;
import org.heidiverse.heidi.verifier.sdjwt.util.DidUtil;
import org.heidiverse.heidi.verifier.sdjwt.util.JwsUtil;
import org.heidiverse.heidi.verifier.sdjwt.util.KapunJwtUtil;
import org.heidiverse.heidi.verifier.sdjwt.util.SdJwtUtil;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/** Builds request-scoped trust verification from the selected verifier identity. */
@Service
public class RequestTrustVerifierFactory {
    private static final String IDENTITY_STATEMENT_TYPE =
            "swiyu-identity-trust-statement+jwt";

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public RequestTrustVerifierFactory(
            RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    public X509CertVerifier x509Verifier(VerificationRequestData request) {
        var trust = request.trustConfiguration();
        if (trust == null || trust.trustFramework() != TrustFrameworkType.DE) return null;
        var anchors = trust.eudiTrustAnchors() == null
                ? List.<String>of() : trust.eudiTrustAnchors();
        return new TrustedRootX509CertVerifier(
                anchors.stream().map(this::parseCertificate).toList());
    }

    public DidTrustVerifier didTrustVerifier(VerificationRequestData request) {
        var trust = request.trustConfiguration();
        if (trust == null || trust.trustFramework() != TrustFrameworkType.CH) return null;
        return did -> verifySwissIdentityStatement(
                did, trust.swissTrustAnchor(), trust.swissTrustRegistryBaseUrl());
    }

    private void verifySwissIdentityStatement(
            String subjectDid, String trustAnchorDid, String registryBaseUrl)
            throws InvalidSdJwtException {
        if (!hasText(trustAnchorDid) || !hasText(registryBaseUrl)) {
            throw new InvalidSdJwtException(
                    "Swiss trust framework requires a trust-anchor DID and trust-registry URL");
        }
        try {
            var response = restClientBuilder.clone().baseUrl(registryBaseUrl).build()
                    .get()
                    .uri("/api/v2/identity-trust-statement/{identifier}", subjectDid)
                    .retrieve()
                    .body(String.class);
            var compact = serializedStatement(response);
            var statement = KapunJwtUtil.parse(compact);
            var header = KapunJwtUtil.header(statement);
            var claims = KapunJwtUtil.payload(statement);
            var keyId = header.get("kid") instanceof String value ? value : null;
            if (!hasText(keyId) || !trustAnchorDid.equals(keyId.split("#", 2)[0])) {
                throw new InvalidSdJwtException(
                        "Swiss identity statement was not signed by the configured trust anchor");
            }
            if (!subjectDid.equals(claims.get("sub"))) {
                throw new InvalidSdJwtException(
                        "Swiss identity statement subject does not match the credential DID document");
            }
            var now = Instant.now();
            if (!afterNow(claims.get("exp"), now)
                    || claims.get("nbf") != null
                            && (!(claims.get("nbf") instanceof Number)
                                    || afterNow(claims.get("nbf"), now))) {
                throw new InvalidSdJwtException("Swiss identity statement is not currently valid");
            }
            var anchorKey = DidUtil.resolveJwkFromDidKey(keyId);
            if (!JwsUtil.verify(
                    compact,
                    anchorKey,
                    SdJwtUtil.DEFAULT_SUPPORTED_SIG_ALGORITHMS,
                    List.of(IDENTITY_STATEMENT_TYPE))) {
                throw new InvalidSdJwtException("Invalid Swiss identity statement signature");
            }
        } catch (InvalidSdJwtException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidSdJwtException(
                    "Could not verify Swiss identity statement for " + subjectDid, exception);
        }
    }

    private X509Certificate parseCertificate(String value) {
        try {
            var normalized = value
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(
                            Base64.getDecoder().decode(normalized)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid EUDI trust-anchor certificate", exception);
        }
    }

    private String serializedStatement(String body) throws Exception {
        if (!hasText(body)) throw new InvalidSdJwtException("Trust registry returned an empty statement");
        var trimmed = body.trim();
        if (trimmed.startsWith("\"")) return objectMapper.readTree(trimmed).asString();
        if (trimmed.startsWith("{")) {
            JsonNode object = objectMapper.readTree(trimmed);
            for (var field : List.of("statement", "jwt", "value")) {
                if (object.has(field) && object.get(field).isString()) {
                    return object.get(field).asString();
                }
            }
        }
        return trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean afterNow(Object value, Instant now) {
        if (!(value instanceof Number number)) return false;
        return Instant.ofEpochSecond(number.longValue()).isAfter(now);
    }
}
