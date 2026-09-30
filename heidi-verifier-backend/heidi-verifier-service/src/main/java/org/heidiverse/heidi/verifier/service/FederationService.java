// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service;

import org.heidiverse.heidi.verifier.model.api.wallet.ClientMetadata;
import org.heidiverse.heidi.verifier.service.credentials.CredentialFormatService;
import org.kapunsdk.presentation.request.model.OID4VPVersion;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Serves a verifier identity's OpenID Federation entity configuration.
 *
 * <p>The verifier contributes only its metadata. The platform checks the entity belongs to the
 * identity and signs it through the signing service with the identity's federation key, so no
 * federation key lives here.
 */
@Service
public class FederationService {
    private static final String ENTITY_CONFIGURATION_PATH =
            "/internal/platform/v1/identities/{identity}/federation/entity-configuration";

    private final RestClient platformClient;
    private final String verifierBaseUrl;
    private final List<CredentialFormatService> credentialFormatServices;

    @org.springframework.beans.factory.annotation.Autowired
    public FederationService(
            RestClient.Builder builder,
            @Value("${heidi.verifier.platform-internal-base-url}") String platformBaseUrl,
            @Value("${heidi.verifier.platform-basic-auth:}") String basicAuth,
            @Value("${heidi.verifier.public-base-url}") String verifierBaseUrl,
            List<CredentialFormatService> credentialFormatServices) {
        var platformBuilder = builder.clone().baseUrl(platformBaseUrl);
        if (!basicAuth.isBlank()) {
            platformBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth);
        }
        this.platformClient = platformBuilder.build();
        this.verifierBaseUrl = verifierBaseUrl.replaceAll("/+$", "");
        this.credentialFormatServices = credentialFormatServices;
    }

    /** The signed entity configuration, or empty when the identity is in no federation. */
    public Optional<String> entityConfiguration(String identity) {
        var request = Map.of(
                "entityId", entityIdentifier(identity),
                "metadata", Map.of("openid_credential_verifier", verifierMetadata()));
        try {
            return Optional.ofNullable(platformClient.post()
                    .uri(ENTITY_CONFIGURATION_PATH, identity)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class));
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) return Optional.empty();
            throw exception;
        }
    }

    String entityIdentifier(String identity) {
        return verifierBaseUrl + "/" + identity;
    }

    private ClientMetadata verifierMetadata() {
        var vpFormats = credentialFormatServices.stream()
                .flatMap(service -> service.getVpFormatObject(OID4VPVersion.DRAFT_28)
                        .entrySet()
                        .stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        // Per-request keys are supplied in authorization client metadata; federation must not
        // advertise a standing key set.
        return ClientMetadata.from(
                OID4VPVersion.DRAFT_28,
                vpFormats,
                null,
                null,
                null);
    }
}
