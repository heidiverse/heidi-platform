// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * The client half of a signing service's connection check.
 *
 * <p>A signing service says which clients it accepts; the issuer and verifier publish the public
 * key they would present to it. Together that is what an operator needs: a green tick, or a key to
 * add. The key differs per signing service, so the provider scheme decides which one is shown.
 */
@Service
public class SigningClientStatusService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SigningClientStatusService.class);
    private static final String ISSUER_PATH = "/internal/issuer/v1/signing-client";
    private static final String VERIFIER_PATH = "/internal/verifier/v1/signing-client";

    private final BackendKeys backendKeys;

    @Autowired
    public SigningClientStatusService(
            RestClient.Builder builder,
            @Value("${heidi.platform.issuer-internal-base-url:}") String issuerBaseUrl,
            @Value("${heidi.platform.verifier-internal-base-url:}") String verifierBaseUrl,
            @Value("${heidi.platform.server.api.basic-auth:}") String basicAuth) {
        this(scheme -> {
            var keys = new java.util.LinkedHashMap<String, String>();
            read(builder, issuerBaseUrl, ISSUER_PATH, scheme, basicAuth)
                    .ifPresent(key -> keys.put("issuer", key));
            read(builder, verifierBaseUrl, VERIFIER_PATH, scheme, basicAuth)
                    .ifPresent(key -> keys.put("verifier", key));
            return keys;
        });
    }

    public SigningClientStatusService(BackendKeys backendKeys) {
        this.backendKeys = backendKeys;
    }

    /** The public keys the backends would present to the signing service of this scheme. */
    @FunctionalInterface
    public interface BackendKeys {
        Map<String, String> of(String providerScheme);
    }

    public List<ClientStatus> describe(String providerScheme, Map<String, String> acceptedKeys) {
        var status = new java.util.LinkedHashMap<String, ClientStatus>();
        acceptedKeys.forEach((name, key) -> status.put(name,
                new ClientStatus(name, "platform".equals(name), null)));

        Map<String, String> keys;
        try {
            keys = backendKeys.of(providerScheme);
        } catch (RuntimeException exception) {
            // A name alone cannot prove that the backend's current key is accepted.
            LOGGER.warn("Could not read the backends' signing client keys", exception);
            return List.copyOf(status.values());
        }

        keys.forEach((name, publicKey) -> {
            var accepted = publicKey.equals(acceptedKeys.get(name));
            status.put(name, new ClientStatus(name, accepted, accepted ? null : publicKey));
        });
        return List.copyOf(status.values());
    }

    private static java.util.Optional<String> read(
            RestClient.Builder builder,
            String baseUrl,
            String path,
            String providerScheme,
            String basicAuth) {
        if (baseUrl == null || baseUrl.isBlank()) return java.util.Optional.empty();
        var request = builder.clone().baseUrl(baseUrl).build()
                .get()
                .uri(uri -> uri.path(path)
                        .queryParam("provider", providerScheme).build())
                .header(HttpHeaders.ACCEPT, "application/json");
        if (basicAuth != null && !basicAuth.isBlank()) {
            request.header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth);
        }
        var response = request.retrieve()
                .body(ClientKeyResponse.class);
        return response == null || response.publicKey() == null || response.publicKey().isBlank()
                ? java.util.Optional.empty()
                : java.util.Optional.of(response.publicKey());
    }

    private record ClientKeyResponse(String client, String publicKey) {}

    /** A client of a signing service: known to it, or waiting with the key to add. */
    public record ClientStatus(String name, boolean known, String publicKey) {}
}
