// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.data.service;

import org.heidiverse.heidi.verifier.data.repository.VerificationRequestRepository;
import org.heidiverse.heidi.verifier.model.entity.VerificationRequestEntity;
import org.heidiverse.heidi.shared.trustframework.TrustConfiguration;
import org.heidiverse.heidi.shared.trustframework.TrustFrameworkType;
import org.heidiverse.heidi.verifier.model.validation.ValidationMode;
import org.heidiverse.heidi.verifier.model.vp.VerificationRequestData;
import org.heidiverse.heidi.verifier.model.vp.VerifierAttestation;
import org.kapunsdk.presentation.request.model.OID4VPVersion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uniffi.kapun_dcql_rust.DcqlQuery;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class VerificationRequestService {
    private static final String FAILED = "FAILED";
    private static final String SUCCESS = "SUCCESS";

    private final VerificationRequestRepository verificationRequestRepository;

    public VerificationRequestService(
            final VerificationRequestRepository verificationRequestRepository) {
        this.verificationRequestRepository = verificationRequestRepository;
    }

    @Transactional
    public void saveVerificationRequest(
            final String requestId,
            final String transactionId,
            final String nonce,
            final String clientId,
            final String responseMode,
            final Instant expiry,
            final String redirectUri,
            final String schemaLookup,
            final OID4VPVersion OID4VPVersion,
            final String verifyingKey,
            final String provingKey,
            final String zkpDefinition,
            final DcqlQuery dcqlQuery,
            final List<String> transactionData,
            final String validationLogic,
            final ValidationMode validationMode,
            final String tenantId,
            final Map<String, String> clientMetadata,
            final String signingIdentity,
            final String signingKeyId,
            final String signingTrustSystem,
            final String clientIdScheme,
            final TrustConfiguration trustConfiguration,
            final List<VerifierAttestation> verifierAttestations,
            final boolean storeVpToken,
            final String signingSnapshot) {
        saveVerificationRequest(
                requestId, transactionId, nonce, clientId, responseMode, expiry, redirectUri,
                schemaLookup, OID4VPVersion, verifyingKey, provingKey, zkpDefinition, dcqlQuery,
                null, false,
                transactionData, validationLogic, validationMode, tenantId, clientMetadata,
                signingIdentity, signingKeyId, signingTrustSystem, clientIdScheme,
                trustConfiguration,
                verifierAttestations, storeVpToken, signingSnapshot, null);
    }

    @Transactional
    public void saveVerificationRequest(
            final String requestId,
            final String transactionId,
            final String nonce,
            final String clientId,
            final String responseMode,
            final Instant expiry,
            final String redirectUri,
            final String schemaLookup,
            final OID4VPVersion OID4VPVersion,
            final String verifyingKey,
            final String provingKey,
            final String zkpDefinition,
            final DcqlQuery dcqlQuery,
            final String scope,
            final boolean includeDcqlQuery,
            final List<String> transactionData,
            final String validationLogic,
            final ValidationMode validationMode,
            final String tenantId,
            final Map<String, String> clientMetadata,
            final String signingIdentity,
            final String signingKeyId,
            final String signingTrustSystem,
            final String clientIdScheme,
            final TrustConfiguration trustConfiguration,
            final List<VerifierAttestation> verifierAttestations,
            final boolean storeVpToken,
            final String signingSnapshot,
            final String presentationProfileId) {
        final var verificationRequestEntity =
                VerificationRequestEntity.from(
                        requestId,
                        transactionId,
                        nonce,
                        clientId,
                        responseMode,
                        expiry,
                        redirectUri,
                        schemaLookup,
                        OID4VPVersion,
                        verifyingKey,
                        provingKey,
                        zkpDefinition,
                        dcqlQuery,
                        scope,
                        includeDcqlQuery,
                        transactionData,
                        validationLogic,
                        validationMode,
                        tenantId,
                        clientMetadata,
                        signingIdentity,
                        signingKeyId,
                        signingTrustSystem,
                        clientIdScheme,
                        trustFramework(trustConfiguration),
                        eudiTrustAnchors(trustConfiguration),
                        swissTrustAnchor(trustConfiguration),
                        swissTrustRegistryBaseUrl(trustConfiguration),
                        verifierAttestations,
                        storeVpToken,
                        presentationProfileId);
        verificationRequestEntity.setSigningSnapshot(signingSnapshot);
        verificationRequestRepository.save(verificationRequestEntity);
    }

    @Transactional(readOnly = true)
    public String findTransactionId(final String requestId) {
        return verificationRequestRepository
                .findByRequestId(requestId)
                .map(VerificationRequestEntity::getTransactionId)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found"));
    }

    @Transactional
    public void markStarted(final String requestId) {
        verificationRequestRepository
                .findByRequestId(requestId)
                .ifPresent(
                        verificationRequestEntity -> {
                            if ("NOT_STARTED".equals(verificationRequestEntity.getStatus())) {
                                verificationRequestEntity.setStatus("STARTED");
                            }
                        });
    }

    @Transactional
    public void markFailed(final String requestId) {
        verificationRequestRepository
                .findByRequestIdForUpdate(requestId)
                .ifPresent(
                        entity -> {
                            // Finish and erase together, serialized against key creation.
                            entity.setStatus(FAILED);
                            entity.clearResponseEncryptionKey();
                        });
    }

    @Transactional
    public void markSucceeded(final String requestId) {
        verificationRequestRepository
                .findByRequestIdForUpdate(requestId)
                .ifPresent(
                        entity -> {
                            // Completion must never commit with its private key retained.
                            entity.setStatus(SUCCESS);
                            entity.clearResponseEncryptionKey();
                        });
    }

    @Transactional
    public Optional<ResponseEncryptionKey> responseEncryptionKey(final String requestId) {
        return verificationRequestRepository.findByRequestIdForUpdate(requestId)
                .filter(entity -> entity.getResponseEncryptionKeyId() != null
                        && entity.getResponseEncryptionPublicJwk() != null
                        && entity.getResponseEncryptionPrivateJwk() != null)
                .map(entity -> new ResponseEncryptionKey(
                        entity.getRequestId(),
                        entity.getResponseEncryptionKeyId(),
                        entity.getResponseEncryptionPublicJwk(),
                        entity.getResponseEncryptionPrivateJwk()));
    }

    @Transactional
    public ResponseEncryptionKey saveResponseEncryptionKey(
            final String requestId,
            final String keyId,
            final String publicJwk,
            final String encryptedPrivateJwk) {
        return verificationRequestRepository.findByRequestIdForUpdate(requestId)
                .map(entity -> {
                    if (FAILED.equals(entity.getStatus()) || SUCCESS.equals(entity.getStatus())) {
                        throw new IllegalStateException("Verification request has finished");
                    }
                    if (entity.getResponseEncryptionKeyId() == null
                            || entity.getResponseEncryptionPublicJwk() == null
                            || entity.getResponseEncryptionPrivateJwk() == null) {
                        entity.setResponseEncryptionKey(keyId, publicJwk, encryptedPrivateJwk);
                        return new ResponseEncryptionKey(
                                requestId, keyId, publicJwk, encryptedPrivateJwk);
                    }
                    return new ResponseEncryptionKey(
                            entity.getRequestId(),
                            entity.getResponseEncryptionKeyId(),
                            entity.getResponseEncryptionPublicJwk(),
                            entity.getResponseEncryptionPrivateJwk());
                })
                .orElseThrow(() -> new NoSuchElementException("Verification request not found"));
    }

    @Transactional(readOnly = true)
    public List<ResponseEncryptionKey> responseEncryptionKeys(final Instant now) {
        return verificationRequestRepository.findByExpiresAtAfter(now).stream()
                .filter(entity -> entity.getResponseEncryptionKeyId() != null
                        && entity.getResponseEncryptionPublicJwk() != null
                        && entity.getResponseEncryptionPrivateJwk() != null)
                .map(entity -> new ResponseEncryptionKey(
                        entity.getRequestId(),
                        entity.getResponseEncryptionKeyId(),
                        entity.getResponseEncryptionPublicJwk(),
                        entity.getResponseEncryptionPrivateJwk()))
                .toList();
    }

    @Transactional
    public void setValidationResult(final String requestId, final boolean validationResult) {
        verificationRequestRepository
                .findByRequestId(requestId)
                .ifPresent(entity -> entity.setValidationResult(validationResult));
    }

    @Transactional(readOnly = true)
    public Boolean findValidationResultByTransactionId(final String transactionId) {
        return verificationRequestRepository.findByTransactionId(transactionId).stream()
                .map(VerificationRequestEntity::getValidationResult)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public String findStatusByTransactionId(final String transactionId) {
        final var verificationRequests =
                verificationRequestRepository.findByTransactionId(transactionId);
        if (verificationRequests.isEmpty()) {
            throw new NoSuchElementException("Transaction not found");
        }
        return aggregateStatus(verificationRequests);
    }

    static String aggregateStatus(final List<VerificationRequestEntity> verificationRequests) {
        final var statuses =
                verificationRequests.stream()
                        .map(VerificationRequestEntity::getStatus)
                        .collect(Collectors.toSet());
        if (statuses.contains("SUCCESS")) {
            return "SUCCESS";
        }
        if (statuses.contains("FAILED")) {
            return "FAILED";
        }
        if (statuses.contains("STARTED")) {
            return "STARTED";
        }
        return "NOT_STARTED";
    }

    /**
     * Finds the OID4VP request data bound to the passed request id.
     *
     * @param requestId id provided to the wallet by the verification frontend. Used for
     *     communication between the verification backend and the wallet.
     * @return verification request data
     * @throws NoSuchElementException when no transaction is found
     */
    @Transactional(readOnly = true)
    public VerificationRequestData findVerificationRequestData(final String requestId) {
        return verificationRequestRepository
                .findByRequestId(requestId)
                .map(
                        verificationRequestEntity ->
                                new VerificationRequestData(
                                        verificationRequestEntity.getNonce(),
                                        verificationRequestEntity.getClientId(),
                                        verificationRequestEntity.getResponseMode(),
                                        verificationRequestEntity.getRedirectUri(),
                                        verificationRequestEntity.getSchemaLookup(),
                                        verificationRequestEntity.getVerifyingKey(),
                                        verificationRequestEntity.getProvingKey(),
                                        verificationRequestEntity.getZkpDefinition(),
                                        verificationRequestEntity.getDcqlQuery(),
                                        verificationRequestEntity.getScope(),
                                        verificationRequestEntity.isIncludeDcqlQuery(),
                                        verificationRequestEntity.getTransactionData(),
                                        verificationRequestEntity.getValidationLogic(),
                                        verificationRequestEntity.getValidationMode(),
                                        verificationRequestEntity.getTenantId(),
                                        verificationRequestEntity.getClientMetadata(),
                                        verificationRequestEntity.getSigningIdentity(),
                                        verificationRequestEntity.getSigningKeyId(),
                                        verificationRequestEntity.getSigningTrustSystem(),
                                        verificationRequestEntity.getClientIdScheme(),
                                        verificationRequestEntity.getOID4VPVersion(),
                                        new TrustConfiguration(
                                                verificationRequestEntity.getTrustFramework(),
                                                verificationRequestEntity.getEudiTrustAnchors(),
                                                verificationRequestEntity.getSwissTrustAnchor(),
                                                verificationRequestEntity
                                                        .getSwissTrustRegistryBaseUrl()),
                                        verificationRequestEntity.getVerifierAttestations(),
                                        verificationRequestEntity.isStoreVpToken(),
                                        verificationRequestEntity.getSigningSnapshot(),
                                        verificationRequestEntity.getPresentationProfileId()))
                .orElseThrow(() -> new NoSuchElementException("Transaction not found"));
    }

    private static TrustFrameworkType trustFramework(TrustConfiguration configuration) {
        return configuration == null ? null : configuration.trustFramework();
    }

    private static List<String> eudiTrustAnchors(TrustConfiguration configuration) {
        return configuration == null ? null : configuration.eudiTrustAnchors();
    }

    private static String swissTrustAnchor(TrustConfiguration configuration) {
        return configuration == null ? null : configuration.swissTrustAnchor();
    }

    private static String swissTrustRegistryBaseUrl(TrustConfiguration configuration) {
        return configuration == null ? null : configuration.swissTrustRegistryBaseUrl();
    }

    @Transactional(readOnly = true)
    public Optional<VerificationRequestEntity> findVerificationRequestDataByTransactionId(
            final String transactionId) {
        List<VerificationRequestEntity> entities =
                verificationRequestRepository.findByTransactionId(transactionId);
        return entities.isEmpty() ? Optional.empty() : Optional.of(entities.getFirst());
    }

    @Transactional(readOnly = true)
    public List<String> finishedSigningFlows() {
        return verificationRequestRepository.finishedSigningFlows();
    }

    @Transactional
    public void markSigningFlowReleased(String flowId) {
        verificationRequestRepository.findByTransactionId(flowId)
                .forEach(VerificationRequestEntity::markSigningFlowReleased);
    }

    @Transactional
    public long cleanupExpiredEntries() {
        return verificationRequestRepository.deleteByExpiresAtBefore(Instant.now());
    }

    public record ResponseEncryptionKey(
            String requestId,
            String keyId,
            String publicJwk,
            String encryptedPrivateJwk) {}
}
