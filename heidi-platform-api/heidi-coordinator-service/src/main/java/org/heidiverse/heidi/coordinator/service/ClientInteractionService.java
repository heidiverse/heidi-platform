// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service;

import org.heidiverse.heidi.coordinator.data.service.IntegrationProcessDataService;
import org.heidiverse.heidi.coordinator.model.api.ClientInteractionData;
import org.heidiverse.heidi.coordinator.model.api.ClientInteractionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.ZonedDateTime;

/** Resolves the browser-safe projection of an integration process. */
@Service
public class ClientInteractionService {

    private static final String TOKEN_SCHEME = "Bearer ";

    private final IntegrationProcessDataService processDataService;
    private final ClientInteractionTokenService tokenService;
    private final ProcessOrchestrationService orchestrationService;
    private final ObjectMapper objectMapper;

    public ClientInteractionService(
            IntegrationProcessDataService processDataService,
            ClientInteractionTokenService tokenService,
            ProcessOrchestrationService orchestrationService,
            ObjectMapper objectMapper) {
        this.processDataService = processDataService;
        this.tokenService = tokenService;
        this.orchestrationService = orchestrationService;
        this.objectMapper = objectMapper;
    }

    public ClientInteractionResponse get(String authorizationHeader) throws JacksonException {
        String token = extractToken(authorizationHeader);
        var process = processDataService.getByClientInteractionTokenHash(tokenService.hash(token));
        requireActive(process.getExpiresAt());

        var interactionData = objectMapper.treeToValue(
                process.getClientInteractionData(), ClientInteractionData.class);
        var state = orchestrationService.getState(process.getCoordinatorConnectionId());
        JsonNode claims = filterClaims(state.disclosures(), process.getClientDisplayClaims());
        ClientInteractionResponse.DisplayClaims display =
                claims == null || process.getProofSchemeId() == null
                        ? null
                        : new ClientInteractionResponse.DisplayClaims(
                                orchestrationService.getProofScheme(process.getProofSchemeId()),
                                claims);
        JsonNode configuration =
                process.getClientConfiguration() == null
                                || process.getClientConfiguration().isNull()
                        ? objectMapper.createObjectNode()
                        : process.getClientConfiguration();

        return new ClientInteractionResponse(
                process.getAction(),
                process.isUseDcApi(),
                toProcessData(interactionData.crossDevice()),
                toProcessData(interactionData.sameDevice()),
                state.state(),
                display,
                configuration);
    }

    private ClientInteractionResponse.ClientProcessData toProcessData(
            ClientInteractionData.ProcessData processData) {
        if (processData == null) return null;
        return new ClientInteractionResponse.ClientProcessData(
                processData.qrCodeDataPath(), processData.qrCodeDataScheme());
    }

    private JsonNode filterClaims(Object disclosures, JsonNode allowlist) {
        if (disclosures == null || allowlist == null || !allowlist.isArray()) return null;

        JsonNode source = objectMapper.valueToTree(disclosures);
        ObjectNode result = objectMapper.createObjectNode();
        for (JsonNode allowed : allowlist) {
            if (!allowed.isString()) continue;
            String pointer = allowed.stringValue();
            if (pointer == null || !pointer.startsWith("/")) continue;

            JsonNode value = source.at(pointer);
            if (!value.isMissingNode()) setPointer(result, pointer, value);
        }
        return result.isEmpty() ? null : result;
    }

    private void setPointer(ObjectNode root, String pointer, JsonNode value) {
        String[] segments = pointer.substring(1).split("/");
        ObjectNode current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            String segment = decode(segments[i]);
            JsonNode child = current.get(segment);
            if (!(child instanceof ObjectNode)) {
                child = objectMapper.createObjectNode();
                current.set(segment, child);
            }
            current = (ObjectNode) child;
        }
        if (segments.length > 0) current.set(decode(segments[segments.length - 1]), value);
    }

    private String decode(String segment) {
        return segment.replace("~1", "/").replace("~0", "~");
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(TOKEN_SCHEME)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Client token required");
        }
        String token = authorizationHeader.substring(TOKEN_SCHEME.length());
        if (!tokenService.isClientInteractionToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid client token");
        }
        return token;
    }

    private void requireActive(ZonedDateTime expiresAt) {
        if (expiresAt == null || expiresAt.isBefore(ZonedDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Interaction expired");
        }
    }
}
