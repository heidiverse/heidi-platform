// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.heidiverse.heidi.verifier.data.repository.VerificationRequestRepository;
import org.heidiverse.heidi.verifier.model.entity.VerificationRequestEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class VerificationRequestServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "SUCCESS"})
    void finishedFlowRejectsNewKey(String status) {
        var request = requestWithStatus(status);
        var repository = mock(VerificationRequestRepository.class);
        when(repository.findByRequestIdForUpdate(request.getRequestId())).thenReturn(Optional.of(request));

        assertThrows(IllegalStateException.class, () -> new VerificationRequestService(repository)
                .saveResponseEncryptionKey(request.getRequestId(), "key", "public-jwk", "encrypted-private-jwk"));
        assertNull(request.getResponseEncryptionPrivateJwk());
    }

    @Test
    void failureClearsResponseKey() {
        var request = requestWithStatus("STARTED");
        request.setResponseEncryptionKey("key", "public-jwk", "encrypted-private-jwk");
        var repository = mock(VerificationRequestRepository.class);
        when(repository.findByRequestIdForUpdate(request.getRequestId())).thenReturn(Optional.of(request));

        new VerificationRequestService(repository).markFailed(request.getRequestId());

        assertEquals("FAILED", request.getStatus());
        assertNull(request.getResponseEncryptionPrivateJwk());
    }

    @Test
    void successClearsResponseKey() {
        var request = requestWithStatus("STARTED");
        request.setResponseEncryptionKey("key", "public-jwk", "encrypted-private-jwk");
        var repository = mock(VerificationRequestRepository.class);
        when(repository.findByRequestIdForUpdate(request.getRequestId())).thenReturn(Optional.of(request));

        new VerificationRequestService(repository).markSucceeded(request.getRequestId());

        assertEquals("SUCCESS", request.getStatus());
        assertNull(request.getResponseEncryptionPrivateJwk());
    }

    @Test
    void aggregateStatusReturnsSuccessWhenOneFlowSucceeded() {
        assertEquals(
                "SUCCESS",
                VerificationRequestService.aggregateStatus(
                        List.of(requestWithStatus("NOT_STARTED"), requestWithStatus("SUCCESS"))));
    }

    @Test
    void aggregateStatusReturnsFailedWhenOneFlowFailedAndNoneSucceeded() {
        assertEquals(
                "FAILED",
                VerificationRequestService.aggregateStatus(
                        List.of(requestWithStatus("NOT_STARTED"), requestWithStatus("FAILED"))));
    }

    @Test
    void aggregateStatusReturnsStartedWhenOneFlowStartedAndNoneFinished() {
        assertEquals(
                "STARTED",
                VerificationRequestService.aggregateStatus(
                        List.of(requestWithStatus("NOT_STARTED"), requestWithStatus("STARTED"))));
    }

    private VerificationRequestEntity requestWithStatus(final String status) {
        final var request =
                VerificationRequestEntity.from(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        "nonce",
                        "client-id",
                        "direct_post",
                        Instant.now().plusSeconds(300),
                        null,
                        "schema",
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
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false);
        request.setStatus(status);
        return request;
    }
}
