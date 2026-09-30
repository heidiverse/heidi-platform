// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service;

import org.heidiverse.heidi.shared.signing.SigningOperationResult;
import org.heidiverse.heidi.verifier.model.vp.BbsIssuerMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/** Executes provider-level operations through the generic signing protocol. */
@Service
public class SigningOperationClient {
    private static final String COMPLETED = "COMPLETED";

    private final RestClient platformClient;
    private final ObjectMapper objectMapper;

    public SigningOperationClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${heidi.verifier.platform-internal-base-url}") String platformBaseUrl,
            @Value("${heidi.verifier.platform-basic-auth:}") String basicAuth) {
        var platformBuilder = builder.clone().baseUrl(platformBaseUrl);
        if (!basicAuth.isBlank()) {
            platformBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth);
        }
        this.platformClient = platformBuilder.build();
        this.objectMapper = objectMapper;
    }

    public JsonNode execute(String proofSchemeId, String operation, String inputJson) {
        SigningOperationResult result = platformClient.post()
                .uri("/internal/platform/v1/proof-schemas/{proofSchemeId}/operations/{operation}",
                        proofSchemeId, operation)
                .contentType(MediaType.APPLICATION_JSON)
                .body(inputJson)
                .retrieve()
                .body(SigningOperationResult.class);
        if (result == null) {
            throw new IllegalStateException(
                    "Platform returned no result for operation '" + operation + "'");
        }
        if (!COMPLETED.equals(result.status()) || result.resultJson() == null) {
            throw new IllegalStateException(
                    "Signing provider did not complete operation '" + operation + "'");
        }
        try {
            return objectMapper.readTree(result.resultJson());
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Signing provider returned invalid result for operation '" + operation + "'",
                    exception);
        }
    }

    public BbsIssuerMetadata bbsIssuerMetadata(
            String proofSchemeId, List<String> credentialQueryIds) {
        var metadata = platformClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/platform/v1/proof-schemas/{proofSchemeId}/bbs-issuer-metadata")
                        .queryParam("credentialQueryId", credentialQueryIds.toArray())
                        .build(proofSchemeId))
                .retrieve()
                .body(BbsIssuerMetadata.class);
        if (metadata == null) {
            throw new IllegalStateException("Platform returned no BBS issuer metadata");
        }
        return metadata;
    }
}
