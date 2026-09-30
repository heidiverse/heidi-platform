// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import static org.heidiverse.heidi.signing.server.SigningProtocolModels.*;

import java.net.URI;
import java.util.List;

import org.heidiverse.heidi.shared.signing.SigningKeyCapabilities;
import org.heidiverse.heidi.shared.signing.SigningKeyCreator;
import org.heidiverse.heidi.shared.signing.SigningKeyDeleter;
import org.heidiverse.heidi.shared.signing.SigningKeyException;
import org.heidiverse.heidi.shared.signing.SigningPurpose;
import org.heidiverse.heidi.shared.signing.SigningKeyImporter;
import org.heidiverse.heidi.shared.signing.SigningKeyProvider;
import org.heidiverse.heidi.shared.signing.SigningKeyRef;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;
import org.heidiverse.heidi.shared.signing.SigningOperationProvider;
import org.heidiverse.heidi.shared.signing.SigningOperationRequest;
import org.heidiverse.heidi.shared.signing.SigningOperationResult;
import org.heidiverse.heidi.shared.signing.SigningContentKeyProvider;
import org.heidiverse.heidi.shared.signing.SigningContentKeyRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.beans.factory.annotation.Value;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** HTTP implementation of the provider contract. */
@RestController
@RequestMapping("/v1")
public class SigningProtocolController {
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_PENDING = "PENDING";

    private static final Logger LOG = LoggerFactory.getLogger(SigningProtocolController.class);

    private final SigningKeyProvider provider;
    private final ObjectMapper objectMapper;
    private final RegisteredKeyAuthenticationService registeredKeyAuthentication;
    private final SigningClients clients;
    private final SigningGrants grants;

    @Value("${heidi.signing.auth.fail-open:false}")
    private boolean failOpen;

    public SigningProtocolController(
            SigningKeyProvider provider,
            ObjectMapper objectMapper,
            RegisteredKeyAuthenticationService registeredKeyAuthentication,
            SigningClients clients,
            SigningGrants grants) {
        this.provider = provider;
        this.objectMapper = objectMapper;
        this.registeredKeyAuthentication = registeredKeyAuthentication;
        this.clients = clients;
        this.grants = grants;
    }

    @GetMapping("/capabilities")
    public CapabilitiesResponse capabilities() {
        var capabilities = SigningKeyCapabilities.of(provider);
        return new CapabilitiesResponse(
                capabilities.scheme(),
                capabilities.supportedAlgorithms(),
                capabilities.digestSigningAlgorithms(),
                capabilities.supportedOperations(),
                capabilities.keylessOperations(),
                capabilities.canCreate(),
                capabilities.canImport(),
                capabilities.canDelete(),
                clients.acceptance().wireValue(),
                provider instanceof SigningContentKeyProvider content
                        ? content.contentKeyAlgorithms() : List.of());
    }

    @GetMapping("/health")
    public HealthResponse health() {
        var health = provider.health();
        return new HealthResponse(health.healthy(), health.latencyMillis(), health.error());
    }

    @PostMapping("/auth/registrations")
    public OpenRegistrationResponse openRegistration(
            HttpServletRequest httpRequest, @RequestBody OpenRegistrationRequest request) {
        var authenticatedClient = SigningClientContext.of(httpRequest);
        if (authenticatedClient != null
                && request.client() != null
                && !authenticatedClient.equals(request.client())) {
            throw new SigningAuthorizationException(
                    "Registration client does not match transport authentication");
        }
        var client = authenticatedClient == null ? request.client() : authenticatedClient;
        var registration = registeredKeyAuthentication.open(
                request.provider(), client, request.publicKey());
        return new OpenRegistrationResponse(registration.id(), registration.psk());
    }

    @GetMapping("/auth/clients")
    public List<ClientResponse> listClients() {
        return clients.names().stream()
                .map(name -> new ClientResponse(
                        name,
                        registeredKeyAuthentication.isRegistered(name),
                        registeredKeyAuthentication.lastUse(name), clients.publicKey(name)))
                .toList();
    }

