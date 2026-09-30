// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;


import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.heidiverse.heidi.shared.trustframework.TrustFrameworkType;
import org.heidiverse.heidi.verifier.model.vp.VerificationRequestData;
import org.kapunsdk.credentials.Mdoc;
import org.kapunsdk.credentials.mdoc.MDocVerificationException;
import org.kapunsdk.credentials.mdoc.VerificationKt;
import org.kapunsdk.credentials.mdoc.VerificationStep;
import org.kapunsdk.presentation.request.model.OID4VPVersion;
import org.kapunsdk.util.extensions.ValueExtensionKt;
import uniffi.kapun_crypto_rust.Kapun_crypto_rust_jvmKt;
import uniffi.kapun_util_rust.JsonNumber;

import tools.jackson.databind.ObjectMapper;
import io.jsonwebtoken.lang.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

@Service
public class MdocService implements CredentialFormatService {

    /** Value typically used to identify a credential of type mdoc */
    public static final String FORMAT_IDENTIFIER = "mso_mdoc";

    /** Supported signature algorithms */
    public static final List<String> SUPPORTED_SIG_ALGORITHMS =
            List.of("ES256", "ES384", "ES512", "EdDSA");

    public static final List<Integer> SUPPORTED_COSE_SIG_ALGORITHMS = List.of(-7, -35, -36, -8);

    private final ObjectMapper objectMapper;
    private final boolean verifyIssuerSignature;
    private final boolean verifyDeviceSignature;
    private final RequestTrustVerifierFactory trustVerifierFactory;

    @org.springframework.beans.factory.annotation.Autowired
    public MdocService(
            final ObjectMapper objectMapper,
            @Value("${heidi.verifier.oid4vp.verify-issuer-signature}")
                    final boolean verifyIssuerSignature,
            @Value("${heidi.verifier.oid4vp.verify-device-signature}")
                    final boolean verifyDeviceSignature,
            final RequestTrustVerifierFactory trustVerifierFactory) {
        this.objectMapper = objectMapper;
        this.verifyIssuerSignature = verifyIssuerSignature;
        this.verifyDeviceSignature = verifyDeviceSignature;
        this.trustVerifierFactory = trustVerifierFactory;
    }

    @Override
    public String getFormatIdentifier() {
        return FORMAT_IDENTIFIER;
    }

    @Override
    public Map<String, Object> getVpFormatObject(OID4VPVersion OID4VPVersion) {
        if (OID4VPVersion.getVersion() < OID4VPVersion.DRAFT_28.getVersion()) {
            return Map.of(FORMAT_IDENTIFIER, Map.of("alg", SUPPORTED_SIG_ALGORITHMS));
        } else {
            return Map.of(
                    FORMAT_IDENTIFIER,
                    Map.of(
                            "issuerauth_alg_values",
                            SUPPORTED_COSE_SIG_ALGORITHMS,
                            "deviceauth_alg_values",
                            SUPPORTED_COSE_SIG_ALGORITHMS));
        }
    }

