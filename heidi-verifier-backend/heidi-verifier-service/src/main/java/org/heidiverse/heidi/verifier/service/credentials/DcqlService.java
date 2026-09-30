// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import org.heidiverse.heidi.verifier.service.credentials.bbs.BbsService;
import org.heidiverse.heidi.verifier.service.SigningOperationClient;

import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.heidiverse.heidi.verifier.model.vp.VerificationRequestData;
import org.kapunsdk.CheckVpTokenCallback;
import org.kapunsdk.NoCredentialSetQueryOptionSatisfiedException;
import org.kapunsdk.VerificationKt;
import org.kapunsdk.util.extensions.ValueExtensionKt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uniffi.kapun_dcql_bbs_rust.BbsParseException;
import uniffi.kapun_dcql_rust.ClaimsQuery;
import uniffi.kapun_dcql_rust.CredentialQuery;
import uniffi.kapun_dcql_rust.DcqlQuery;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class DcqlService {
    private static final Logger logger = LoggerFactory.getLogger(DcqlService.class);

    private final SdJwtService sdJwtService;
    private final MdocService mdocService;
    private final BbsService bbsService;
    private final W3CService w3cService;
    private final OpenBadgeService openBadgeService;
    private final TrustedAuthorityVerifier trustedAuthorityVerifier;
    private final ObjectMapper jacksonObjectMapper;
    private final SigningOperationClient signingOperationClient;

    public DcqlService(
            SdJwtService sdJwtService,
            MdocService mdocService,
            BbsService bbsService,
            W3CService w3cService,
            OpenBadgeService openBadgeService,
            TrustedAuthorityVerifier trustedAuthorityVerifier,
            ObjectMapper jacksonObjectMapper,
            SigningOperationClient signingOperationClient) {
        this.sdJwtService = sdJwtService;
        this.mdocService = mdocService;
        this.bbsService = bbsService;
        this.w3cService = w3cService;
        this.openBadgeService = openBadgeService;
        this.trustedAuthorityVerifier = trustedAuthorityVerifier;
        this.jacksonObjectMapper = jacksonObjectMapper;
        this.signingOperationClient = signingOperationClient;
    }

    public Map<String, Object> parseAndVerify(
            VerificationRequestData verificationRequestData,
            Map<String, String> vpTokens,
            DcqlQuery dcqlQuery,
            String mdocGeneratedNonce,
            final List<String> transactionData,
            String responseUri)
            throws VpVerificationException, JacksonException {
        return parseAndVerify(
                verificationRequestData,
                vpTokens,
                dcqlQuery,
                mdocGeneratedNonce,
                transactionData,
                responseUri,
                null);
    }

    public Map<String, Object> parseAndVerify(
            VerificationRequestData verificationRequestData,
            Map<String, String> vpTokens,
            DcqlQuery dcqlQuery,
            String mdocGeneratedNonce,
            final List<String> transactionData,
            String responseUri,
            byte[] responseEncryptionKeyThumbprint)
            throws VpVerificationException, JacksonException {

        final var nonce = verificationRequestData.nonce();
        final var clientId = verificationRequestData.clientId();
        var bbsIssuer = hasBbsQuery(dcqlQuery)
                ? signingOperationClient.bbsIssuerMetadata(
                        verificationRequestData.schemaLookup(), bbsCredentialQueryIds(dcqlQuery))
                : null;

        final Map<String, Map<String, String>> zkpVerifyingKeys = new HashMap<>();
        if (verificationRequestData.verificationKeys() != null) {
            final Map<String, String> verifyingKeysBase64 =
                    jacksonObjectMapper
                            .readerForMapOf(String.class)
                            .readValue(verificationRequestData.verificationKeys());
            for (Map.Entry<String, String> entry : verifyingKeysBase64.entrySet()) {
                final var verifyingKeysString = new String(
                        Base64.getUrlDecoder().decode(entry.getValue()),
                        StandardCharsets.UTF_8);
                final Map<String, String> verifyingKeys = jacksonObjectMapper
                        .readerForMapOf(String.class)
                        .readValue(verifyingKeysString);
                zkpVerifyingKeys.put(entry.getKey(), verifyingKeys);
            }
        }

        if (bbsService.isClaimBasedPresentation(vpTokens, dcqlQuery)) {
            final var credentialQueries =
                    Objects.requireNonNull(dcqlQuery.getCredentials());


            final var keys = new ArrayList<>(vpTokens.keySet());
            final var key1 = keys.getFirst();
            final var key2 = keys.getLast();

            final var query1 =
                    credentialQueries.stream()
                            .filter(q -> key1.equals(q.getId()))
                            .findFirst()
                            .orElse(null);
            final var query2 =
                    credentialQueries.stream()
                            .filter(q -> key2.equals(q.getId()))
                            .findFirst()
                            .orElse(null);

            if (query1 == null || query2 == null)
                throw new VpVerificationException("Couldn't find matching credential queries");

            try {
                var verifiedClaims = bbsService.parseAndVerifyClaimBased(
                        vpTokens.get(key1),
                        nonce,
                        clientId,
                        query1,
                        query2,
                        bbsIssuer);
                // Claim-based BBS presentations currently expose no issuer trust evidence. The
                // authority verifier therefore accepts unconstrained queries and fails closed for
                // constrained ones.
                trustedAuthorityVerifier.verify(
                        org.kapunsdk.credentials.models.credential.CredentialType.BbsTermwise,
                        vpTokens.get(key1),
                        Map.of(),
                        query1);
                trustedAuthorityVerifier.verify(
                        org.kapunsdk.credentials.models.credential.CredentialType.BbsTermwise,
                        vpTokens.get(key2),
                        Map.of(),
                        query2);
                return verifiedClaims;
            } catch (NoSuchAlgorithmException e) {
                throw new VpVerificationException("NoSuchAlgorithmException: " + e.getMessage());
            } catch (BbsParseException e) {
                throw new VpVerificationException("BbsParseException: " + e.getMessage());
            }
        }

        final Map<String, String> zkpDefinition;
        if (verificationRequestData.zkpDefinition() != null) {
            zkpDefinition =
                    jacksonObjectMapper
                            .readerForMapOf(String.class)
                            .readValue(verificationRequestData.zkpDefinition());
        } else {
            zkpDefinition = new HashMap<>();
        }

        final Set<String> credentialVerificationFailures =
                Collections.synchronizedSet(new LinkedHashSet<>());
        final CheckVpTokenCallback callback = (credentialType, vpToken, id) -> {
            var credentials = dcqlQuery.getCredentials();
            if(credentials == null || credentials.isEmpty()) {
                throw new VpVerificationException("Query contains no credentials");
            }
            var credentialQuery = credentials.stream().filter((item)-> item.getId().equals(id)).findFirst();
            if(credentialQuery.isEmpty()) {
                throw new VpVerificationException("Response contains reference to unknown query");
            }
            var requireCryptographicKeyBinding = credentialQuery.get().getRequireCryptographicHolderBinding() == null || credentialQuery.get().getRequireCryptographicHolderBinding();


            try {
                var verifiedClaims = switch (credentialType) {

                    case SdJwt -> ValueExtensionKt.toPlainValueMap(
                            sdJwtService.parseAndVerify(
                                vpToken,
                                requireCryptographicKeyBinding,
                                nonce,
                                clientId,
                                transactionData,
                                verificationRequestData,
                                trustedAuthorityVerifier.x509FallbackVerifier(
                                        credentialQuery.get())));
                    case Mdoc -> ValueExtensionKt.toPlainValueMap(
                            mdocService.parseAndVerify(
                                vpToken,
                                requireCryptographicKeyBinding,
                                nonce,
                                mdocGeneratedNonce,
                                clientId,
                                transactionData,
                                responseUri,
                                verificationRequestData,
                                responseEncryptionKeyThumbprint));
                    case BbsTermwise -> ValueExtensionKt.toPlainValueMap(
                            bbsService.parseAndVerify(
                                vpToken,
                                requireCryptographicKeyBinding,
                                zkpDefinition.get(id),
                                zkpVerifyingKeys.get(id),
                                nonce,
                                clientId,
                                bbsIssuer));
                    case W3C_VCDM -> ValueExtensionKt.toPlainValueMap(
                            w3cService.parseAndVerify(
                                vpToken,
                                requireCryptographicKeyBinding,
                                nonce,
                                clientId,
                                transactionData,
                                verificationRequestData,
                                trustedAuthorityVerifier.x509FallbackVerifier(
                                        credentialQuery.get())));
                    case OpenBadge303 ->  ValueExtensionKt.toPlainValueMap(
                            openBadgeService.parseAndVerify(
                                    vpToken,
                                    requireCryptographicKeyBinding,
                                    zkpDefinition.get(id),
                                    zkpVerifyingKeys.get(id),
                                    nonce,
                                    clientId));
                    case Unknown -> throw new VpVerificationException("Unknown credential type");
                };
                trustedAuthorityVerifier.verify(
                        credentialType, vpToken, verifiedClaims, credentialQuery.get());
                return verifiedClaims;
            } catch (Exception e) {
                credentialVerificationFailures.add(
                        "credential query id="
                                + id
                                + ", type="
                                + credentialType
                                + ": "
                                + exceptionMessage(e));
                logger.warn(
                        "Failed to verify DCQL vp_token for credential query id={}, credentialType={}, format={}, requireCryptographicHolderBinding={}",
                        id,
                        credentialType,
                        credentialQuery.get().getFormat(),
                        requireCryptographicKeyBinding,
                        e);
                throw e;
            }
        };

        final var nestedMap =
                verifyDcqlPresentation(
                        dcqlQuery, vpTokens, callback, credentialVerificationFailures);

        // Widen Map<String, Map<String, Value>> to Map<String, Object>; the inner Value instances
        // are kept as-is and unwrapped again by HashMapConverter on read.
        return new HashMap<>(nestedMap);
    }

    private static boolean hasBbsQuery(DcqlQuery dcqlQuery) {
        return !bbsCredentialQueryIds(dcqlQuery).isEmpty();
    }

    private static List<String> bbsCredentialQueryIds(DcqlQuery dcqlQuery) {
        if (dcqlQuery == null || dcqlQuery.getCredentials() == null) return List.of();
        return dcqlQuery.getCredentials().stream()
                .filter(query -> "bbs-termwise".equals(query.getFormat()))
                .map(CredentialQuery::getId)
                .toList();
    }

    private Map<String, Map<String, uniffi.kapun_util_rust.Value>> verifyDcqlPresentation(
            DcqlQuery dcqlQuery,
            Map<String, String> vpTokens,
            CheckVpTokenCallback callback,
            Set<String> credentialVerificationFailures) {
        try {
            return VerificationKt.verifyDcqlPresentation(dcqlQuery, vpTokens, callback);
        } catch (Exception e) {
            if (e instanceof NoCredentialSetQueryOptionSatisfiedException) {
                logCredentialSetDiagnostics(dcqlQuery, vpTokens);
                if (!credentialVerificationFailures.isEmpty()) {
                    throw new VpVerificationException(
                            e.getMessage()
                                    + " Credential verification failures: "
                                    + String.join("; ", credentialVerificationFailures));
                }
            }
            throw e;
        }
    }

    private static String exceptionMessage(Exception exception) {
        var message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private void logCredentialSetDiagnostics(DcqlQuery dcqlQuery, Map<String, String> vpTokens) {
        if (!logger.isWarnEnabled()) {
            return;
        }

        final var credentials = dcqlQuery.getCredentials();
        final var credentialSets = dcqlQuery.getCredentialSets();

        logger.warn(
                "No DCQL credential set option was satisfied. vp_token ids={}, credential ids={}, credential sets={}",
                vpTokens.keySet(),
                credentials == null
                        ? List.of()
                        : credentials.stream().map(CredentialQuery::getId).toList(),
                credentialSets);

        if (credentials == null) {
            return;
        }

        for (final var credential : credentials) {
            logger.warn(
                    "DCQL credential query id={}, format={}, meta={}, requireCryptographicHolderBinding={}, claims={}",
                    credential.getId(),
                    credential.getFormat(),
                    credential.getMeta(),
                    credential.getRequireCryptographicHolderBinding(),
                    formatClaims(credential.getClaims()));
        }
    }

    private List<List<uniffi.kapun_credential_core_rust.PointerPart>> formatClaims(
            List<ClaimsQuery> claims) {
        if (claims == null) {
            return List.of();
        }
        return claims.stream().map(ClaimsQuery::getPath).toList();
    }
}
