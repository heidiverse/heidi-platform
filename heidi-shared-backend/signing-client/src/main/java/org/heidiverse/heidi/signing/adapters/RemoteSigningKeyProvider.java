// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.adapters;

import org.heidiverse.heidi.shared.signing.ProviderHealth;
import org.heidiverse.heidi.shared.signing.SigningKeyCapabilities;
import org.heidiverse.heidi.shared.signing.SigningKeyCapabilitiesSource;
import org.heidiverse.heidi.shared.signing.SigningKeyCreator;
import org.heidiverse.heidi.shared.signing.SigningKeyDeleter;
import org.heidiverse.heidi.shared.signing.SigningKeyException;
import org.heidiverse.heidi.shared.signing.SigningKeyImporter;
import org.heidiverse.heidi.shared.signing.SigningKeyProvider;
import org.heidiverse.heidi.shared.signing.SigningKeyRef;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;
import org.heidiverse.heidi.shared.signing.SigningOperationProvider;
import org.heidiverse.heidi.shared.signing.SigningOperationRequest;
import org.heidiverse.heidi.shared.signing.SigningOperationResult;
import org.heidiverse.heidi.shared.signing.SigningContentKeyProvider;
import org.heidiverse.heidi.shared.signing.SigningContentKeyRequest;
import org.heidiverse.heidi.shared.signing.SigningGrantWriter;
import org.heidiverse.heidi.shared.signing.SigningProviderException;
import org.heidiverse.heidi.shared.signing.SigningPurpose;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import uniffi.heidi_signing.Heidi_signing_jvmKt;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** HTTP client for the protocol in {@code spec/signing-protocol}. */
public final class RemoteSigningKeyProvider
        implements org.heidiverse.heidi.shared.signing.SigningKeyRevoker,
                org.heidiverse.heidi.shared.signing.SigningCsrProvider, SigningKeyProvider,
                SigningKeyCapabilitiesSource,
                SigningKeyCreator,
                SigningKeyImporter,
                SigningKeyDeleter,
                SigningGrantWriter,
                SigningOperationProvider,
                SigningContentKeyProvider {
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_PENDING = "PENDING";

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final Authentication authentication;
    private volatile Capabilities capabilities;
    private volatile RegisteredSession registeredSession;

    public RemoteSigningKeyProvider(
            RestClient.Builder builder, ObjectMapper objectMapper, URI endpoint, String bearerToken) {
        this(builder, objectMapper, endpoint, Authentication.from(null, bearerToken));
    }

    public RemoteSigningKeyProvider(
            RestClient.Builder builder, URI endpoint, String bearerToken) {
        this(builder, new ObjectMapper(), endpoint, Authentication.from(null, bearerToken));
    }

    public RemoteSigningKeyProvider(
            RestClient.Builder builder,
            URI endpoint,
            Authentication authentication) {
        this(builder, new ObjectMapper(), endpoint, authentication);
    }

    public RemoteSigningKeyProvider(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            URI endpoint,
            Authentication authentication) {
        this.client = builder.clone().baseUrl(endpoint.toString()).build();
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.authentication = authentication == null ? Authentication.none() : authentication;
    }

    @Override
    public String scheme() {
        return capabilities().scheme();
    }

    @Override
    public List<String> supportedAlgorithms() {
        return capabilities().supportedAlgorithms();
    }

    @Override
    public List<String> digestSigningAlgorithms() {
        return capabilities().digestSigningAlgorithms();
    }

    @Override
    public List<String> supportedOperations() {
        return capabilities().supportedOperations();
    }

    public List<String> keylessOperations() {
        return capabilities().keylessOperations();
    }

    @Override
    public List<String> contentKeyAlgorithms() {
        capabilities();
        return capabilities.contentKeyAlgorithms();
    }

    @Override
    public byte[] contentKey(SigningKeyRef ref, SigningContentKeyRequest request) {
        if (request == null || !contentKeyAlgorithms().contains(request.algorithm())) {
            throw new SigningKeyException(
                    "Remote provider does not support content-key algorithm: "
                            + (request == null ? null : request.algorithm()));
        }
        requireScheme(ref.uri());
        var content = new java.util.LinkedHashMap<String, Object>();
        content.put("keyUri", ref.uri());
        content.put("algorithm", request.algorithm());
        content.put("enc", request.contentEncryption());
        if (request.ephemeralPublicJwk() != null && !request.ephemeralPublicJwk().isBlank()) {
            try {
                content.put("epk", objectMapper.readTree(request.ephemeralPublicJwk()));
            } catch (Exception exception) {
                throw new SigningKeyException("Content-key request has an invalid epk", exception);
            }
        }
        if (request.agreementPartyUInfo() != null) {
            content.put("apu", request.agreementPartyUInfo());
        }
        if (request.agreementPartyVInfo() != null) {
            content.put("apv", request.agreementPartyVInfo());
        }
        if (request.encryptedKey() != null) {
            content.put("encryptedKey", request.encryptedKey());
        }
        var response = post("/v1/keys/content-key", content, ref.uri());
        try {
            return Base64.getDecoder().decode(response.path("contentKey").asString());
        } catch (Exception exception) {
            throw new SigningKeyException("Signing backend returned an invalid content key", exception);
        }
    }

    /** How this service accepts clients, or null where it predates clients. */
    public String clientAcceptance() {
        capabilities();
        return capabilities.clientAcceptance();
    }

    /** The clients this service knows, and whether each has completed a registration. */
    public List<ClientStatus> clients() {
        var uri = requestUri("/v1/auth/clients", Map.of());
        try {
            var response = client.get().uri(uri)
                    .headers(headers -> authenticate(headers, "GET", uri, new byte[0], null))
                    .retrieve().body(JsonNode.class);
            var clients = new java.util.ArrayList<ClientStatus>();
            response.forEach(node -> {
                java.time.Instant lastUse = null;
                var value = node.path("lastUse").asText(null);
                if (value != null && !value.isBlank()) {
                    try {
                        lastUse = java.time.Instant.parse(value);
                    } catch (java.time.format.DateTimeParseException ignored) {
                        // Optional status from an older provider; keep the connection check usable.
                    }
                }
                clients.add(new ClientStatus(
                        node.path("name").asString(""),
                        node.path("registered").asBoolean(false),
                        lastUse, node.path("publicKey").asText(null)));
            });
            return List.copyOf(clients);
        } catch (Exception exception) {
            throw failure("list clients", uri, exception);
        }
    }

    /** Platform-assisted acceptance; refused by a service that takes clients from configuration. */
    public void addClient(String name, String base64PublicKey) {
        post("/v1/auth/clients", Map.of("name", name, "publicKey", base64PublicKey), null);
    }

    public void removeClient(String name) {
        var uri = requestUri("/v1/auth/clients", Map.of("name", name));
        try {
            client.delete().uri(uri)
                    .headers(headers -> authenticate(headers, "DELETE", uri, new byte[0], null))
                    .retrieve().toBodilessEntity();
        } catch (Exception exception) {
            throw failure("remove client", uri, exception);
        }
    }

    public record ClientStatus(String name, boolean registered, java.time.Instant lastUse, String publicKey) {
        public ClientStatus(String name, boolean registered) {
            this(name, registered, null, null);
        }
    }

    @Override
    public void replaceGrants(String scope, Map<String, Set<SigningPurpose>> grants) {
        var wire = grants.entrySet().stream()
                .map(entry -> Map.of(
                        "client", entry.getKey(),
                        "purposes", entry.getValue().stream().map(SigningPurpose::wireValue).toList()))
                .toList();
        var uri = requestUri("/v1/keys/grants", Map.of());
        try {
            var body = objectMapper.writeValueAsBytes(
                    Map.of("scope", scope, "grants", wire));
            client.put().uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> authenticate(headers, "PUT", uri, body, null))
                    .body(body)
                    .retrieve().toBodilessEntity();
        } catch (Exception exception) {
            throw failure("write grants", uri, exception);
        }
    }

    @Override
    public Set<String> grantScopes() {
        var response = get("/v1/keys/grants/scopes", Map.of(), null);
        var scopes = new java.util.LinkedHashSet<String>();
        response.forEach(node -> {
            var scope = node.asText("");
            if (!scope.isBlank()) scopes.add(scope);
        });
        return Set.copyOf(scopes);
    }

    @Override
    public java.util.Optional<Map<String, Set<SigningPurpose>>> grants(String scope) {
        var response = get("/v1/keys/grants", Map.of("scope", scope), scope);
        var grants = new java.util.LinkedHashMap<String, Set<SigningPurpose>>();
        response.forEach(node -> grants.put(
                node.path("client").asText(),
                java.util.stream.StreamSupport.stream(node.path("purposes").spliterator(), false)
                        .map(value -> SigningPurpose.of(value.asText()))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet())));
        return java.util.Optional.of(Map.copyOf(grants));
    }

    @Override
    public SigningKeyRef resolve(String keyUri) {
        requireScheme(keyUri);
        return ref(get("/v1/keys", Map.of("uri", keyUri), keyUri));
    }

    @Override
    public SigningKeyRef createKey(String keyId, String algorithm) {
        return createKey(null, keyId, algorithm, Set.of(SigningKeyUsage.SIGN));
    }

    @Override
    public SigningKeyRef createKey(String keyId, String algorithm, Set<SigningKeyUsage> usages) {
        return createKey(null, keyId, algorithm, usages);
    }

    /** Creates a key in an explicit signing-service namespace. */
    public SigningKeyRef createKey(String namespace, String keyId, String algorithm) {
        return createKey(namespace, keyId, algorithm, Set.of(SigningKeyUsage.SIGN));
    }

    public SigningKeyRef createKey(
            String namespace, String keyId, String algorithm, Set<SigningKeyUsage> usages) {
        requireCapability(capabilities().canCreate(), "create keys");
        var request = new java.util.LinkedHashMap<String, Object>();
        request.put("keyId", keyId);
        request.put("algorithm", algorithm);
        request.put("usages", usages);
        if (namespace != null && !namespace.isBlank()) request.put("namespace", namespace);
        return ref(post("/v1/keys", request, null));
    }

    @Override
    public SigningKeyRef importKey(String keyId, String privateJwk, String algorithm) {
        return importKey(null, keyId, privateJwk, algorithm, Set.of(SigningKeyUsage.SIGN));
    }

    @Override
    public SigningKeyRef importKey(
            String keyId, String privateJwk, String algorithm, Set<SigningKeyUsage> usages) {
        return importKey(null, keyId, privateJwk, algorithm, usages);
    }

    /** Imports a key into an explicit signing-service namespace. */
    public SigningKeyRef importKey(
            String namespace, String keyId, String privateJwk, String algorithm) {
        return importKey(namespace, keyId, privateJwk, algorithm, Set.of(SigningKeyUsage.SIGN));
    }

    public SigningKeyRef importKey(
            String namespace,
            String keyId,
            String privateJwk,
            String algorithm,
            Set<SigningKeyUsage> usages) {
        requireCapability(capabilities().canImport(), "import keys");
        JsonNode jwk;
        try {
            jwk = objectMapper.readTree(privateJwk);
        } catch (Exception exception) {
            throw new SigningKeyException("Private JWK is not valid JSON", exception);
        }
        var request = new java.util.LinkedHashMap<String, Object>();
        request.put("keyId", keyId);
        request.put("algorithm", algorithm);
        request.put("privateJwk", jwk);
        request.put("usages", usages);
        if (namespace != null && !namespace.isBlank()) request.put("namespace", namespace);
        return ref(post("/v1/keys/import", request, null));
    }

    @Override
    public void revokeKey(SigningKeyRef ref) {
        requireScheme(ref.uri());
        post("/v1/keys/revoke", Map.of("keyUri", ref.uri()), ref.uri());
    }

    @Override
    public String createCsr(SigningKeyRef ref, org.heidiverse.heidi.shared.signing.SigningCsrRequest request) {
        requireScheme(ref.uri());
        return post("/v1/keys/csr", Map.of("keyUri", ref.uri(), "request", request), ref.uri()).path("pem").asText();
    }

    @Override
    public void deleteKey(SigningKeyRef ref) {
        requireCapability(capabilities().canDelete(), "delete keys");
        requireScheme(ref.uri());
        try {
            var uri = requestUri("/v1/keys", Map.of("uri", ref.uri()));
            client.delete().uri(uri)
                    .headers(headers -> authenticate(headers, "DELETE", uri, new byte[0], ref.uri()))
                    .retrieve().toBodilessEntity();
        } catch (Exception exception) {
            throw failure("delete key", endpoint, exception);
        }
    }

    @Override
    public byte[] sign(SigningKeyRef ref, byte[] message) {
        return signature(ref, Map.of("message", Base64.getEncoder().encodeToString(message)));
    }

    @Override
    public byte[] signDigest(SigningKeyRef ref, byte[] digest, String digestAlgorithm) {
        if (!digestSigningAlgorithms().contains(ref.algorithm())) {
            throw new SigningKeyException("Remote provider cannot sign a digest with " + ref.algorithm());
        }
        return signature(ref, Map.of(
                "digest", Base64.getEncoder().encodeToString(digest),
                "digestAlgorithm", digestAlgorithm));
    }

    @Override
    public SigningOperationResult execute(
            SigningKeyRef ref, SigningOperationRequest request) {
        requireScheme(ref.uri());
        if (!supportedOperations().contains(request.operation())) {
            throw new SigningKeyException(
                    "Remote provider does not support operation: " + request.operation());
        }
        JsonNode input;
        try {
            input = objectMapper.readTree(request.inputJson());
        } catch (Exception exception) {
            throw new SigningKeyException("Operation input is not valid JSON", exception);
        }
        if (input == null || !input.isObject()) {
            throw new SigningKeyException("Operation input must be a JSON object");
        }

        var content = new java.util.LinkedHashMap<String, Object>();
        content.put("operation", request.operation());
        content.put("keyUri", ref.uri());
        content.put("input", input);
        if (request.operationId() != null && !request.operationId().isBlank()) {
            content.put("operationId", request.operationId());
        }

        var response = post("/v1/operations", content, ref.uri());
        return operationResult(response);
    }

    @Override
    public SigningOperationResult executeKeyless(SigningOperationRequest request) {
        if (!keylessOperations().contains(request.operation())) {
            throw new SigningKeyException(
                    "Remote provider does not support keyless operation: " + request.operation());
        }
        JsonNode input;
        try {
            input = objectMapper.readTree(request.inputJson());
        } catch (Exception exception) {
            throw new SigningKeyException("Operation input is not valid JSON", exception);
        }
        if (input == null || !input.isObject()) {
            throw new SigningKeyException("Operation input must be a JSON object");
        }

        var content = new java.util.LinkedHashMap<String, Object>();
        content.put("operation", request.operation());
        content.put("input", input);
        if (request.operationId() != null && !request.operationId().isBlank()) {
            content.put("operationId", request.operationId());
        }

        return operationResult(post("/v1/operations", content, null));
    }

    @Override
    public ProviderHealth health() {
        long started = System.nanoTime();
        try {
            var response = get("/v1/health", Map.of(), null);
            if (!response.path("healthy").asBoolean(false)) {
                return ProviderHealth.down(response.path("error").asString("Signing backend is unhealthy"));
            }
            return ProviderHealth.up((System.nanoTime() - started) / 1_000_000);
        } catch (Exception exception) {
            return ProviderHealth.down(exception.getMessage());
        }
    }

    @Override
    public SigningKeyCapabilities capabilities() {
        var current = capabilities;
        if (current != null) return current.asPublic();
        synchronized (this) {
            if (capabilities == null) {
                var node = capabilitiesResponse();
                var scheme = node.path("scheme").asString("");
                if (scheme.isBlank()) {
                    throw new SigningKeyException("Signing backend returned no key URI scheme");
                }
                capabilities = new Capabilities(
                        scheme,
                        strings(node.path("supportedAlgorithms")),
                        strings(node.path("digestSigningAlgorithms")),
                        strings(node.path("supportedOperations")),
                        strings(node.path("keylessOperations")),
                        node.path("canCreate").asBoolean(false),
                        node.path("canImport").asBoolean(false),
                        node.path("canDelete").asBoolean(false),
                        node.path("clientAcceptance").asString(null),
                        strings(node.path("contentKeyAlgorithms")));
            }
            return capabilities.asPublic();
        }
    }

    private byte[] signature(SigningKeyRef ref, Map<String, String> content) {
        requireScheme(ref.uri());
        var request = new java.util.LinkedHashMap<String, Object>();
        request.put("keyUri", ref.uri());
        request.put("algorithm", ref.algorithm());
        request.putAll(content);
        var response = post("/v1/signatures", request, ref.uri());
        try {
            return Base64.getDecoder().decode(response.path("signature").asString());
        } catch (Exception exception) {
            throw new SigningKeyException("Signing backend returned an invalid signature", exception);
        }
    }

    private SigningKeyRef ref(JsonNode node) {
        var publicKeyDocument = node.path("publicKeyDocument");
        if (!publicKeyDocument.isObject()) {
            throw new SigningKeyException("Signing backend returned no public key document");
        }
        var usages = java.util.EnumSet.noneOf(SigningKeyUsage.class);
        node.path("usages").forEach(item -> {
            try {
                usages.add(SigningKeyUsage.valueOf(item.asText()));
            } catch (IllegalArgumentException exception) {
                throw new SigningKeyException("Signing backend returned invalid key usages", exception);
            }
        });
        if (usages.isEmpty()) usages.add(SigningKeyUsage.SIGN);
        return new SigningKeyRef(
                node.path("uri").asString(), publicKeyDocument.toString(),
                node.path("algorithm").asString(), usages);
    }

    private JsonNode get(String path, Map<String, String> query, String keyId) {
        var uri = requestUri(path, query);
        try {
            return client.get().uri(uri)
                    .headers(headers -> authenticate(headers, "GET", uri, new byte[0], keyId))
                    .retrieve().body(JsonNode.class);
        } catch (Exception exception) {
            throw failure("call " + path, uri, exception);
        }
    }

    /** Capabilities are public discovery and provide the scheme used to derive the client key. */
    private JsonNode capabilitiesResponse() {
        var uri = requestUri("/v1/capabilities", Map.of());
        try {
            var request = client.get().uri(uri);
            if (!authentication.isRegistered()) {
                request.headers(headers -> authenticate(headers, "GET", uri, new byte[0], null));
            }
            return request.retrieve().body(JsonNode.class);
        } catch (Exception exception) {
            throw failure("call /v1/capabilities", uri, exception);
        }
    }

    private JsonNode post(String path, Object content, String keyId) {
        var uri = requestUri(path, Map.of());
        try {
            var body = objectMapper.writeValueAsBytes(content);
            return client.post().uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> authenticate(headers, "POST", uri, body, keyId))
                    .body(body)
                    .retrieve().body(JsonNode.class);
        } catch (Exception exception) {
            throw failure("call " + path, uri, exception);
        }
    }

    private void authenticate(
            HttpHeaders headers, String method, URI uri, byte[] body, String keyId) {
        switch (authentication.mode()) {
            case "none" -> { }
            case "bearer" -> headers.setBearerAuth(authentication.bearerToken());
            case "mtls" -> { /* the HTTP client supplies the client certificate */ }
            case "registered" -> {
                var session = registeredSession();
                var providerScheme = capabilities().scheme();
                var timestamp = Long.toString(System.currentTimeMillis());
                var canonical = canonicalRequest(method, uri, body);
                var requestHash = Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(sha256(canonical));
                var purpose = purpose(method, uri);
                var proofPayload = (timestamp + ":" + purpose + ":" + requestHash)
                        .getBytes(StandardCharsets.UTF_8);
                try {
                    var signature = Heidi_signing_jvmKt.authenticate(
                            session.seed(), null, session.psk(), providerScheme, keyId, proofPayload);
                    headers.set("Signing-Authentication", session.registrationId());
                    headers.set("Signing-Authentication-Timestamp", timestamp);
                    headers.set("Signing-Authentication-Purpose", purpose);
                    headers.set("Signing-Authentication-Signature", Base64.getEncoder().encodeToString(signature));
                    if (keyId != null && !keyId.isBlank()) {
                        headers.set("Signing-Authentication-Key-Id", keyId);
                    }
                } catch (Exception exception) {
                    throw new SigningKeyException("Could not create registered signing authentication", exception);
                }
            }
            default -> throw new SigningKeyException(
                    "Unsupported signing authentication mode: " + authentication.mode());
        }
    }

    private RegisteredSession registeredSession() {
        var current = registeredSession;
        if (current != null) return current;
        synchronized (this) {
            if (registeredSession != null) return registeredSession;
            try {
                var providerScheme = capabilities().scheme();
                // One seed per service, a key per signing service: the derivation binds the
                // provider scheme, so the same seed shows a different public key to each of them.
                var seed = authentication.seed() == null
                        ? Heidi_signing_jvmKt.generateSeed()
                        : authentication.seed();
                var publicKey = Heidi_signing_jvmKt.publicKey(seed, null, providerScheme);
                var openBody = objectMapper.writeValueAsBytes(Map.of(
                        "provider", providerScheme,
                        "client", authentication.client(),
                        "publicKey", publicKey));
                var openUri = requestUri("/v1/auth/registrations", Map.of());
                var open = client.post().uri(openUri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(openBody)
                        .retrieve().body(JsonNode.class);
                var registrationId = open.path("registrationId").asString("");
                var psk = decodeBase64(open.path("psk").asString(""), "registration PSK");
                if (registrationId.isBlank() || psk.length == 0) {
                    throw new SigningKeyException("Signing backend returned an invalid registration");
                }

                var registerBody = objectMapper.writeValueAsBytes(Map.of(
                        "publicKey", publicKey,
                        "signature", Heidi_signing_jvmKt.authenticate(
                                seed, null, psk, providerScheme, null, publicKey)));
                var registerUri = requestUri(
                        "/v1/auth/registrations/" + registrationId + "/key", Map.of());
                client.post().uri(registerUri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(registerBody)
                        .retrieve().toBodilessEntity();
                registeredSession = new RegisteredSession(registrationId, seed, psk);
                return registeredSession;
            } catch (SigningKeyException exception) {
                throw exception;
            } catch (Exception exception) {
                throw failure("open registered signing authentication", exception);
            }
        }
    }

    private URI requestUri(String path, Map<String, String> query) {
        var builder = UriComponentsBuilder.fromUri(endpoint).path(path);
        query.forEach(builder::queryParam);
        return builder.build().encode().toUri();
    }

    private String purpose(String method, URI uri) {
        var required = SigningPurpose.required(method, uri.getRawPath());
        return required == null ? SigningPurpose.READ.wireValue() : required.wireValue();
    }

    /** Canonical request bytes signed by the registered-key profile. */
    public static byte[] canonicalRequest(String method, URI uri, byte[] body) {
        var target = uri.getRawPath();
        if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
            target += "?" + uri.getRawQuery();
        }
        var prefix = (method + "\n" + target + "\n").getBytes(StandardCharsets.UTF_8);
        var canonical = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, canonical, 0, prefix.length);
        System.arraycopy(body, 0, canonical, prefix.length, body.length);
        return canonical;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JRE has no SHA-256", exception);
        }
    }

    private static byte[] decodeBase64(String value, String description) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new SigningKeyException("Signing backend returned invalid " + description, exception);
        }
    }

    private void requireCapability(boolean supported, String operation) {
        if (!supported) {
            throw new SigningKeyException(
                    "Remote provider '" + scheme() + "' does not support " + operation);
        }
    }

    private void requireScheme(String keyUri) {
        if (keyUri == null || !keyUri.startsWith(scheme() + "://")) {
            throw new SigningKeyException("Remote provider cannot handle key URI: " + keyUri);
        }
    }

    private SigningProviderException failure(String operation, URI uri, Exception exception) {
        return new SigningProviderException(
                "Could not " + operation + " at " + uri + ": " + reason(exception), exception);
    }

    private SigningProviderException failure(String operation, Exception exception) {
        return failure(operation, endpoint, exception);
    }

    private static String reason(Exception exception) {
        if (exception instanceof RestClientResponseException response) {
            return "signing provider returned HTTP " + response.getStatusCode().value()
                    + " " + response.getStatusText();
        }

        var cause = rootCause(exception);
        if (exception instanceof ResourceAccessException) {
            return "signing provider is unreachable: " + message(cause);
        }
        return "signing provider request failed: " + message(cause);
    }

    private static Throwable rootCause(Throwable exception) {
        var cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String message(Throwable exception) {
        var message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName() : message;
    }

    private SigningOperationResult operationResult(JsonNode response) {
        var status = response.path("status").asString("");
        if (status.isBlank()) {
            throw new SigningKeyException("Signing backend returned no operation status");
        }
        if (!STATUS_COMPLETED.equals(status) && !STATUS_PENDING.equals(status)) {
            throw new SigningKeyException(
                    "Signing backend returned an unknown operation status: " + status);
        }
        var result = response.get("result");
        var interaction = response.get("interaction");
        var operationId = response.path("operationId").asString(null);
        if (STATUS_COMPLETED.equals(status) && (result == null || result.isNull())) {
            throw new SigningKeyException("Signing backend returned no operation result");
        }
        if (STATUS_PENDING.equals(status)
                && (operationId == null || operationId.isBlank()
                        || interaction == null || interaction.isNull())) {
            throw new SigningKeyException(
                    "Signing backend returned an incomplete pending operation");
        }
        return new SigningOperationResult(
                status,
                result == null || result.isNull() ? null : result.toString(),
                operationId,
                interaction == null || interaction.isNull() ? null : interaction.toString());
    }

    private static List<String> strings(JsonNode array) {
        var values = new java.util.ArrayList<String>();
        array.forEach(node -> values.add(node.asString()));
        return List.copyOf(values);
    }

    /**
     * How this caller authenticates. A client name and a seed are the form this decision settles
     * on: the service knows the caller by name, the seed never leaves this process, and one seed
     * serves every signing service because the key is derived per provider scheme.
     */
    public record Authentication(
            String mode,
            String bearerToken,
            String client,
            byte[] seed) {
        public Authentication {
            mode = mode == null || mode.isBlank()
                    ? (bearerToken == null || bearerToken.isBlank() ? "none" : "bearer")
                    : mode.trim().toLowerCase(Locale.ROOT);
            bearerToken = bearerToken == null ? "" : bearerToken;
            client = client == null || client.isBlank() ? null : client.trim();
            seed = seed == null ? null : seed.clone();
            if (!List.of("none", "bearer", "mtls", "registered").contains(mode)) {
                throw new IllegalArgumentException("Unsupported signing authentication mode: " + mode);
            }
            if ("bearer".equals(mode) && bearerToken.isBlank()) {
                throw new IllegalArgumentException("Bearer signing authentication requires a token");
            }
            if ("registered".equals(mode) && (client == null || seed == null)) {
                throw new IllegalArgumentException(
                        "Registered signing authentication requires a client and seed");
            }
        }

        @Override
        public byte[] seed() {
            return seed == null ? null : seed.clone();
        }

        public static Authentication from(String mode, String bearerToken) {
            return new Authentication(mode, bearerToken, null, null);
        }

        /** A named client authenticating with its own seed; no shared credential involved. */
        public static Authentication forClient(String client, byte[] seed) {
            return new Authentication("registered", "", client, seed);
        }

        public static Authentication none() {
            return new Authentication("none", "", null, null);
        }

        public boolean isRegistered() {
            return "registered".equals(mode);
        }
    }

    private record RegisteredSession(String registrationId, byte[] seed, byte[] psk) {
        private RegisteredSession {
            seed = seed.clone();
            psk = psk.clone();
        }

        @Override
        public byte[] seed() {
            return seed.clone();
        }

        @Override
        public byte[] psk() {
            return psk.clone();
        }
    }

    private record Capabilities(
            String scheme,
            List<String> supportedAlgorithms,
            List<String> digestSigningAlgorithms,
            List<String> supportedOperations,
            List<String> keylessOperations,
            boolean canCreate,
            boolean canImport,
            boolean canDelete,
            String clientAcceptance,
            List<String> contentKeyAlgorithms) {
        private SigningKeyCapabilities asPublic() {
            return new SigningKeyCapabilities(
                    scheme, canCreate, canImport, canDelete,
                    supportedAlgorithms, digestSigningAlgorithms, supportedOperations,
                    keylessOperations, contentKeyAlgorithms);
        }
    }
}
