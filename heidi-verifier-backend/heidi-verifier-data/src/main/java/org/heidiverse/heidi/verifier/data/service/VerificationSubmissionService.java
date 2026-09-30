// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.data.service;

import org.heidiverse.heidi.verifier.data.repository.VerificationSubmissionRepository;
import org.heidiverse.heidi.verifier.model.entity.VerificationSubmissionEntity;
import org.heidiverse.heidi.verifier.model.exception.VerificationResponseNotFoundException;

import tools.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class VerificationSubmissionService {

    private final VerificationSubmissionRepository verificationSubmissionRepository;

    public VerificationSubmissionService(
            final VerificationSubmissionRepository verificationSubmissionRepository) {
        this.verificationSubmissionRepository = verificationSubmissionRepository;
    }

    @Transactional
    public void saveVerificationSubmission(
            final String transactionId, final Map<String, Object> disclosures) {
        saveVerificationSubmission(transactionId, disclosures, null);
    }

    @Transactional
    public void saveVerificationSubmission(
            final String transactionId,
            final Map<String, Object> disclosures,
            final JsonNode vpToken) {
        final var verificationSubmissionEntity =
                VerificationSubmissionEntity.from(transactionId, disclosures, vpToken);
        verificationSubmissionRepository.save(verificationSubmissionEntity);
    }

    @Transactional(readOnly = true)
    public boolean submissionExists(final String transactionId) {
        return verificationSubmissionRepository.existsByTransactionId(transactionId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> findDisclosures(final String transactionId) {
        return verificationSubmissionRepository
                .findByTransactionId(transactionId)
                .map(VerificationSubmissionEntity::getDisclosures)
                .orElseThrow(() -> new VerificationResponseNotFoundException(transactionId));
    }

    @Transactional(readOnly = true)
    public JsonNode findVpToken(final String transactionId) {
        return verificationSubmissionRepository
                .findByTransactionId(transactionId)
                .map(VerificationSubmissionEntity::getVpToken)
                .filter(java.util.Objects::nonNull)
                .orElseThrow(() -> new VerificationResponseNotFoundException(transactionId));
    }
}
