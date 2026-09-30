// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.heidiverse.heidi.verifier.data.service.VerificationRequestService;
import org.heidiverse.heidi.verifier.data.service.VerificationSubmissionService;
import org.heidiverse.heidi.verifier.model.entity.VerificationRequestEntity;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class VerifierServiceTransactionDataTest {

    @Test
    void returnsOpaqueTransactionDataWithoutProfileSpecificParsing() {
        final var transactionData = List.of("not-base64", "opaque-value");
        final var requestEntity =
                VerificationRequestEntity.from(
                        "request-id",
                        "transaction-id",
                        "nonce",
                        "client-id",
                        "direct_post.jwt",
                        Instant.now().plusSeconds(300),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        transactionData,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false);

        final var requestService =
                new VerificationRequestService(null) {
                    @Override
                    public Optional<VerificationRequestEntity>
                            findVerificationRequestDataByTransactionId(String transactionId) {
                        return Optional.of(requestEntity);
                    }

                    @Override
                    public Boolean findValidationResultByTransactionId(String transactionId) {
                        return null;
                    }
                };
        final var submissionService =
                new VerificationSubmissionService(null) {
                    @Override
                    public Map<String, Object> findDisclosures(String transactionId) {
                        return Map.of();
                    }
                };

        final var verifierService =
                new VerifierService(
                        requestService,
                        submissionService,
                        Duration.ofMinutes(5),
                        new ObjectMapper());

        final var response = verifierService.lookupResponseData("transaction-id");

        assertEquals(transactionData, response.transactionData());
        assertEquals(Map.of(), response.disclosures());
    }
}
