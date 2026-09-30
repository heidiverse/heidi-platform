// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.ws.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.heidiverse.heidi.verifier.model.api.verifier.VerificationResponseData;
import org.heidiverse.heidi.verifier.model.exception.VerificationResponseNotFoundException;
import org.heidiverse.heidi.verifier.service.VerifierService;
import org.heidiverse.heidi.verifier.ws.advice.ExceptionHandling;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class Oid4vpVerifierControllerTest {

    @Test
    void fetchRequestStateIncludesPossumValidationResult() throws Exception {
        final var mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new Oid4vpVerifierController(
                                        null, new ValidationResultVerifierService()))
                        .build();

        mockMvc.perform(
                        get("/internal/verifier/v1/state")
                                .param("transactionId", "transaction-id")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.validation_result").value(false));
    }

    @Test
    void fetchResponseDataReturnsSanitizedProblemWhenTransactionIdIsUnknown() throws Exception {
        final var transactionId = "d3baf44b-5740-4408-aba9-c55f8d46ef05";

        final var mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new Oid4vpVerifierController(
                                        null, new MissingVerificationResponseVerifierService()))
                        .setControllerAdvice(new ExceptionHandling())
                        .build();

        mockMvc.perform(
                get("/internal/verifier/v1/authorization")
                                .param("transactionId", transactionId)
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Verification response not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("verification_response_not_found"))
                .andExpect(jsonPath("$.transactionId").value(transactionId))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void doesNotServeLegacyVerifierAlias() throws Exception {
        final var mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new Oid4vpVerifierController(
                                        null, new ValidationResultVerifierService()))
                        .build();

        mockMvc.perform(get("/v1/verifier/state").param("transactionId", "transaction-id"))
                .andExpect(status().isNotFound());
    }

    private static class MissingVerificationResponseVerifierService extends VerifierService {

        MissingVerificationResponseVerifierService() {
            super(null, null, null, null);
        }

        @Override
        public VerificationResponseData lookupResponseData(String transactionId) {
            throw new VerificationResponseNotFoundException(transactionId);
        }
    }

    private static class ValidationResultVerifierService extends VerifierService {

        ValidationResultVerifierService() {
            super(null, null, null, null);
        }

        @Override
        public String lookupRequestStatus(String transactionId) {
            return "SUCCESS";
        }

        @Override
        public Boolean lookupValidationResult(String transactionId) {
            return false;
        }
    }
}