    @Override
    public Map<String, Object> getFormatObject() {
        return Map.of(FORMAT_IDENTIFIER, Map.of("alg", SUPPORTED_SIG_ALGORITHMS));
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
            throws VpVerificationException, NoSuchAlgorithmException {
        return parseAndVerify(vpToken, requireCryptographicKeyBinding, nonce,
                mdocGeneratedNonce, clientId, transactionData, responseUri, null);
    }

    public Map<String, Object> parseAndVerify(
            String vpToken,
            Boolean requireCryptographicKeyBinding,
            String nonce,
            String mdocGeneratedNonce,
            String clientId,
            List<String> transactionData,
            String responseUri,
            VerificationRequestData request)
            throws VpVerificationException, NoSuchAlgorithmException {
        return parseAndVerify(
                vpToken,
                requireCryptographicKeyBinding,
                nonce,
                mdocGeneratedNonce,
                clientId,
                transactionData,
                responseUri,
                request,
                null);
    }

    public Map<String, Object> parseAndVerify(
            String vpToken,
            Boolean requireCryptographicKeyBinding,
            String nonce,
            String mdocGeneratedNonce,
            String clientId,
            List<String> transactionData,
            String responseUri,
            VerificationRequestData request,
            byte[] responseEncryptionKeyThumbprint)
            throws VpVerificationException, NoSuchAlgorithmException {
        final Map<String, Object> disclosedClaimSet = new HashMap<>();

        // deserialize credential presentation
        try {

            final var documents =
                    VerificationKt.parseVpToken(Base64.getUrlDecoder().decode(vpToken));

            if (documents != null && documents.size() == 1) {
                final var mdoc = documents.getFirst();

                if (request != null) {
                    var trust = request.trustConfiguration();
                    if (trust != null && trust.trustFramework() == TrustFrameworkType.CH) {
                        throw new VpVerificationException(
                                "Swiss DID trust verification is not supported for MDoc credentials");
                    }
                    var requestVerifier = trustVerifierFactory.x509Verifier(request);
                    if (requestVerifier != null && !requestVerifier.validCertChain(
                            issuerCertificateChain(mdoc))) {
                        throw new VpVerificationException(
                                "MDoc issuer chain is not anchored in the configured identity trust anchors");
                    }
                }

                var verificationSteps =
                        new HashSet<>(Collections.setOf(
                                VerificationStep.Validity.INSTANCE,
                                VerificationStep.DocType.INSTANCE,
                                VerificationStep.CertChain.INSTANCE,
                                VerificationStep.IssuerSigned.INSTANCE));

                if (verifyIssuerSignature) {
                    verificationSteps.add(VerificationStep.IssuerSignature.INSTANCE);
                }

                if (verifyDeviceSignature) {
                    verificationSteps.add(
                            new VerificationStep.DeviceSignature(
                                    clientId,
                                    nonce,
                                    responseEncryptionKeyThumbprint(
                                            request, responseEncryptionKeyThumbprint),
                                    handoverResponseUri(request, responseUri)));
                }

                final var verifiedNamespaces =
                        VerificationKt.verifyDocument(Mdoc.Companion, mdoc, verificationSteps);

                if (!(verifiedNamespaces instanceof uniffi.kapun_util_rust.Value.OrderedObject)) {
                    throw new VpVerificationException("Document namespace is not an ordered object");
                }

                for (final var namespace :
                        ((uniffi.kapun_util_rust.Value.OrderedObject) verifiedNamespaces)
                                .getV1()
                                .getEntries()) {
                    final var key =
                            ((uniffi.kapun_util_rust.Value.String) namespace.component1()).getV1();
                    final var value = ValueExtensionKt.toPlainObject(namespace.component2());

                    disclosedClaimSet.put(key, value);
                }
            } else {
                throw new VpVerificationException("Expected exactly one MDoc document");
            }
        } catch (MDocVerificationException e) {
            throw new VpVerificationException("Failed to verify MDoc: " + e.getMessage());
        }
        return disclosedClaimSet;
    }

    private byte[] responseEncryptionKeyThumbprint(
            VerificationRequestData request, byte[] selectedThumbprint) {
        if (request != null
                && (request.responseMode() == null || !request.responseMode().endsWith(".jwt"))) {
            return null;
        }
        return selectedThumbprint == null || selectedThumbprint.length == 0
                ? null : selectedThumbprint.clone();
    }

    private static String handoverResponseUri(
            VerificationRequestData request, String responseUri) {
        if (request == null
                || request.responseMode() == null
                || request.responseMode().startsWith("direct_post")) {
            return responseUri;
        }
        return request.redirectUri();
    }

    static List<X509Certificate> issuerCertificateChain(String vpToken) {
        try {
            var documents = VerificationKt.parseVpToken(Base64.getUrlDecoder().decode(vpToken));
            if (documents == null || documents.size() != 1) {
                throw new VpVerificationException("Expected exactly one MDoc document");
            }
            return issuerCertificateChain(documents.getFirst());
        } catch (MDocVerificationException exception) {
            throw new VpVerificationException(
                    "Could not parse MDoc issuer certificate chain: " + exception.getMessage());
        }
    }

    private static List<X509Certificate> issuerCertificateChain(
            uniffi.kapun_util_rust.Value document) {
        try {
            var issuerSigned = ValueExtensionKt.get(document, "issuerSigned");
            var issuerAuth = ValueExtensionKt.get(issuerSigned, "issuerAuth");
            var unprotectedHeaders = ValueExtensionKt.asOrderedObject(
                    ValueExtensionKt.get(issuerAuth, 1));
            var chainValue = ValueExtensionKt.get(
                    unprotectedHeaders,
                    new uniffi.kapun_util_rust.Value.Number(new JsonNumber.Integer(33)));
            var encodedCertificates = ValueExtensionKt.isArray(chainValue)
                    ? ValueExtensionKt.asArray(chainValue).stream()
                            .map(ValueExtensionKt::asBytes).filter(Objects::nonNull).toList()
                    : Optional.ofNullable(ValueExtensionKt.asBytes(chainValue)).stream().toList();
            var certificateFactory = CertificateFactory.getInstance("X.509");
            return encodedCertificates.stream()
                    .flatMap(encoded -> Kapun_crypto_rust_jvmKt.extractCerts(encoded).stream())
                    .map(certificate -> {
                        try {
                            return (X509Certificate) certificateFactory.generateCertificate(
                                    new ByteArrayInputStream(certificate.getOriginalCert()));
                        } catch (Exception exception) {
                            throw new IllegalArgumentException(
                                    "Invalid MDoc issuer certificate", exception);
                        }
                    })
                    .toList();
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not read MDoc issuer certificate chain", exception);
        }
    }

}
