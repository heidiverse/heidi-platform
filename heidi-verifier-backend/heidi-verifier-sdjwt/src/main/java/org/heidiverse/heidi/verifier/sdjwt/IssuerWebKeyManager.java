// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt;

import org.heidiverse.heidi.shared.wellknown.WellKnownUriUtil;
import org.heidiverse.heidi.verifier.sdjwt.model.CustomJwkMatcher;
import org.heidiverse.heidi.verifier.sdjwt.model.IssuerMetadata;

import tools.jackson.databind.ObjectMapper;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.proc.SimpleSecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.Resource;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IssuerWebKeyManager {

    private static final int REMOTE_KEY_TIMEOUT_MS = 10_000;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DefaultResourceRetriever remoteKeyResourceRetriever =
            new DefaultResourceRetriever(REMOTE_KEY_TIMEOUT_MS, REMOTE_KEY_TIMEOUT_MS);
    private static final Map<String, JWKSource<SecurityContext>> issuerKeySources =
            new ConcurrentHashMap<>();

    private IssuerWebKeyManager() {}

    public static JWK getIssuerKey(final Algorithm alg, final String iss, final String keyId)
            throws URISyntaxException, IOException, KeySourceException {
        if (iss != null && keyId != null) {
            return getIssuerKey(
                    iss,
                    new JWKMatcher.Builder()
                            .keyType(KeyType.forAlgorithm(alg))
                            .keyID(keyId)
                            .build(),
                    IssuerWebKeyManager::signatureKeyUseMatches);
        }
        return null;
    }

    static boolean signatureKeyUseMatches(JWK jwk) {
        return jwk.getKeyUse() == null || KeyUse.SIGNATURE.equals(jwk.getKeyUse());
    }

    private static JWK getIssuerKey(String issuer, JWKMatcher jwkMatcher, CustomJwkMatcher custom)
            throws URISyntaxException, IOException, KeySourceException {
        final JWKSource<SecurityContext> jwkSource;
        if (issuerKeySources.containsKey(issuer)) {
            jwkSource = issuerKeySources.get(issuer);
        } else {
            jwkSource =
                    JWKSourceBuilder.create(
                                    jwtVcIssuerMetadataUri(issuer).toURL(),
                                    url -> {
                                        final IssuerMetadata issuerMetadata;
                                        issuerMetadata =
                                                objectMapper.readValue(
                                                        remoteKeyResourceRetriever
                                                                .retrieveResource(url)
                                                                .getContent(),
                                                        IssuerMetadata.class);
                                        if (issuerMetadata.jwks() != null) {
                                            return new Resource(
                                                    objectMapper.writeValueAsString(
                                                            issuerMetadata.jwks()),
                                                    "jwks");
                                        } else if (issuerMetadata.jwksUri() != null) {
                                            /* NOTE: Nimbus doesn't seem to care about the particular resource content type, but this could easily become a breaking change in future releases */
                                            return new Resource(
                                                    remoteKeyResourceRetriever
                                                            .retrieveResource(
                                                                    toUrl(issuerMetadata.jwksUri()))
                                                            .getContent(),
                                                    "jwks");
                                        } else {
                                            return new Resource("", "jwks");
                                        }
                                    })
                            .build();
            issuerKeySources.put(issuer, jwkSource);
        }
        final List<JWK> jwks =
                jwkSource.get(new JWKSelector(jwkMatcher), new SimpleSecurityContext()).stream()
                        .filter(custom::matches)
                        .toList();
        return !jwks.isEmpty() ? jwks.getFirst() : null;
    }

    private static URL toUrl(String value) throws IOException {
        try {
            return new URI(value).toURL();
        } catch (URISyntaxException | IllegalArgumentException e) {
            final var malformed = new MalformedURLException("Invalid jwks_uri: " + value);
            malformed.initCause(e);
            throw malformed;
        }
    }

    static URI jwtVcIssuerMetadataUri(String issuer) throws URISyntaxException {
        return WellKnownUriUtil.fromIssuer(issuer, "/.well-known/jwt-vc-issuer");
    }
}
