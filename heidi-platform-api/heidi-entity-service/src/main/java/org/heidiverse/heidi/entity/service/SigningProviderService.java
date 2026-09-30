// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import org.heidiverse.heidi.coordinator.service.utils.CryptoUtils;
import org.heidiverse.heidi.entity.data.repository.SigningProviderRepository;
import org.heidiverse.heidi.entity.model.entity.SigningProviderEntity;
import org.heidiverse.heidi.entity.model.signing.SigningProviderConfiguration;
import org.heidiverse.heidi.entity.model.signing.SigningProviderConnectionResponse;
import org.heidiverse.heidi.entity.model.signing.SigningProviderRequest;
import org.heidiverse.heidi.entity.model.signing.SigningProviderResponse;
import org.heidiverse.heidi.shared.signing.SigningKeyCapabilities;
import org.heidiverse.heidi.shared.signing.SigningKeyProvider;
import org.heidiverse.heidi.signing.adapters.RemoteSigningKeyProvider;
import org.heidiverse.heidi.signing.adapters.SigningClientKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Tenant-scoped signing backend configuration. Secrets are encrypted before persistence. */
@Service
public class SigningProviderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SigningProviderService.class);
    private static final String REFRESH_INTERVAL =
            "${heidi.platform.signing-provider.capability-refresh-interval-ms:21600000}";
    private static final String REFRESH_INITIAL_DELAY =
            "${heidi.platform.signing-provider.capability-refresh-initial-delay-ms:60000}";
    private static final Set<String> BACKEND_CLIENTS = Set.of("issuer", "verifier");
    private static final String REGISTERED_AUTHENTICATION = "registered";
    private static final String PLATFORM_ASSISTED_ACCEPTANCE = "platform-assisted";

    private final SigningProviderRepository repository;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private final String encryptionMasterKey;
    private final String globalEndpoint;
    private final String globalAuthenticationMode;
    private final String globalBearerToken;
    private final SigningClientStatusService clientStatusService;

    @Value("${heidi.platform.signing-provider.client-seed:}")
    private String platformClientSeed;

    public SigningProviderService(
            SigningProviderRepository repository,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,
            @Value("${heidi.platform.encryption-master-key}") String encryptionMasterKey,
            @Value("${heidi.platform.global-signing-provider.endpoint:}") String globalEndpoint,
            @Value("${heidi.platform.global-signing-provider.authentication-mode:none}")
                    String globalAuthenticationMode,
            @Value("${heidi.platform.global-signing-provider.bearer-token:}") String globalBearerToken,
            SigningClientStatusService clientStatusService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
        this.encryptionMasterKey = encryptionMasterKey;
        this.globalEndpoint = globalEndpoint;
        this.globalAuthenticationMode = globalAuthenticationMode;
        this.globalBearerToken = globalBearerToken;
        this.clientStatusService = clientStatusService;
    }

    /** Includes providers without keys, whose keyless operation grants still need reconciliation. */
    public List<SigningProviderEntity> providers() {
        return repository.findAll();
    }

    public List<SigningProviderResponse> findAll(String tenantId) {
        var global = repository.findAllByTenantIdIsNullOrderByName();
        var tenant = tenantId == null || tenantId.isBlank()
                ? List.<SigningProviderEntity>of()
                : repository.findAllByTenantIdOrderByName(tenantId);
        return java.util.stream.Stream.concat(global.stream(), tenant.stream())
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SigningProviderResponse create(String tenantId, SigningProviderRequest request) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("A tenant provider requires a tenant");
        }
        return createInScope(tenantId, request);
    }

    @Transactional
    public SigningProviderResponse createGlobal(SigningProviderRequest request) {
        return createInScope(null, request);
    }

    /** Checks a provider without storing its endpoint or credentials. */
    public SigningProviderConnectionResponse checkConnection(SigningProviderRequest request) {
        if (request.endpoint() == null || request.endpoint().isBlank()) {
            throw new IllegalArgumentException("A signing provider requires an endpoint");
        }
        return connection(configuration(request));
    }

    private SigningProviderConnectionResponse connection(SigningProviderConfiguration configuration) {
        var signingProvider = provider(configuration, null);
        var capabilities = SigningKeyCapabilities.of(signingProvider);
        var clients = clientStatus(signingProvider, capabilities.scheme());
        return new SigningProviderConnectionResponse(
                capabilities.scheme(),
                configuration.endpoint(),
                configuration.authenticationMode(),
                capabilities.supportedAlgorithms(),
                capabilities.digestSigningAlgorithms(),
                capabilities.supportedOperations(),
                capabilities.keylessOperations(),
                capabilities.canCreate(),
                capabilities.canImport(),
                capabilities.canDelete(),
                signingProvider instanceof RemoteSigningKeyProvider remote
                        ? remote.clientAcceptance() : null,
                clients,
                capabilities.contentKeyAlgorithms());
    }

    /** Checks a persisted provider without requiring the caller to resend its credentials. */
    public SigningProviderConnectionResponse connection(String tenantId, int providerId) {
        return connection(configuration(resolve(tenantId, providerId)));
    }

    /** Introduces a backend public key only when the service explicitly allows it. */
    public SigningProviderConnectionResponse registerClient(
            SigningProviderRequest request, String client) {
        return registerClient(configuration(request), client);
    }

    /** Registers a backend client using a persisted provider configuration. */
    public SigningProviderConnectionResponse registerClient(
            String tenantId, int providerId, String client) {
        return registerClient(configuration(owned(tenantId, providerId)), client);
    }

    private SigningProviderConnectionResponse registerClient(
            SigningProviderConfiguration configuration, String client) {
        if (!BACKEND_CLIENTS.contains(client)) {
            throw new IllegalArgumentException("Unsupported signing client: " + client);
        }
        if (configuration.endpoint() == null || configuration.endpoint().isBlank()) {
            throw new IllegalArgumentException("A signing provider requires an endpoint");
        }
        var signingProvider = provider(configuration, null);
        if (!(signingProvider instanceof RemoteSigningKeyProvider remote)
                || !PLATFORM_ASSISTED_ACCEPTANCE.equals(remote.clientAcceptance())) {
            throw new IllegalArgumentException(
                    "This signing provider does not allow platform-assisted client registration");
        }
        var scheme = SigningKeyCapabilities.of(remote).scheme();
        var status = clientStatus(remote, scheme).stream()
                .filter(candidate -> client.equals(candidate.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "The " + client + " client public key is unavailable"));
        if (!status.known()) {
            if (status.publicKey() == null || status.publicKey().isBlank()) {
                throw new IllegalArgumentException(
                        "The " + client + " client public key is unavailable");
            }
            remote.addClient(client, status.publicKey());
        }
        return connection(configuration);
    }

    /**
     * Which clients the signing service knows, and the keys to add for the ones it does not. A
     * service that predates clients answers nothing, and the check reports the rest as before.
     */
    private List<SigningProviderConnectionResponse.ClientStatus> clientStatus(
            SigningKeyProvider signingProvider, String scheme) {
        if (!(signingProvider instanceof RemoteSigningKeyProvider remote)) return List.of();
        if (remote.clientAcceptance() == null) return List.of();

        try {
            var remoteClients = remote.clients();
            var acceptedKeys = new java.util.LinkedHashMap<String, String>();
            remoteClients.forEach(client -> acceptedKeys.put(client.name(), client.publicKey()));
            var remoteByName = remoteClients.stream().collect(
                    java.util.stream.Collectors.toMap(
                            RemoteSigningKeyProvider.ClientStatus::name,
                            java.util.function.Function.identity(),
                            (first, ignored) -> first));
            return clientStatusService.describe(scheme, acceptedKeys).stream()
                    .map(status -> {
                        var remoteStatus = remoteByName.get(status.name());
                        return new SigningProviderConnectionResponse.ClientStatus(
                                status.name(), status.known(),
                                status.known() && remoteStatus != null && remoteStatus.registered(),
                                remoteStatus == null ? null : remoteStatus.lastUse(),
                                status.publicKey());
                    })
                    .toList();
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not read the signing service's clients", exception);
            return clientStatusService.describe(scheme, java.util.Map.of()).stream()
                    .map(status -> new SigningProviderConnectionResponse.ClientStatus(
                            status.name(), status.known(), false, null, status.publicKey()))
                    .toList();
        }
    }

    /** Creates or refreshes the chart-configured global provider. */
    @Transactional
    public SigningProviderEntity ensureGlobalProvider() {
        if (globalEndpoint == null || globalEndpoint.isBlank()) {
            throw new IllegalStateException("Global signing provider endpoint is not configured");
        }
        var name = "Heidi Software Signing Service";
        var globalProviders = repository.findAllByTenantIdIsNullOrderByName();
        var existing = globalProviders.stream()
                .filter(provider -> name.equals(provider.getName()))
                .findFirst();
        if (existing.isEmpty()) {
            // Preserve an operator's existing default, while still making the
            // chart-managed service available in the global Cockpit provider list.
            var isDefault = globalProviders.stream().noneMatch(SigningProviderEntity::isDefaultProvider);
            var request = new SigningProviderRequest(
                    name, globalEndpoint, globalBearerToken, isDefault, globalAuthenticationMode);
            var created = repository.save(entity(null, request));
            reconcileClients(created);
            return created;
        }

        var provider = existing.get();
        // This named provider is owned by deployment configuration. Reconcile its
        // connection settings so an upgrade from bearer to registered auth does
        // not leave the persisted global row using credentials the chart removed.
        var desired = configuration(
                new SigningProviderRequest(
                        name,
                        globalEndpoint,
                        globalBearerToken,
                        provider.isDefaultProvider(),
                        globalAuthenticationMode));
        var current = configuration(provider);
        if (!Objects.equals(current.endpoint(), desired.endpoint())
                || !Objects.equals(current.bearerToken(), desired.bearerToken())
                || !Objects.equals(current.authenticationMode(), desired.authenticationMode())) {
            provider.setEncryptedConfiguration(
                    encrypt(snapshot(desired), provider.getEncryptionSalt()));
            var updated = repository.save(provider);
            reconcileClients(updated);
            return updated;
        }
        // A previous deployment may have persisted this provider before the
        // global default was selected. Restore the fallback only when no other
        // global provider is already the operator's default.
        if (!provider.isDefaultProvider()
                && globalProviders.stream().noneMatch(SigningProviderEntity::isDefaultProvider)) {
            provider.setDefaultProvider(true);
            var updated = repository.save(provider);
            reconcileClients(updated);
            return updated;
        }
        reconcileClients(provider);
        return provider;
    }

    private SigningProviderResponse createInScope(
            String tenantId, SigningProviderRequest request) {
        if (request.endpoint() == null || request.endpoint().isBlank()) {
            throw new IllegalArgumentException("A signing provider requires an endpoint");
        }
        var entity = entity(tenantId, request);
        if (entity.isDefaultProvider()) clearDefault(tenantId);
        return toResponse(repository.save(entity));
    }

    private SigningProviderEntity entity(String tenantId, SigningProviderRequest request) {
        var entity = new SigningProviderEntity();
        entity.setTenantId(tenantId);
        entity.setName(request.name());
        entity.setEncryptionSalt(UUID.randomUUID().toString());
        entity.setDefaultProvider(Boolean.TRUE.equals(request.defaultProvider()));
        var configuration = configuration(request);
        entity.setEncryptedConfiguration(
                encrypt(snapshot(configuration), entity.getEncryptionSalt()));
        return entity;
    }

    @Transactional
    public void delete(String tenantId, int providerId) {
        var entity = owned(tenantId, providerId);
        repository.delete(entity);
    }

    public SigningProviderEntity owned(String tenantId, int providerId) {
        var entity = repository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Signing provider not found: " + providerId));
        if (!Objects.equals(tenantId, entity.getTenantId())) {
            throw new SecurityException("Signing provider does not belong to the tenant");
        }
        return entity;
    }

    /** Resolves a provider selected by a tenant; global providers are intentionally shared. */
    public SigningProviderEntity resolve(String tenantId, int providerId) {
        var entity = repository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Signing provider not found: " + providerId));
        if (entity.getTenantId() != null && !Objects.equals(tenantId, entity.getTenantId())) {
            throw new SecurityException("Signing provider does not belong to the tenant");
        }
        return entity;
    }

    public SigningProviderEntity defaultProvider(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            var tenantProvider = repository.findFirstByTenantIdAndDefaultProviderTrue(tenantId);
            if (tenantProvider.isPresent()) return tenantProvider.get();
        }
        return repository.findFirstByTenantIdIsNullAndDefaultProviderTrue().orElse(null);
    }

    /** Returns the decrypted connection settings for an owned provider for internal routing only. */
    public SigningProviderConfiguration runtimeConfiguration(String tenantId, int providerId) {
        return configuration(resolve(tenantId, providerId));
    }

    /** Refreshes and persists the capabilities used by issuer runtime configuration. */
    @Transactional
    public SigningProviderResponse refresh(String tenantId, int providerId) {
        var entity = resolve(tenantId, providerId);
        refresh(entity);
        return toResponse(entity);
    }

    /** Keeps provider capability snapshots current without coupling metadata requests to providers. */
    @Scheduled(fixedDelayString = REFRESH_INTERVAL, initialDelayString = REFRESH_INITIAL_DELAY)
    public void refreshAll() {
        for (var entity : repository.findAll()) {
            try {
                refresh(entity);
                reconcileClients(entity);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not refresh signing provider capabilities for {}", entity.getId(), exception);
            }
        }
    }

    /** Best-effort reconciliation for global providers; outages must not break the platform. */
    void reconcileClients(SigningProviderEntity entity) {
        if (entity == null || entity.getId() == null || entity.getTenantId() != null) return;

        try {
            var configuration = configuration(entity);
            if (!REGISTERED_AUTHENTICATION.equals(configuration.authenticationMode())) return;

            var signingProvider = provider(entity.getTenantId(), entity.getId());
            if (!(signingProvider instanceof RemoteSigningKeyProvider remote)
                    || !PLATFORM_ASSISTED_ACCEPTANCE.equals(remote.clientAcceptance())) return;

            var scheme = configuration.scheme();
            if (scheme == null || scheme.isBlank()) {
                scheme = SigningKeyCapabilities.of(remote).scheme();
            }
            clientStatus(remote, scheme).stream()
                    .filter(status -> BACKEND_CLIENTS.contains(status.name())
                            && !status.known()
                            && status.publicKey() != null
                            && !status.publicKey().isBlank())
                    .forEach(status -> {
                        try {
                            remote.addClient(status.name(), status.publicKey());
                            LOGGER.info("Registered {} client with signing provider {}",
                                    status.name(), entity.getId());
                        } catch (RuntimeException exception) {
                            LOGGER.warn("Could not register {} client with signing provider {}",
                                    status.name(), entity.getId(), exception);
                        }
                    });
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not reconcile signing provider clients for {}", entity.getId(), exception);
        }
    }

    /** Builds the protocol client for a tenant provider without exposing its credentials. */
    public SigningKeyProvider provider(String tenantId, int providerId) {
        var entity = resolve(tenantId, providerId);
        return provider(configuration(entity), providerId);
    }

    /** Refuse unusable bindings before a backend attempts its first private-key operation. */
    public void requireClient(String tenantId, int providerId,
            org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotConsumer consumer) {
        var config = configuration(resolve(tenantId, providerId));
        if ("none".equals(config.authenticationMode())) return;
        var client = consumer.name().toLowerCase(java.util.Locale.ROOT);
        var accepted = clientStatus(provider(tenantId, providerId), config.scheme()).stream()
                .anyMatch(status -> client.equals(status.name()) && status.known());
        if (!accepted) {
            throw new IllegalArgumentException("Signing service " + config.endpoint()
                    + " must accept the current " + client + " client key before this identity can use it");
        }
    }

    public SigningKeyCapabilities capabilities(String tenantId, int providerId) {
        return SigningKeyCapabilities.of(provider(tenantId, providerId));
    }

    public SigningProviderConfiguration configuration(SigningProviderEntity entity) {
        try {
            return objectMapper.readValue(
                    CryptoUtils.decryptBlob(
                            entity.getEncryptedConfiguration(),
                            entity.getEncryptionSalt(),
                            encryptionMasterKey),
                    SigningProviderConfiguration.class);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not decrypt signing provider configuration " + entity.getId(), exception);
        }
    }

    private SigningProviderConfiguration configuration(SigningProviderRequest request) {
        return normalizedConfiguration(request);
    }

    /** Captures the provider state that issuer metadata may use without probing the provider. */
    private SigningProviderConfiguration snapshot(SigningProviderConfiguration configuration) {
        try {
            return configuration.withCapabilities(
                    SigningKeyCapabilities.of(provider(configuration, null)));
        } catch (RuntimeException exception) {
            return configuration;
        }
    }

    private void refresh(SigningProviderEntity entity) {
        var configuration = configuration(entity);
        var capabilities = SigningKeyCapabilities.of(provider(configuration, entity.getId()));
        entity.setEncryptedConfiguration(
                encrypt(configuration.withCapabilities(capabilities), entity.getEncryptionSalt()));
        repository.save(entity);
    }

    private SigningKeyProvider provider(SigningProviderConfiguration config, Integer providerId) {
        if (config.endpoint() == null || config.endpoint().isBlank()) {
            throw new IllegalArgumentException(providerId == null
                    ? "Global signing provider endpoint is not configured"
                    : "Signing provider has no endpoint: " + providerId);
        }
        RemoteSigningKeyProvider.Authentication authentication;
        if ("registered".equals(config.authenticationMode())
                && platformClientSeed != null && !platformClientSeed.isBlank()) {
            authentication = new SigningClientKeys("platform", platformClientSeed).authentication();
        } else {
            authentication = RemoteSigningKeyProvider.Authentication.from(
                    config.authenticationMode(),
                    config.bearerToken());
        }
        return new RemoteSigningKeyProvider(
                restClientBuilder,
                objectMapper,
                URI.create(config.endpoint()),
                authentication);
    }

    private SigningProviderConfiguration normalizedConfiguration(SigningProviderRequest request) {
        var mode = request.authenticationMode();
        if (mode == null || mode.isBlank()) {
            mode = request.bearerToken() == null || request.bearerToken().isBlank()
                    ? "none" : "bearer";
        }
        mode = mode.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("none", "bearer", "mtls", "registered").contains(mode)) {
            throw new IllegalArgumentException("Unsupported signing authentication mode: " + mode);
        }
        if ("bearer".equals(mode)
                && (request.bearerToken() == null || request.bearerToken().isBlank())) {
            throw new IllegalArgumentException("Bearer signing authentication requires a token");
        }
        if ("registered".equals(mode)
                && (platformClientSeed == null || platformClientSeed.isBlank())) {
            throw new IllegalArgumentException(
                    "Registered signing authentication requires the platform client seed");
        }
        return new SigningProviderConfiguration(request.endpoint(), request.bearerToken(), mode);
    }

    private String encrypt(SigningProviderConfiguration configuration, String salt) {
        try {
            return CryptoUtils.encryptBlob(
                    objectMapper.writeValueAsString(configuration),
                    salt,
                    encryptionMasterKey);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize signing provider configuration", exception);
        }
    }

    private void clearDefault(String tenantId) {
        var providers = tenantId == null
                ? repository.findAllByTenantIdIsNullOrderByName()
                : repository.findAllByTenantIdOrderByName(tenantId);
        providers.forEach(provider -> {
            provider.setDefaultProvider(false);
            repository.save(provider);
        });
    }

    private SigningProviderResponse toResponse(SigningProviderEntity entity) {
        var configuration = configuration(entity);
        return new SigningProviderResponse(
                entity.getId(), entity.getName(),
                configuration.scheme(),
                configuration.endpoint(),
                configuration.authenticationMode(),
                entity.isDefaultProvider(),
                configuration.supportedAlgorithms(),
                configuration.digestSigningAlgorithms(),
                configuration.supportedOperations(),
                configuration.keylessOperations(),
                configuration.canCreate(),
                configuration.canImport(),
                configuration.canDelete(),
                entity.getTenantId() == null ? "global" : "tenant",
                entity.getTenantId(),
                configuration.contentKeyAlgorithms());
    }
}
