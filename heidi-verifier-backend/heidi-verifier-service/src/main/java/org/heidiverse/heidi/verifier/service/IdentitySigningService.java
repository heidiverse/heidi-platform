// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.util.Base64URL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.heidiverse.heidi.signing.adapters.ProviderJwsSigner;
import org.heidiverse.heidi.signing.adapters.RemoteSigningKeyProvider;
import org.heidiverse.heidi.signing.adapters.SigningClientKeys;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.*;
import java.net.URI;
import java.io.ByteArrayInputStream;

/** Resolves the identity-owned signer through the signing provider. */
@Service
public class IdentitySigningService {
    private final RestClient platformClient;
    private final RestClient.Builder clientBuilder;
    private final ObjectMapper objectMapper;
    private static final String SIGNING_CLIENT = "verifier";

    private final String providerBaseUrl;
    private final String providerAuthenticationMode;
    private final SigningClientKeys clientKeys;

    public IdentitySigningService(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${heidi.verifier.platform-internal-base-url}") String platformBaseUrl,
            @Value("${heidi.verifier.platform-basic-auth:}") String basicAuth,
            @Value("${heidi.verifier.signing-provider.base-url:}") String providerBaseUrl,
            @Value("${heidi.verifier.signing-provider.authentication-mode:}") String providerAuthenticationMode,
            @Value("${heidi.verifier.signing-provider.client-seed:}") String clientSeed) {
        var platformBuilder = builder.clone().baseUrl(platformBaseUrl);
        if (!basicAuth.isBlank()) {
            platformBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth);
        }
        this.platformClient = platformBuilder.build();
        this.clientBuilder = builder;
        this.providerBaseUrl = providerBaseUrl;
        this.providerAuthenticationMode = providerAuthenticationMode;
        this.clientKeys = new SigningClientKeys(SIGNING_CLIENT, clientSeed);
        this.objectMapper = objectMapper;
    }

    public ResolvedSigner resolve(String identity, String trustSystem) {
        return resolve(identity, trustSystem, null);
    }

    public ResolvedSigner resolve(String identity, String trustSystem, String signingKeyId) {
        var configuration = snapshot(identity, trustSystem, signingKeyId, null);
        return configuration == null ? null : resolveSnapshot(configuration);
    }

    /** Public configuration pinned to a request; it contains no provider credentials. */
    public String snapshot(String identity, String trustSystem, String signingKeyId) {
        return snapshot(identity, trustSystem, signingKeyId, null);
    }

    /** Resolve identity material for a presentation profile and pin that profile at the call site. */
    public String snapshot(
            String identity, String trustSystem, String signingKeyId, String presentationProfileId) {
        if (identity == null || identity.isBlank()) return null;
        String effectiveTrustSystem = trustSystem == null || trustSystem.isBlank() ? "Default" : trustSystem;
        // EUDI verifier requests use the identity's wallet access certificate, not issuer trust material.
        return platformClient.get().uri(uri -> uri
                        .path("/internal/platform/v1/identities/{identity}/presentation-signing-configuration")
                        .queryParam("trustSystem", effectiveTrustSystem)
                        .queryParamIfPresent("presentationProfileId", Optional.ofNullable(presentationProfileId))
                        .queryParamIfPresent("signingKeyId", Optional.ofNullable(signingKeyId))
                        .build(identity))
                .retrieve().body(String.class);
    }

    public void retainSnapshot(String identity, String configuration, String flowId, java.time.Instant expiry) {
        var keyUri = objectMapper.readTree(configuration).path("keyUri").asString(null);
        if (keyUri == null || keyUri.isBlank()) throw new IllegalArgumentException("Signing snapshot has no key URI");
        platformClient.post().uri("/internal/platform/v1/identities/{identity}/signing-flows", identity)
                .body(Map.of("flowId", flowId, "client", "VERIFIER", "keyUri", keyUri,
                        "purpose", "SIGNING", "expiresAt", expiry.toString()))
                .retrieve().toBodilessEntity();
    }

    public void releaseSnapshot(String flowId) {
        platformClient.delete().uri("/internal/platform/v1/identities/signing-flows/VERIFIER/{flowId}", flowId)
                .retrieve().toBodilessEntity();
    }

    public ResolvedSigner resolveSnapshot(String configuration) {
        if (configuration == null || configuration.isBlank()) {
            throw new IllegalArgumentException("Presentation request has no signing snapshot");
        }
        try {
            var config = objectMapper.readTree(configuration);
            var algorithm = JWSAlgorithm.parse(config.path("algorithm").asString("ES256"));
            var certificateChain = new ArrayList<String>();
            config.path("certificateChain").forEach(certificate ->
                    certificateChain.add(certificate.asString()));
            var publicJwk = withCertificateChain(
                    JWK.parse(config.path("issuerJwk").asString()), certificateChain);
            var keyUri = config.path("keyUri").asString(null);
            if (keyUri == null || keyUri.isBlank()) {
                throw new IllegalArgumentException("Identity signing configuration has no key URI");
            }
            var providerEndpoint = config.path("providerEndpoint").asString("");
            if (providerEndpoint.isBlank()) {
                providerEndpoint = providerBaseUrl;
            }
            if (providerEndpoint.isBlank()) {
                throw new IllegalStateException("Verifier signing provider endpoint is not configured");
            }
            var providerMode = config.path("providerAuthenticationMode").asString("");
            if (providerMode.isBlank()) {
                providerMode = providerAuthenticationMode;
            }
            JWSSigner signer = providerSigner(
                    keyUri, algorithm, providerEndpoint, providerMode);
            return new ResolvedSigner(
                    publicJwk,
                    signer,
                    config.path("issuerClaim").isMissingNode()
                            ? null
                            : config.path("issuerClaim").asString(null),
                    config.path("keyId").asString(publicJwk.getKeyID()));
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Could not resolve verifier identity signer", exception);
        }
    }

    private JWSSigner providerSigner(
            String keyUri,
            JWSAlgorithm algorithm,
            String providerEndpoint,
            String providerMode) {
        // The verifier authenticates with its own registered client key.
        var authentication = clientKeys.isConfigured()
                ? clientKeys.authentication()
                : RemoteSigningKeyProvider.Authentication.from(providerMode, "");
        var provider = new RemoteSigningKeyProvider(
                clientBuilder, objectMapper, URI.create(providerEndpoint), authentication);
        return new ProviderJwsSigner(provider, provider.resolve(keyUri));
    }

    /** The public half of this verifier's credential, per signing service. Never the seed. */
    public ClientKey clientPublicKey(String providerScheme) {
        if (!clientKeys.isConfigured()) return null;
        return new ClientKey(clientKeys.client(), clientKeys.publicKey(providerScheme));
    }

    public record ClientKey(String client, String publicKey) {}

    public record ResolvedSigner(JWK publicJwk, JWSSigner signer, String issuerClaim, String keyId) {
        public ResolvedSigner(JWK publicJwk, JWSSigner signer, String issuerClaim) {
            this(publicJwk, signer, issuerClaim, publicJwk == null ? null : publicJwk.getKeyID());
        }
    }

    static JWK withCertificateChain(JWK publicJwk, List<String> certificateChain)
            throws ParseException {
        if (certificateChain == null || certificateChain.isEmpty()) return publicJwk;
        var jwk = new HashMap<String, Object>(publicJwk.toJSONObject());
        jwk.put("x5c", joseCertificateChain(certificateChain));
        return JWK.parse(jwk);
    }

    private static List<String> joseCertificateChain(List<String> certificateChain) {
        if (certificateChain.size() < 2) return certificateChain;

        try {
            var bytes = Base64.getDecoder().decode(certificateChain.getLast());
            var factory = CertificateFactory.getInstance("X.509");
            var root = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(bytes));
            if (!root.getSubjectX500Principal().equals(root.getIssuerX500Principal())) {
                return certificateChain;
            }
            root.verify(root.getPublicKey());

            return certificateChain.subList(0, certificateChain.size() - 1);
        } catch (Exception exception) {
            return certificateChain;
        }
    }

    public String x509CertificateHash(ResolvedSigner resolvedSigner) {
        return x509CertificateHash(resolvedSigner.publicJwk());
    }

    public static String x509CertificateHash(JWK publicJwk) {
        var certificateChain = publicJwk.getX509CertChain();
        if (certificateChain == null || certificateChain.isEmpty()) {
            throw new IllegalArgumentException("x509_hash requires an X.509 certificate chain");
        }
        try {
            return Base64URL.encode(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(certificateChain.getFirst().decode()))
                    .toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash verifier leaf certificate", exception);
        }
    }

}
