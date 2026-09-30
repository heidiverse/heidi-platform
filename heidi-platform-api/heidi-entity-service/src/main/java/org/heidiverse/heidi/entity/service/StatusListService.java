// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import org.heidiverse.heidi.entity.data.repository.StatusListAllocationRepository;
import org.heidiverse.heidi.entity.data.repository.StatusListRepository;
import org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService;
import org.heidiverse.heidi.entity.data.service.IssuerDataService;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialType;
import org.heidiverse.heidi.entity.model.entity.StatusListAllocationEntity;
import org.heidiverse.heidi.entity.model.entity.StatusListEntity;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeEntity;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustConfigurationRequest;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileCatalog;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileFamily;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileRole;
import org.heidiverse.heidi.entity.model.statuslist.StatusListEntryResponse;
import org.heidiverse.heidi.entity.model.statuslist.StatusListPublishMode;
import org.heidiverse.heidi.entity.model.statuslist.StatusListRequest;
import org.heidiverse.heidi.entity.model.statuslist.StatusListReferenceResponse;
import org.heidiverse.heidi.entity.model.statuslist.StatusListResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class StatusListService {
    static final long DEFAULT_TTL_SECONDS = StatusListTokenService.DEFAULT_TTL_SECONDS;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final StatusListRepository repository;
    private final StatusListAllocationRepository allocationRepository;
    private final CredentialSchemeDataService credentialSchemeDataService;
    private final IssuerDataService issuerDataService;
    private final SigningKeyService keyService;
    private final IdentityKeySlotService identityKeySlotService;
    private final IssuerService issuerService;
    private final SwissTrustStatementRefreshService swissTrustStatementRefreshService;
    private final StatusListCodec codec;
    private final StatusListTokenService tokenService;
    private final RestClient client;
    private final RestClient.Builder clientBuilder;
    private final String platformBaseUrl;

    @Autowired
    public StatusListService(
            StatusListRepository repository,
            StatusListAllocationRepository allocationRepository,
            CredentialSchemeDataService credentialSchemeDataService,
            IssuerDataService issuerDataService,
            SigningKeyService keyService,
            IdentityKeySlotService identityKeySlotService,
            IssuerService issuerService,
            StatusListCodec codec,
            StatusListTokenService tokenService,
            SwissTrustStatementRefreshService swissTrustStatementRefreshService,
            RestClient.Builder clientBuilder,
            @Value("${heidi.platform.public-base-url}") String platformBaseUrl) {
        this.repository = repository;
        this.allocationRepository = allocationRepository;
        this.credentialSchemeDataService = credentialSchemeDataService;
        this.issuerDataService = issuerDataService;
        this.keyService = keyService;
        this.identityKeySlotService = identityKeySlotService;
        this.issuerService = issuerService;
        this.swissTrustStatementRefreshService = swissTrustStatementRefreshService;
        this.codec = codec;
        this.tokenService = tokenService;
        this.clientBuilder = clientBuilder.clone();
        this.client = this.clientBuilder.build();
        this.platformBaseUrl = platformBaseUrl.replaceAll("/$", "");
    }

    public StatusListService(
            StatusListRepository repository,
            StatusListAllocationRepository allocationRepository,
            CredentialSchemeDataService credentialSchemeDataService,
            SigningKeyService keyService,
            StatusListCodec codec,
            StatusListTokenService tokenService,
            RestClient.Builder clientBuilder,
            String platformBaseUrl) {
        this(repository, allocationRepository, credentialSchemeDataService, null, keyService,
                null, null, codec, tokenService, null, clientBuilder, platformBaseUrl);
    }

    public List<StatusListResponse> findAll(String tenantId) {
        return repository.findAllByTenantIdOrderByName(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    public StatusListResponse find(String tenantId, UUID id) {
        return toResponse(owned(tenantId, id));
    }

    @Transactional
    public StatusListResponse create(String tenantId, StatusListRequest request) {
        validate(request);
        keyService.owned(tenantId, request.signingKeyId());

        var now = Instant.now();
        var entity = new StatusListEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setName(request.name().trim());
        entity.setType(request.type());
        entity.setBits(request.bits());
        entity.setEntryCount(request.entryCount());
        entity.setPublishMode(request.publishMode());
        entity.setEndpoint(request.endpoint());
        entity.setSigningKeyId(request.signingKeyId());
        entity.setTtl(request.ttl() == null ? DEFAULT_TTL_SECONDS : request.ttl());
        entity.setStatusData(codec.empty(request.entryCount(), request.bits()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        var saved = repository.save(entity);
        if (identityKeySlotService != null) {
            credentialSchemeDataService.findAllByStatusListId(saved.getId()).stream()
                    .map(StatusListService::issuerIdentity)
                    .filter(Objects::nonNull)
                    .distinct()
                    .forEach(identity -> identityKeySlotService.ensureKeySlot(
                            identity.getTenantId(), identity.getId(),
                            org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.STATUS_LIST,
                            null, null, saved.getSigningKeyId(), null));
        }
        if (issuerService != null) issuerService.reconcileSigningGrants();
        return toResponse(saved);
    }

    public StatusListEntryResponse entry(String tenantId, UUID id, int index) {
        var entity = owned(tenantId, id);
        requireIndex(entity, index);
        return new StatusListEntryResponse(
                index, codec.get(entity.getStatusData(), entity.getBits(), index));
    }

    @Transactional
    public StatusListEntryResponse setEntry(String tenantId, UUID id, int index, int status) {
        var entity = ownedForUpdate(tenantId, id);
        requireIndex(entity, index);
        var data = entity.getStatusData();
        codec.set(data, entity.getBits(), index, status);
        entity.setStatusData(data);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);

        if (entity.getPublishedAt() != null) {
            publish(entity);
        } else if (entity.getSwissStatusListId() != null) {
            republishSwiss(entity);
        }
        return new StatusListEntryResponse(index, status);
    }

    @Transactional
    public StatusListReferenceResponse allocate(
            String issuerSlug,
            String credentialIdentifier,
            String version,
            String allocationId,
            int count) {
        if (allocationId == null || allocationId.isBlank()) {
            throw new IllegalArgumentException("Allocation ID is required");
        }
        if (count < 1) throw new IllegalArgumentException("Allocation count must be positive");

        var scheme = credentialSchemeDataService
                .findByCredentialIdentifierAndVersion(credentialIdentifier, version)
                .orElseThrow(() -> new IllegalArgumentException("Issuance schema not found"));
        if (!Objects.equals(scheme.getIssuerDefinition().getSlug(), issuerSlug)) {
            throw new SecurityException("Issuance schema belongs to a different issuer");
        }
        if (!scheme.getSupportedCredentialTypes().contains(CredentialType.SD_JWT)) return null;
        if (scheme.getStatusListId() == null) return null;
        if (count > scheme.getMaxBatchSize()) {
            throw new IllegalArgumentException("Allocation count exceeds credential batch size");
        }

        var statusList = ownedForUpdate(scheme.getTenantId(), scheme.getStatusListId());
        var swiss = swissProfile(scheme.getIssuanceProfileId());
        if (swiss && (!hasText(statusList.getSwissStatusListUrl())
                || statusList.getSwissPublishedAt() == null)) {
            throw new IllegalStateException(
                    "Swiss issuance requires a status list published in the Swiss registry");
        }
        ensureStatusSlot(scheme, statusList);
        if (issuerService != null) issuerService.reconcileSigningGrants();
        var indices = new ArrayList<Integer>(count);
        var nextIndex = allocationRepository.maxIndex(statusList.getId());
        nextIndex = nextIndex == null ? 0 : Math.addExact(nextIndex, 1);

        for (var ordinal = 0; ordinal < count; ordinal++) {
            var key = allocationId + ":" + ordinal;
            var existing = allocationRepository.findByStatusListIdAndAllocationKey(
                    statusList.getId(), key);
            if (existing.isPresent()) {
                indices.add(existing.get().getIndex());
                continue;
            }
            if (nextIndex >= statusList.getEntryCount()) {
                throw new IllegalStateException("Status list has no free entries");
            }

            var allocation = new StatusListAllocationEntity();
            allocation.setId(UUID.randomUUID());
            allocation.setStatusListId(statusList.getId());
            allocation.setAllocationKey(key);
            allocation.setIndex(nextIndex);
            allocation.setCreatedAt(Instant.now());
            allocationRepository.save(allocation);
            indices.add(nextIndex);
            nextIndex++;
        }

        if (statusList.getPublishedAt() == null) publish(statusList);
        var statusUri = swiss
                ? URI.create(statusList.getSwissStatusListUrl()) : uri(statusList);
        return new StatusListReferenceResponse(statusUri, indices);
    }

    @Transactional
    public StatusListResponse publish(String tenantId, UUID id) {
        var entity = ownedForUpdate(tenantId, id);
        publish(entity);
        return toResponse(entity);
    }

    @Transactional
    public StatusListResponse publishSwiss(String tenantId, UUID id) {
        var entity = ownedForUpdate(tenantId, id);
        var configuration = swissConfiguration(tenantId);
        var accessToken = accessToken(configuration);
        if (entity.getSwissStatusListId() == null) {
            var created = createSwissEntry(configuration, accessToken);
            entity.setSwissStatusListId(created.id());
            entity.setSwissStatusListUrl(created.url());
        }
        uploadSwiss(entity, configuration, accessToken);
        entity.setSwissPublishedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
        return toResponse(entity);
    }

    public String publicToken(UUID id) {
        var entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Status list not found: " + id));
        if (entity.getPublishMode() != StatusListPublishMode.LOCAL
                || entity.getPublishedAt() == null) {
            throw new IllegalArgumentException("Status list is not published locally: " + id);
        }
        return token(entity, uri(entity).toString());
    }

    @Transactional
    public void delete(String tenantId, UUID id) {
        repository.delete(owned(tenantId, id));
    }

    private void publish(StatusListEntity entity) {
        var uri = uri(entity).toString();
        var token = token(entity, uri);
        if (entity.getPublishMode() == StatusListPublishMode.REMOTE) {
            client.put()
                    .uri(entity.getEndpoint())
                    .contentType(MediaType.parseMediaType(StatusListTokenService.MEDIA_TYPE))
                    .body(token)
                    .retrieve()
                    .toBodilessEntity();
        }
        entity.setPublishedToken(token);
        entity.setPublishedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);

        if (entity.getSwissStatusListId() != null) {
            republishSwiss(entity);
        }
    }

    private void republishSwiss(StatusListEntity entity) {
        var configuration = swissConfiguration(entity.getTenantId());
        uploadSwiss(entity, configuration, accessToken(configuration));
        var now = Instant.now();
        entity.setSwissPublishedAt(now);
        entity.setUpdatedAt(now);
        repository.save(entity);
    }

    private SwissEntry createSwissEntry(
            SwissConfiguration configuration, String accessToken) {
        var response = statusClient(configuration.settings())
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/status/business-entities/{businessEntityId}/status-list-entries/")
                        .build(configuration.settings().swissStatusRegistryPartnerId()))
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || response.path("id").isMissingNode()
                || !response.path("statusRegistryUrl").isTextual()) {
            throw new IllegalStateException("Swiss status registry returned an invalid entry");
        }
        return new SwissEntry(
                UUID.fromString(response.path("id").asText()),
                response.path("statusRegistryUrl").asText());
    }

    private void uploadSwiss(
            StatusListEntity entity, SwissConfiguration configuration, String accessToken) {
        var entryId = entity.getSwissStatusListId();
        var url = entity.getSwissStatusListUrl();
        if (entryId == null || url == null || url.isBlank()) {
            throw new IllegalStateException("Swiss status list resource is not initialized");
        }
        var token = tokenService.create(entity, url, configuration.settings().swissDid());
        statusClient(configuration.settings())
                .put()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/status/business-entities/{businessEntityId}/status-list-entries/{entryId}")
                        .build(configuration.settings().swissStatusRegistryPartnerId(), entryId))
                .contentType(MediaType.parseMediaType(StatusListTokenService.MEDIA_TYPE))
                .header("Authorization", "Bearer " + accessToken)
                .body(token)
                .retrieve()
                .toBodilessEntity();
    }

    private RestClient statusClient(IssuerTrustConfigurationRequest settings) {
        return clientBuilder.clone().baseUrl(settings.swissStatusRegistryApiUrl()).build();
    }

    private String accessToken(SwissConfiguration configuration) {
        return swissTrustStatementRefreshService.retrieveAccessToken(
                        configuration.identity(), configuration.settings())
                .orElseThrow(() -> new IllegalStateException(
                        "Swiss Trust Registry OAuth credentials are missing or invalid"));
    }

    private SwissConfiguration swissConfiguration(String tenantId) {
        if (issuerDataService == null || identityKeySlotService == null
                || swissTrustStatementRefreshService == null) {
            throw new IllegalStateException("Swiss status registry is not configured");
        }
        SwissConfiguration global = null;
        for (var identity : issuerDataService.findAll()) {
            if (identity.getTenantId() != null && !Objects.equals(identity.getTenantId(), tenantId)) {
                continue;
            }
            for (var slot : identityKeySlotService.slots(identity.getId())) {
                if (slot.getType() != IdentityKeySlotType.IDENTITY_STATEMENT
                        || slot.getTrustSystem() != IssuerTrustSystem.Switzerland) continue;
                var settings = swissSettings(slot);
                if (hasText(settings.swissStatusRegistryApiUrl())
                        && hasText(settings.swissStatusRegistryPartnerId())) {
                    var configuration = new SwissConfiguration(identity, settings);
                    if (Objects.equals(identity.getTenantId(), tenantId)) return configuration;
                    if (global == null) global = configuration;
                }
            }
        }
        if (global != null) return global;
        throw new IllegalStateException(
                "Swiss status registry URL and business partner ID are not configured");
    }

    private IssuerTrustConfigurationRequest swissSettings(IdentityKeySlotEntity slot) {
        var configuration = slot.getConfiguration();
        if (configuration == null || configuration.isNull()) {
            return new IssuerTrustConfigurationRequest(null, null, null, null, null, null, null);
        }
        try {
            return JSON.treeToValue(configuration, IssuerTrustConfigurationRequest.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid Swiss trust configuration", exception);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String token(StatusListEntity entity, String uri) {
        return swissDid(entity)
                .map(did -> tokenService.create(entity, uri, did))
                .orElseGet(() -> tokenService.create(entity, uri));
    }

    private Optional<String> swissDid(StatusListEntity entity) {
        if (credentialSchemeDataService == null || identityKeySlotService == null) {
            return Optional.empty();
        }
        return credentialSchemeDataService.findAllByStatusListId(entity.getId()).stream()
                .filter(scheme -> swissProfile(scheme.getIssuanceProfileId()))
                .map(CredentialSchemeEntity::getIssuerDefinition)
                .filter(Objects::nonNull)
                .map(IssuerDefinitionEntity::getId)
                .filter(Objects::nonNull)
                .flatMap(identityId -> identityKeySlotService.slots(identityId).stream())
                .filter(slot -> slot.getType() == IdentityKeySlotType.IDENTITY_STATEMENT
                        && slot.getTrustSystem() == IssuerTrustSystem.Switzerland)
                .map(this::swissSettings)
                .map(IssuerTrustConfigurationRequest::swissDid)
                .filter(StatusListService::hasText)
                .findFirst();
    }

    private record SwissConfiguration(
            IssuerDefinitionEntity identity,
            IssuerTrustConfigurationRequest settings) {}

    private record SwissEntry(UUID id, String url) {}

    private StatusListEntity owned(String tenantId, UUID id) {
        var entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Status list not found: " + id));
        requireOwner(entity, tenantId);
        return entity;
    }

    private StatusListEntity ownedForUpdate(String tenantId, UUID id) {
        var entity = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Status list not found: " + id));
        requireOwner(entity, tenantId);
        return entity;
    }

    private void requireOwner(StatusListEntity entity, String tenantId) {
        if (!Objects.equals(entity.getTenantId(), tenantId)) {
            throw new SecurityException("Status list does not belong to the tenant");
        }
    }

    private void requireIndex(StatusListEntity entity, int index) {
        if (index < 0 || index >= entity.getEntryCount()) {
            throw new IllegalArgumentException("Status list index is out of bounds: " + index);
        }
    }

    private void ensureStatusSlot(CredentialSchemeEntity scheme, StatusListEntity statusList) {
        if (identityKeySlotService == null || scheme.getIssuerDefinition() == null) return;
        var identity = scheme.getIssuerDefinition();
        identityKeySlotService.ensureKeySlot(
                identity.getTenantId(), identity.getId(),
                org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.STATUS_LIST,
                null, null, statusList.getSigningKeyId(), null);
    }

    private static IssuerDefinitionEntity issuerIdentity(CredentialSchemeEntity scheme) {
        return scheme.getIssuerDefinition();
    }

    private boolean swissProfile(String profileId) {
        return profileId != null
                && EcosystemProfileCatalog.require(profileId, EcosystemProfileRole.ISSUANCE)
                        .family() == EcosystemProfileFamily.SWISS_SWIYU;
    }

    private void validate(StatusListRequest request) {
        StatusListCodec.requireBits(request.bits());
        if (request.entryCount() < 1) {
            throw new IllegalArgumentException("Entry count must be positive");
        }
        if (request.publishMode() == StatusListPublishMode.REMOTE) {
            requireHttpEndpoint(request.endpoint());
        } else if (request.endpoint() != null) {
            throw new IllegalArgumentException("A local status list cannot have a remote endpoint");
        }
    }

    private void requireHttpEndpoint(URI endpoint) {
        if (endpoint == null
                || !endpoint.isAbsolute()
                || !("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme()))) {
            throw new IllegalArgumentException("Remote endpoint must be an absolute HTTP(S) URI");
        }
    }

    private StatusListResponse toResponse(StatusListEntity entity) {
        var key = keyService.owned(entity.getTenantId(), entity.getSigningKeyId());
        return new StatusListResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getType(),
                entity.getBits(),
                entity.getEntryCount(),
                entity.getPublishMode(),
                entity.getEndpoint(),
                uri(entity),
                entity.getSigningKeyId(),
                key.getLogicalKeyId(),
                entity.getTtl(),
                entity.getPublishedAt(),
                entity.getSwissStatusListId(),
                entity.getSwissStatusListUrl() == null ? null : URI.create(entity.getSwissStatusListUrl()),
                entity.getSwissPublishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private URI uri(StatusListEntity entity) {
        if (entity.getPublishMode() == StatusListPublishMode.REMOTE) return entity.getEndpoint();
        return URI.create(platformBaseUrl + "/public/v1/status-lists/" + entity.getId());
    }
}
