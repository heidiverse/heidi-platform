// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.ws.controller;

import io.swagger.v3.oas.annotations.Operation;

import org.heidiverse.heidi.verifier.model.api.verifier.CredentialsRequest;
import org.heidiverse.heidi.verifier.model.api.verifier.CredentialsRequestResponse;
import org.heidiverse.heidi.verifier.model.api.verifier.VerificationResponseData;
import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.heidiverse.heidi.verifier.service.VerifierService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URISyntaxException;
import java.util.Map;

@RestController
@CrossOrigin(originPatterns = "*")
@RequestMapping("/internal/verifier/v1")
public class Oid4vpVerifierController {

    private final ObjectMapper objectMapper;
    private final VerifierService verifierService;

    public Oid4vpVerifierController(
            final ObjectMapper objectMapper, final VerifierService verifierService) {
        this.objectMapper = objectMapper;
        this.verifierService = verifierService;
    }

    @Operation(
            summary =
                    "Endpoint used by the verifier frontend to initiate a client verification"
                            + " request.")
    @PostMapping(
            value = "/par",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CredentialsRequestResponse> initiateTransaction(
            @RequestParam final String credentialRequest) throws VpVerificationException {
        try {
            final var request = objectMapper.readValue(credentialRequest, CredentialsRequest.class);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(verifierService.initiateTransaction(request));
        } catch (JacksonException e) {
            throw new VpVerificationException(
                    "Failed to deserialize credential request from JSON string: " + e.getMessage());
        } catch (URISyntaxException e) {
            throw new VpVerificationException("Invalid Verifier Base URI: " + e.getMessage());
        }
    }

    @Operation(
            summary =
                    "Endpoint used by the verifier frontend to initiate a client verification"
                            + " request.")
    @PostMapping(
            value = "/zkp-par",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CredentialsRequestResponse> initiateTransactionWithZkp(
            @RequestParam String credentialRequest, @RequestParam String definition)
            throws VpVerificationException {
        try {
            final var request = objectMapper.readValue(credentialRequest, CredentialsRequest.class);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(verifierService.initiateZkpTransaction(request, definition));
        } catch (JacksonException e) {
            throw new VpVerificationException(
                    "Failed to deserialize credential request from JSON string: " + e.getMessage());
        } catch (URISyntaxException e) {
            throw new VpVerificationException("Invalid Verifier Base URI: " + e.getMessage());
        }
    }

    @Operation(
            summary =
                    "Endpoint used by the verifier frontend to periodically query for an"
                            + " authorization response.")
    @GetMapping(value = "/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VerificationResponseData> fetchResponseData(
            @RequestParam final String transactionId) {
        return ResponseEntity.ok(verifierService.lookupResponseData(transactionId));
    }

    @Operation(hidden = true)
    @GetMapping(value = "/vp-token", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> fetchVpToken(@RequestParam final String transactionId) {
        return ResponseEntity.ok(verifierService.lookupVpToken(transactionId));
    }

    @Operation(summary = "Endpoint used by the verifier frontend to query request lifecycle state.")
    @GetMapping(value = "/state", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> fetchRequestState(
            @RequestParam final String transactionId) {
        final Map<String, Object> state = new java.util.LinkedHashMap<>();
        state.put("status", verifierService.lookupRequestStatus(transactionId));
        final var validationResult = verifierService.lookupValidationResult(transactionId);
        if (validationResult != null) {
            state.put("validation_result", validationResult);
        }
        return ResponseEntity.ok(state);
    }
}