    /** Platform-assisted acceptance; refused where the service takes its clients from config. */
    @PostMapping("/auth/clients")
    public ResponseEntity<Void> addClient(
            HttpServletRequest request, @RequestBody AddClientRequest client) {
        authorizeClientManagement(request);
        clients.add(client.name(), client.publicKey(), SigningClientContext.of(request));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/auth/clients")
    public ResponseEntity<Void> removeClient(
            HttpServletRequest request, @RequestParam("name") String client) {
        authorizeClientManagement(request);
        clients.remove(client, SigningClientContext.of(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/registrations/{registrationId}/key")
    public ResponseEntity<Void> registerPublicKey(
            @org.springframework.web.bind.annotation.PathVariable String registrationId,
            @RequestBody RegisterPublicKeyRequest request) {
        registeredKeyAuthentication.registerPublicKey(
                registrationId, request.publicKey(), request.signature());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/keys")
    public KeyRefResponse resolve(HttpServletRequest request, @RequestParam("uri") String uri) {
        var client = SigningClientContext.of(request);
        if (client == null) {
            if (!failOpen) {
                throw new SigningAuthorizationException(
                        "Signing authentication did not identify a client");
            }
        } else {
            grants.requireAny(client, uri);
        }
        return response(resolveKey(uri));
    }

    @GetMapping("/keys/grants")
    public List<GrantRequest> listGrants(
            HttpServletRequest request,
            @RequestParam("scope") String scope) {
        requireText(scope, "scope");
        var client = SigningClientContext.of(request);
        if (client == null) {
            if (!failOpen) {
                throw new SigningAuthorizationException(
                        "Signing authentication did not identify a client");
            }
        } else {
            grants.requireAny(client, scope);
        }
        return grants.find(scope).stream()
                .map(grant -> new GrantRequest(
                        grant.client(),
                        grant.purposes().stream().map(SigningPurpose::wireValue)
                                .collect(java.util.stream.Collectors.toSet())))
                .toList();
    }

    @GetMapping("/keys/grants/scopes")
    public List<String> listGrantScopes(HttpServletRequest request) {
        requireAdministrator(request, "list grant scopes");
        return grants.scopes().stream().sorted().toList();
    }

    /** The platform writes the complete list whenever a signer is bound to an identity. */
    @PutMapping("/keys/grants")
    public ResponseEntity<Void> replaceGrants(
        HttpServletRequest request, @RequestBody GrantsRequest body) {
        requireText(body.scope(), "scope");
        // The first policy on a scope decides who may use it, so it belongs to the administrator;
        // afterwards the key-management grant governs, which is how the platform keeps writing.
        if (grants.hasPolicy(body.scope())) {
            authorize(request, body.scope(), SigningPurpose.KEY_MANAGEMENT);
        } else {
            requireAdministrator(request, "write the first grants on a scope");
        }
        grants.replace(body.scope(), body.grants().stream()
                .map(grant -> new SigningGrants.Grant(
                        grant.client(),
                        grant.purposes().stream().map(SigningPurpose::of)
                                .collect(java.util.stream.Collectors.toSet())))
                .toList());
        audit(request, body.scope(), SigningPurpose.KEY_MANAGEMENT);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/keys")
    public ResponseEntity<KeyRefResponse> create(
            HttpServletRequest httpRequest, @RequestBody CreateKeyRequest request) {
        requireText(request.keyId(), "keyId");
        requireSupported(request.algorithm());
        var effectiveKeyId = effectiveKeyId(request.namespace(), request.keyId());
        requireAdministrator(httpRequest, "create keys");
        requireCapability(SigningKeyCapabilities.of(provider).canCreate(), "create keys");
        if (!(provider instanceof SigningKeyCreator creator)) {
            throw new SigningProtocolException(
                    HttpStatus.NOT_IMPLEMENTED,
                    "Provider '" + provider.scheme() + "' cannot create keys");
        }
        var usages = requestedUsages(request.usages());
        var created = creator.createKey(effectiveKeyId, request.algorithm(), usages);
        requireUsages(created, usages);
        var creatorName = SigningClientContext.of(httpRequest);
        grants.initialise(created.uri(), creatorName);
        grants.initialise(SigningGrants.scope(created.uri()), SigningClientContext.of(httpRequest));
        audit(httpRequest, created.uri(), SigningPurpose.KEY_MANAGEMENT);
        return ResponseEntity.status(HttpStatus.CREATED).body(response(created));
    }

    @PostMapping("/keys/import")
    public ResponseEntity<KeyRefResponse> importKey(
            HttpServletRequest httpRequest, @RequestBody ImportKeyRequest request) {
        requireText(request.keyId(), "keyId");
        requireSupported(request.algorithm());
        var effectiveKeyId = effectiveKeyId(request.namespace(), request.keyId());
        requireAdministrator(httpRequest, "import keys");
        if (request.privateJwk() == null || !request.privateJwk().isObject()) {
            throw new SigningProtocolException(
                    HttpStatus.UNPROCESSABLE_CONTENT, "privateJwk must be a JSON object");
        }
        requireCapability(SigningKeyCapabilities.of(provider).canImport(), "import keys");
        if (!(provider instanceof SigningKeyImporter importer)) {
            throw new SigningProtocolException(
                    HttpStatus.NOT_IMPLEMENTED,
                    "Provider '" + provider.scheme() + "' cannot import keys");
        }
        var usages = requestedUsages(request.usages());
        var imported = importer.importKey(
                effectiveKeyId, request.privateJwk().toString(), request.algorithm(), usages);
        requireUsages(imported, usages);
        var creatorName = SigningClientContext.of(httpRequest);
        grants.initialise(imported.uri(), creatorName);
        grants.initialise(SigningGrants.scope(imported.uri()), SigningClientContext.of(httpRequest));
        audit(httpRequest, imported.uri(), SigningPurpose.KEY_MANAGEMENT);
        return ResponseEntity.status(HttpStatus.CREATED).body(response(imported));
    }

    @DeleteMapping("/keys")
    public ResponseEntity<Void> delete(
            HttpServletRequest httpRequest, @RequestParam("uri") String uri) {
        requireAdministrator(httpRequest, "delete keys");
        authorize(httpRequest, uri, SigningPurpose.KEY_MANAGEMENT);
        requireCapability(SigningKeyCapabilities.of(provider).canDelete(), "delete keys");
        if (!(provider instanceof SigningKeyDeleter deleter)) {
            throw new SigningProtocolException(
                    HttpStatus.NOT_IMPLEMENTED,
                    "Provider '" + provider.scheme() + "' cannot delete keys");
        }
        deleter.deleteKey(resolveKey(uri));
        audit(httpRequest, uri, SigningPurpose.KEY_MANAGEMENT);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/keys/revoke")
    public ResponseEntity<Void> revoke(HttpServletRequest httpRequest, @RequestBody RevokeKeyRequest request) {
        requireText(request.keyUri(), "keyUri");
        requireAdministrator(httpRequest, "revoke keys");
        authorize(httpRequest, request.keyUri(), SigningPurpose.KEY_MANAGEMENT);
        resolveKey(request.keyUri());
        grants.revoke(request.keyUri());
        audit(httpRequest, request.keyUri(), SigningPurpose.KEY_MANAGEMENT);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/keys/csr")
    public CsrResponse createCsr(HttpServletRequest httpRequest, @RequestBody CsrRequest request) {
        requireText(request.keyUri(), "keyUri");
        requireAdministrator(httpRequest, "request certificates");
        authorize(httpRequest, request.keyUri(), SigningPurpose.KEY_MANAGEMENT);
        grants.requireNotRevoked(request.keyUri(), SigningPurpose.SIGNING);
        var pem = org.heidiverse.heidi.signing.adapters.SigningCsr.create(provider, resolveKey(request.keyUri()), request.request());
        audit(httpRequest, request.keyUri(), SigningPurpose.KEY_MANAGEMENT);
        return new CsrResponse(pem);
    }

    @PostMapping("/signatures")
    public SignResponse sign(HttpServletRequest httpRequest, @RequestBody SignRequest request) {
        requireText(request.keyUri(), "keyUri");
        authorize(httpRequest, request.keyUri(), SigningPurpose.SIGNING);
        requireSupported(request.algorithm());
        var ref = resolveKey(request.keyUri());
        if (!request.algorithm().equals(ref.algorithm())) {
            throw new SigningProtocolException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Requested algorithm does not match the key's algorithm");
        }
        boolean hasMessage = request.message() != null;
        boolean hasDigest = request.digest() != null;
        if (hasMessage == hasDigest) {
            throw new SigningProtocolException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Supply exactly one of message or digest");
        }

        byte[] signature;
        if (hasMessage) {
            signature = provider.sign(ref, request.message());
        } else {
            requireText(request.digestAlgorithm(), "digestAlgorithm");
            if (!provider.digestSigningAlgorithms().contains(request.algorithm())) {
                throw new SigningProtocolException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "Provider cannot sign a pre-computed digest with " + request.algorithm());
            }
            signature = provider.signDigest(ref, request.digest(), request.digestAlgorithm());
        }
        if (signature == null) {
            throw new SigningProtocolException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Provider returned no signature");
        }
        audit(httpRequest, request.keyUri(), SigningPurpose.SIGNING);
        return new SignResponse(signature, request.algorithm());
    }

    @PostMapping("/keys/content-key")
    public ContentKeyResponse contentKey(
            HttpServletRequest httpRequest, @RequestBody ContentKeyRequest request) {
        requireText(request.keyUri(), "keyUri");
        requireText(request.algorithm(), "algorithm");
        requireText(request.enc(), "enc");
        authorize(httpRequest, request.keyUri(), SigningPurpose.DECRYPT);
        if (request.epk() != null && !request.epk().isObject()) {
            throw new SigningProtocolException(
                    HttpStatus.UNPROCESSABLE_CONTENT, "epk must be a JSON object");
        }
        if (isEcdh(request.algorithm()) && request.epk() == null) {
            throw new SigningProtocolException(
                    HttpStatus.UNPROCESSABLE_CONTENT, "ECDH content-key operation requires epk");
        }
        if (!(provider instanceof SigningContentKeyProvider content)) {
            throw new SigningProtocolException(
                    HttpStatus.NOT_IMPLEMENTED,
                    "Provider '" + provider.scheme() + "' cannot derive content keys");
        }
        if (!content.contentKeyAlgorithms().contains(request.algorithm())) {
            throw new SigningProtocolException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Provider cannot derive content keys with " + request.algorithm());
        }
        var derived = content.contentKey(
                resolveKey(request.keyUri()),
                new SigningContentKeyRequest(
                        request.algorithm(), request.enc(),
                        request.epk() == null ? null : request.epk().toString(),
                        request.apu(), request.apv(), request.encryptedKey()));
        if (derived == null || derived.length == 0) {
            throw new SigningProtocolException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Provider returned no content key");
        }
        audit(httpRequest, request.keyUri(), SigningPurpose.DECRYPT);
        return new ContentKeyResponse(derived);
    }

    @PostMapping("/operations")
    public ResponseEntity<OperationResponse> execute(
            HttpServletRequest httpRequest, @RequestBody OperationRequest request) {
        requireText(request.operation(), "operation");
        if (request.input() == null || !request.input().isObject()) {
            throw new SigningProtocolException(
                    HttpStatus.UNPROCESSABLE_CONTENT, "input must be a JSON object");
        }
        var capabilities = SigningKeyCapabilities.of(provider);
        if (!capabilities.supportedOperations().contains(request.operation())) {
            throw new SigningProtocolException(
                    HttpStatus.NOT_IMPLEMENTED,
                    "Provider cannot execute operation: " + request.operation());
        }
        if (!(provider instanceof SigningOperationProvider operations)) {
            throw new SigningProtocolException(
                    HttpStatus.NOT_IMPLEMENTED, "Provider cannot execute operations");
        }
        var operationRequest = new SigningOperationRequest(
                request.operation(), request.operationId(), request.input().toString());
        var keyless = capabilities.keylessOperations().contains(request.operation());
        if (keyless) {
            authorize(httpRequest, "op/" + request.operation(), SigningPurpose.OPERATIONS);
        } else {
            requireText(request.keyUri(), "keyUri");
            authorize(httpRequest, request.keyUri(), SigningPurpose.OPERATIONS);
        }
        var result = keyless
                ? executeKeyless(request, operations, operationRequest)
                : operations.execute(resolveKey(request.keyUri()), operationRequest);
        if (result == null || result.status() == null || result.status().isBlank()) {
            throw new SigningProtocolException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Provider returned no operation status");
        }
        if (!STATUS_COMPLETED.equals(result.status()) && !STATUS_PENDING.equals(result.status())) {
            throw new SigningProtocolException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Provider returned an unknown operation status: " + result.status());
        }
        if (STATUS_COMPLETED.equals(result.status()) && result.resultJson() == null) {
            throw new SigningProtocolException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Provider returned no operation result");
        }
        if (STATUS_PENDING.equals(result.status())
                && (result.operationId() == null || result.operationId().isBlank()
                        || result.interactionJson() == null)) {
            throw new SigningProtocolException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Provider returned an incomplete pending operation");
        }
        var response = new OperationResponse(
                request.operation(),
                result.status(),
                json(result.resultJson(), "operation result"),
                result.operationId(),
                json(result.interactionJson(), "operation interaction"));
        audit(httpRequest,
                keyless
                        ? "op/" + request.operation() : request.keyUri(),
                SigningPurpose.OPERATIONS);
        var status = STATUS_PENDING.equals(result.status())
                ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    private SigningOperationResult executeKeyless(
            OperationRequest request,
            SigningOperationProvider operations,
            SigningOperationRequest operationRequest) {
        if (request.keyUri() != null && !request.keyUri().isBlank()) {
            throw new SigningProtocolException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "keyUri must be omitted for a keyless operation");
        }
        return operations.executeKeyless(operationRequest);
    }

    private JsonNode json(String value, String description) {
        if (value == null) return null;
        try {
            var parsed = objectMapper.readTree(value);
            if (parsed == null || !parsed.isObject()) {
                throw new IllegalArgumentException("JSON object expected");
            }
            return parsed;
        } catch (Exception exception) {
            throw new SigningProtocolException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Provider returned invalid " + description);
        }
    }

    /**
     * Refuses unless the authenticated client holds the purpose on the key's scope. Modes
     * without a caller identity (none, bearer, mTLS) carry no client, and are left as they are.
     */
    private void authorize(HttpServletRequest request, String keyUri, SigningPurpose purpose) {
        grants.requireNotRevoked(keyUri, purpose);
        var client = SigningClientContext.of(request);
        if (client == null) {
            if (failOpen) return;
            throw new SigningAuthorizationException(
                    "Signing authentication did not identify a client");
        }
        grants.require(client, keyUri, purpose);
    }

    /** Only a client this service already accepts may introduce another one. */
    private void authorizeClientManagement(HttpServletRequest request) {
        if (SigningClientContext.of(request) != null) return;
        if (failOpen) return;
        throw new SigningAuthorizationException("Only a known client may add another");
    }

    /** Key lifecycle belongs to the administrative client; modes without a caller are unaffected. */
    private void requireAdministrator(HttpServletRequest request, String action) {
        var client = SigningClientContext.of(request);
        if (client == null) {
            if (failOpen) return;
            throw new SigningAuthorizationException(
                    "Signing authentication did not identify a client");
        }
        clients.requireAdministrator(client, action);
    }

    private void audit(HttpServletRequest request, String scope, SigningPurpose purpose) {
        LOG.info("signing client={} purpose={} scope={}",
                SigningClientContext.of(request), purpose.wireValue(), scope);
    }

    private SigningKeyRef resolveKey(String uri) {
        requireText(uri, "uri");
        try {
            var parsed = URI.create(uri);
            if (!provider.scheme().equals(parsed.getScheme())) {
                throw new SigningProtocolException(
                        HttpStatus.NOT_FOUND, "Key URI does not belong to this provider");
            }
            return provider.resolve(uri);
        } catch (IllegalArgumentException exception) {
            throw new SigningProtocolException(HttpStatus.NOT_FOUND, "Unknown signing key");
        } catch (SigningKeyException exception) {
            throw new SigningProtocolException(HttpStatus.NOT_FOUND, "Unknown signing key");
        }
    }

    private void requireSupported(String algorithm) {
        requireText(algorithm, "algorithm");
        if (!provider.supportedAlgorithms().contains(algorithm)) {
            throw new SigningProtocolException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Unsupported signing algorithm: " + algorithm);
        }
    }

    private static boolean isEcdh(String algorithm) {
        return algorithm != null && algorithm.startsWith("ECDH-ES");
    }

    private static void requireCapability(boolean supported, String operation) {
        if (!supported) {
            throw new SigningProtocolException(
                    HttpStatus.NOT_IMPLEMENTED, "Provider cannot " + operation);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new SigningProtocolException(
                    HttpStatus.UNPROCESSABLE_CONTENT, name + " must not be blank");
        }
    }

    private String effectiveKeyId(String namespace, String keyId) {
        if (namespace == null || namespace.isBlank()) return keyId;
        if (!namespace.matches("kc/[0-9a-fA-F-]{36}")
                || !keyId.matches("[0-9a-fA-F-]{36}")) {
            throw new SigningProtocolException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "namespace and keyId must be UUID-based logical-key identifiers");
        }
        return namespace + "/" + keyId;
    }

    private KeyRefResponse response(SigningKeyRef ref) {
        if (!provider.scheme().equals(ref.scheme())) {
            throw new SigningProtocolException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Provider returned a key reference for another scheme");
        }
        JsonNode publicKeyDocument;
        try {
            publicKeyDocument = objectMapper.readTree(ref.publicJwk());
        } catch (Exception exception) {
            throw new SigningProtocolException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Provider returned an invalid public key document");
        }
        if (publicKeyDocument == null || !publicKeyDocument.isObject()) {
            throw new SigningProtocolException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Provider returned an invalid public key document");
        }
        return new KeyRefResponse(ref.uri(), publicKeyDocument, ref.algorithm(), ref.usages());
    }

    private static java.util.Set<SigningKeyUsage> requestedUsages(
            java.util.Set<SigningKeyUsage> usages) {
        if (usages == null || usages.isEmpty()) return java.util.Set.of(SigningKeyUsage.SIGN);
        return java.util.Set.copyOf(usages);
    }

    private static void requireUsages(SigningKeyRef ref, java.util.Set<SigningKeyUsage> requested) {
        if (!requested.equals(ref.usages())) {
            throw new SigningProtocolException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Provider returned key usages different from the requested usages");
        }
    }

    @RestControllerAdvice(assignableTypes = SigningProtocolController.class)
    static class ErrorHandler {
        @ExceptionHandler(SigningProtocolException.class)
        ResponseEntity<ProblemResponse> protocol(SigningProtocolException exception) {
            return problem(exception.status(), exception.getMessage());
        }

        @ExceptionHandler(SigningAuthorizationException.class)
        ResponseEntity<ProblemResponse> forbidden(SigningAuthorizationException exception) {
            return problem(HttpStatus.FORBIDDEN, exception.getMessage());
        }

        @ExceptionHandler(SigningKeyException.class)
        ResponseEntity<ProblemResponse> provider(
                SigningKeyException exception, HttpServletRequest request) {
            LOG.error(
                    "Signing provider operation failed client={}: {} {}: {}",
                    SigningClientContext.of(request),
                    request.getMethod(),
                    request.getRequestURI(),
                    exception.getMessage(),
                    exception);
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "Signing provider refused the operation");
        }

        @ExceptionHandler(Exception.class)
        ResponseEntity<ProblemResponse> unexpected(
                Exception exception, HttpServletRequest request) {
            LOG.error(
                    "Unexpected signing protocol failure client={}: {} {}: {}",
                    SigningClientContext.of(request),
                    request.getMethod(),
                    request.getRequestURI(),
                    exception.getMessage(),
                    exception);
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "Signing provider failed");
        }

        private static ResponseEntity<ProblemResponse> problem(HttpStatus status, String detail) {
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(new ProblemResponse(
                            "https://heidi.heidiverse.org/problems/signing",
                            status.getReasonPhrase(),
                            status.value(),
                            detail));
        }
    }
}
