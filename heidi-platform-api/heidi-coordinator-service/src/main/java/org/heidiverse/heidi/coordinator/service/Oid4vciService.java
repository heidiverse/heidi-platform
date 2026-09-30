// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class Oid4vciService {

    private static final String INTERNAL_API = "/internal/issuer/v1";

    private final String oid4vciIssuerBaseUrl;
    private final String basicAuth;
    private final ObjectMapper objectMapper;

    public Oid4vciService(
            @Value("${heidi.platform.issuer-internal-base-url}") String oid4vciIssuerBaseUrl,
            @Value("${heidi.platform.server.api.basic-auth:}") String basicAuth,
            ObjectMapper objectMapper) {
        this.oid4vciIssuerBaseUrl = oid4vciIssuerBaseUrl;
        this.basicAuth = basicAuth;
        this.objectMapper = objectMapper;
    }

    public JsonNode fetchCredentialOfferWithToken(String token, String issuerSlug) {
        return fetchCredentialOffer("{\"token\": \"%s\"}".formatted(token), issuerSlug, "c");
    }

    private JsonNode fetchCredentialOffer(String jsonPayload, String issuerSlug, String flowType) {
        HttpURLConnection connection = null;
        try {
            URL url =
                    URI.create(oid4vciIssuerBaseUrl)
                            .resolve(
                                    INTERNAL_API + "/" + issuerSlug + "/" + flowType
                                            + "/credential-offer")
                            .toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            authenticate(connection);
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int status = connection.getResponseCode();
            String response = readResponse(
                    status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status >= 400) {
                throw issuerRejected(status, response);
            }
            try {
                return objectMapper.readTree(response);
            } catch (JacksonException exception) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Issuer returned an invalid credential offer",
                        exception);
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (final IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not reach issuer while creating credential offer",
                    e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readResponse(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                result.append(line);
            }
        }
        return result.toString();
    }

    private ResponseStatusException issuerRejected(int status, String response) {
        String detail = "Issuer rejected credential offer (HTTP " + status + ")";
        try {
            JsonNode error = objectMapper.readTree(response);
            if (error != null) {
                String code = error.path("error").asString("");
                String description = error.path("error_description").asString("");
                if (!code.isBlank() && !description.isBlank()) {
                    detail += ": " + code + ": " + description;
                } else if (!description.isBlank()) {
                    detail += ": " + description;
                }
            }
        } catch (JacksonException ignored) {
            // Keep the stable HTTP-level message for non-JSON upstream failures.
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, detail);
    }

    public JsonNode getConnectionStatus(String connectionId) {
        return getConnectionStatusWithVariant(connectionId, "c");
    }

    public JsonNode getConnectionStatusWithVariant(String connectionId, String flowVariant) {
        try {
            URL url =
                    URI.create(oid4vciIssuerBaseUrl)
                            .resolve(INTERNAL_API + "/" + flowVariant + "/" + connectionId
                                    + "/connectionStatus")
                            .toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            authenticate(connection);

            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                for (String line; (line = reader.readLine()) != null; ) {
                    response.append(line);
                }

                return objectMapper.readTree(response.toString());
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to fetch connection status for ID: " + connectionId, e);
        }
    }

    public String extractConnectionId(JsonNode credentialOffer) {
        JsonNode connectionIdNode = credentialOffer.get("connection_id");
        if (connectionIdNode != null && connectionIdNode.isString()) {
            return connectionIdNode.asString();
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "Issuer response is missing connection_id");
    }

    private void authenticate(HttpURLConnection connection) {
        if (basicAuth.isBlank()) return;

        connection.setRequestProperty("Authorization", "Basic " + basicAuth);
    }
}
