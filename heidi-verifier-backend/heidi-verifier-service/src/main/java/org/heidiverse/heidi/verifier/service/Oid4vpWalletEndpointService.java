// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.JWEAlgorithm;

import org.heidiverse.heidi.verifier.data.service.VerificationRequestService;
import org.heidiverse.heidi.verifier.data.service.VerificationSubmissionService;
import org.heidiverse.heidi.verifier.model.api.wallet.*;
import org.heidiverse.heidi.verifier.model.exception.DuplicateSubmissionException;
import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.heidiverse.heidi.verifier.model.vp.*;
import org.heidiverse.heidi.verifier.service.credentials.CredentialFormatService;
import org.heidiverse.heidi.verifier.service.credentials.DcqlService;
import org.heidiverse.heidi.verifier.service.util.TransactionDataHasher;
import org.heidiverse.heidi.verifier.service.util.ResponseEncryptionSessionKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class Oid4vpWalletEndpointService {

    private static final Logger logger = LoggerFactory.getLogger(Oid4vpWalletEndpointService.class);
    private final ObjectMapper objectMapper;
    private final String responseUrl;
    private final VerificationRequestService verificationRequestService;
    private final VerificationSubmissionService verificationSubmissionService;
    private final List<CredentialFormatService> credentialFormatServices;
    private final DcqlService dcqlService;
    private final PossumValidationService possumValidationService;
    private final SigningOperationClient signingOperationClient;

    private final ResponseEncryptionSessionKeys sessionKeys;

    @Autowired
    public Oid4vpWalletEndpointService(
            final ObjectMapper objectMapper,
            @Value("${heidi.verifier.oid4vp.wallet.response-base-url}") final String responseBaseUrl,
            final VerificationRequestService verificationRequestService,
            final VerificationSubmissionService verificationSubmissionService,
            final List<CredentialFormatService> credentialFormatServices,
            final DcqlService dcqlService,
            final PossumValidationService possumValidationService,
            final SigningOperationClient signingOperationClient,
            final ResponseEncryptionSessionKeys sessionKeys) {
        this.objectMapper = objectMapper;
        this.responseUrl =
                UriComponentsBuilder.fromUriString(responseBaseUrl)
                        .path("/v1/wallet/authorization")
                        .toUriString();
        this.verificationRequestService = verificationRequestService;
        this.verificationSubmissionService = verificationSubmissionService;
        this.credentialFormatServices = credentialFormatServices;
        this.dcqlService = dcqlService;
        this.possumValidationService = possumValidationService;
        this.signingOperationClient = signingOperationClient;
        this.sessionKeys = sessionKeys;
    }

    public String lookupSchemaId(final String requestId) {
        final var verificationRequestData =
                verificationRequestService.findVerificationRequestData(requestId);
        return verificationRequestData.schemaLookup();
    }

    /**
     * Looks up the requested Verifiable Credentials for the given request-id, and returns a
     * corresponding presentation definition.
     *
     * @param requestId The request-id as communicated to the wallet, e.g. by means of a QR code.
     * @return A presentation definition requesting the required credentials, along with credential
     *     format requirements, verifier metadata, and some sort of verifier identification.
     */
    public VerificationRequest lookupRequestData(final String requestId) {
        final String transactionId = verificationRequestService.findTransactionId(requestId);
        if (verificationSubmissionService.submissionExists(transactionId)) {
            throw new DuplicateSubmissionException(requestId);
        }
        verificationRequestService.markStarted(requestId);
        final var verificationRequestData =
                verificationRequestService.findVerificationRequestData(requestId);
        var responseEncryptionPolicy = PresentationProfilePolicy.resolve(
                verificationRequestData.presentationProfileId());
        validateResponseMode(verificationRequestData.responseMode(), responseEncryptionPolicy);
        var builder =
                VerificationRequest.builder()
                        .withClientId(verificationRequestData.clientId())
                        .withResponseUri(responseUrl)
                        .withResponseType("vp_token") // we only support a single response type
                        .withResponseMode(
                                verificationRequestData
                                        .responseMode()) // we only support a single response mode
                        .withNonce(verificationRequestData.nonce())
                        .withState(requestId)
                        .withAud("https://self-issued.me/v2")
                        .withClientMetadata(
                                ClientMetadata.from(
                                        verificationRequestData.OID4VPVersion(),
                                        credentialFormatServices.stream()
                                                .flatMap(
                                                        service ->
                                                                service
                                                                        .getVpFormatObject(
                                                                                verificationRequestData
                                                                                        .OID4VPVersion())
                                                .entrySet()
                                                .stream())
                                        .collect(Collectors.toMap(
                                                Map.Entry::getKey,
                                                Map.Entry::getValue)),
                                        responseEncryptionPolicy.responseEncryptionAlg(),
                                        responseEncryptionPolicy.responseEncryptionEnc(),
                                        responseEncryptionPolicy.responseEncryptionEncs(),
                                        responseEncryptionJwks(requestId),
                                        verificationRequestData.clientMetadata()))
                        .withDcqlQuery(verificationRequestData.dcqlQuery())
                        .withTransactionData(verificationRequestData.transactionData())
                        .withVeriferAttestation(verificationRequestData.verifierAttestations());
        builder = builder.withScope(verificationRequestData.scope());

        if (verificationRequestData.provingKeys() != null
                && verificationRequestData.verificationKeys() != null
                && verificationRequestData.zkpDefinition() != null
                && !verificationRequestData.provingKeys().isBlank()
                && !verificationRequestData.verificationKeys().isBlank()) {
            var provingKey = verificationRequestData.provingKeys();
            var zkpDefinition = verificationRequestData.zkpDefinition();
            var bbsIssuer = signingOperationClient.bbsIssuerMetadata(
                    verificationRequestData.schemaLookup(),
                    bbsCredentialQueryIds(verificationRequestData.dcqlQuery()));
            builder =
                    builder.withZkp(
                            new Zkp(
                                    zkpDefinition,
                                    provingKey,
                                    bbsIssuer.issuerPk(),
                                    bbsIssuer.issuerId(),
                                    bbsIssuer.issuerKeyId()));
        }
        return builder.build();
    }

    static void validateResponseMode(
            String storedResponseMode, PresentationProfilePolicy policy) {
        if (!Objects.equals(storedResponseMode, policy.responseMode())) {
            throw new VpVerificationException(
                    "Stored response mode does not match presentation profile");
        }
    }

    static List<String> bbsCredentialQueryIds(uniffi.kapun_dcql_rust.DcqlQuery dcqlQuery) {
        if (dcqlQuery == null || dcqlQuery.getCredentials() == null) return List.of();
        return dcqlQuery.getCredentials().stream()
                .filter(query -> "bbs-termwise".equals(query.getFormat()))
                .map(uniffi.kapun_dcql_rust.CredentialQuery::getId)
                .toList();
    }

    private Map<String, Object> responseEncryptionJwks(final String requestId) {
        var existing = verificationRequestService.responseEncryptionKey(requestId);
        var key = existing.map(value -> new ResponseEncryptionSessionKeys.ResponseKey(
                        value.requestId(), value.keyId(), value.publicJwk(), value.encryptedPrivateJwk()))
                .orElseGet(() -> {
                    var generated = sessionKeys.generate(requestId);
                    var stored = verificationRequestService.saveResponseEncryptionKey(
                            requestId,
                            generated.keyId(),
                            generated.publicJwk(),
                            generated.encryptedPrivateJwk());
                    return new ResponseEncryptionSessionKeys.ResponseKey(
                            stored.requestId(),
                            stored.keyId(),
                            stored.publicJwk(),
                            stored.encryptedPrivateJwk());
                });
        try {
            var publicJwk = JWK.parse(key.publicJwk()).toJSONObject();
            publicJwk.put("use", KeyUse.ENCRYPTION.getValue());
            publicJwk.put("alg", JWEAlgorithm.ECDH_ES.getName());
            return Map.of("keys", List.of(publicJwk));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored response encryption public key is invalid", exception);
        }
    }

    /**
     * Parses and verifies the submitted list of VP tokens, and resolves the input descriptor
     * mappings to obtain a set of disclosed claims.
     *
     * @param vpTokens Either a single Verifiable Presentation or an array of JSON strings and JSON
     *     objects, each representing a Verifiable Presentation.
     * @param state The request-id of the corresponding authorization request.
     * @return SubmissionResponse
     * @throws VpVerificationException If any parsing or verification steps fail.
     */
    public SubmissionResponse handleAuthorizationResponse(
            Object vpTokens, String state, String mdocGeneratedNonce)
            throws VpVerificationException {
        return handleAuthorizationResponse(vpTokens, state, mdocGeneratedNonce, null);
    }

    public SubmissionResponse handleAuthorizationResponse(
            Object vpTokens,
            String state,
            String mdocGeneratedNonce,
            byte[] responseEncryptionKeyThumbprint)
            throws VpVerificationException {
        final var verificationRequestData =
                verificationRequestService.findVerificationRequestData(state);
        final var dcqlQuery = verificationRequestData.dcqlQuery();
        if (dcqlQuery == null) {
            throw new VpVerificationException("OID4VP requests require dcql_query.");
        }

        final String transactionId = verificationRequestService.findTransactionId(state);
        if (verificationSubmissionService.submissionExists(transactionId)) {
            logger.warn("Submission already exists for request id {}", state);
            throw new DuplicateSubmissionException(state);
        }

        final var transactionData = verificationRequestData.transactionData();
        final var transactionDataHashes =
                transactionData != null
                        ? hashAndBase64UrlEncodeTransactionData(transactionData)
                        : null;

        final Map<String, String> vpTokensMap;
        try {
            // vp_token is a map from credential query ids to VP tokens.
            if (vpTokens instanceof String vps) {
                vpTokensMap = readVpTokenMap(vps);
            } else if (vpTokens instanceof Map<?, ?>) {
                vpTokensMap = normalizeVpTokenMap(vpTokens);
            } else {
                throw new VpVerificationException(
                        "failed to deserialize map of strings from VP token map");
            }
        } catch (JacksonException e) {
            throw new VpVerificationException(
                    "failed to deserialize map of strings from VP token map");
        }

        final Map<String, Object> disclosuresPerCredential;
        logger.info("Starting DCQL presentation verification for request id {}", state);
        logger.info("VP tokens map: {}", vpTokensMap.keySet());

        try {
            disclosuresPerCredential =
                    dcqlService.parseAndVerify(
                            verificationRequestData,
                            vpTokensMap,
                            dcqlQuery,
                            mdocGeneratedNonce,
                            transactionDataHashes,
                            responseUrl,
                            responseEncryptionKeyThumbprint);
        } catch (Exception e) {
            logger.info("Failed to verify DCQL presentation for request id {}", state, e);
            final var errorDescription =
                    "Failed to verify dcql presentation due to: " + e.getMessage();
            verificationRequestService.markFailed(state);
            throw new VpVerificationException(errorDescription);
        }

        if (verificationRequestData.validationMode().isEnabled()) {
            final boolean validationResult;
            try {
                validationResult =
                        possumValidationService.validate(
                                verificationRequestData.validationLogic(),
                                disclosuresPerCredential);
            } catch (Exception e) {
                logger.info("Failed to execute Possum validation for request id {}", state, e);
                verificationRequestService.markFailed(state);
                throw new VpVerificationException(
                        "Failed to execute Possum validation due to: " + e.getMessage());
            }
            verificationRequestService.setValidationResult(state, validationResult);
            if (verificationRequestData.validationMode().isEnforced() && !validationResult) {
                verificationRequestService.markFailed(state);
                throw new VpVerificationException("Possum validation did not match");
            }
        }

        if (!verificationSubmissionService.submissionExists(transactionId)) {
            verificationSubmissionService.saveVerificationSubmission(
                    transactionId,
                    disclosuresPerCredential,
                    verificationRequestData.storeVpToken()
                            ? objectMapper.valueToTree(vpTokens)
                            : null);
            verificationRequestService.markSucceeded(state);
        } else {
            logger.warn("Submission already exists for request id {}", state);
            throw new DuplicateSubmissionException(state);
        }

        logger.debug("Successfully stored credential presentation for request id {}", state);
        if (verificationRequestData.redirectUri() == null) {
            return new SubmissionResponse(null);
        }
        return new SubmissionResponse(verificationRequestData.redirectUri() + "#" + transactionId);
    }

    private Map<String, String> readVpTokenMap(final String vpTokens) throws JacksonException {
        try {
            return objectMapper.readerForMapOf(String.class).readValue(vpTokens);
        } catch (JacksonException ex) {
            final var mapWithList =
                    objectMapper.readValue(
                            vpTokens, new TypeReference<Map<String, List<String>>>() {});
            return mapWithList.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getFirst()));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> normalizeVpTokenMap(final Object vpTokens) {
        if (((Map<?, ?>) vpTokens).values().stream().allMatch(v -> v instanceof String)) {
            return (Map<String, String>) vpTokens;
        }
        if (((Map<?, ?>) vpTokens).values().stream().allMatch(v -> v instanceof List<?>)) {
            return ((Map<String, List<String>>) vpTokens)
                    .entrySet().stream()
                            .collect(
                                    Collectors.toMap(
                                            Map.Entry::getKey, e -> e.getValue().getFirst()));
        }
        throw new VpVerificationException("Unsupported vp token type: " + vpTokens);
    }

    private List<String> hashAndBase64UrlEncodeTransactionData(List<String> transactionData) {
        return TransactionDataHasher.hashAndBase64UrlEncode(transactionData);
    }
}
