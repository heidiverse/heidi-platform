// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.heidiverse.heidi.entity.data.repository.IdentityKeySlotRepository;
import org.heidiverse.heidi.entity.data.repository.IssuerDefinitionRepository;
import org.heidiverse.heidi.entity.data.repository.SigningCertificateRepository;
import org.heidiverse.heidi.entity.data.repository.SigningKeyRepository;
import org.heidiverse.heidi.entity.data.repository.SigningKeyVersionRepository;
import org.heidiverse.heidi.entity.data.repository.SigningProviderRepository;
import org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotRequest;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotResponse;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotConsumer;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Owns the identity-to-key/provider boundary introduced by ADR 0002. */
@Service
public class IdentityKeySlotService {
    private static final int DEFAULT_ORDER = 0;
    private static final String DUPLICATE_DECRYPTION_KEY =
            "Identity already has this request decryption key for this trust framework";
    private static final String BBS_PRESENTATION_SETUP_OPERATION =
            "w3c.bbs-data-integrity-presentation-setup";

    private final IdentityKeySlotRepository slotRepository;
    private final IssuerDefinitionRepository identityRepository;
    private final SigningKeyRepository keyRepository;
    @Autowired(required = false)
    private org.heidiverse.heidi.entity.data.repository.IssuingSubcaRepository issuingSubcas;
    private final SigningKeyVersionRepository versionRepository;
    private final SigningCertificateRepository certificateRepository;
    private final SigningProviderRepository providerRepository;
    private final SigningProviderService providerService;
    private final SwissKeyPublication swissKeys;
    private final org.heidiverse.heidi.entity.data.repository.IdentityKeyPublicationRepository publications;
    private final org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService credentials;
    private final org.heidiverse.heidi.entity.data.service.ProofSchemeDataService proofs;

    /** The complete public metadata needed to route one signing operation. */
    public record ResolvedKey(
            IdentityKeySlotEntity slot,
            SigningKeyEntity key,
            SigningKeyVersionEntity version,
            List<String> certificateChain) {}

    @Autowired
    public IdentityKeySlotService(
            IdentityKeySlotRepository slotRepository,
            IssuerDefinitionRepository identityRepository,
            SigningKeyRepository keyRepository,
            SigningKeyVersionRepository versionRepository,
            SigningCertificateRepository certificateRepository,
            SigningProviderRepository providerRepository,
            SigningProviderService providerService,
            org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService credentials,
            org.heidiverse.heidi.entity.data.service.ProofSchemeDataService proofs,
            org.heidiverse.heidi.entity.data.repository.IdentityKeyPublicationRepository publications,
            SwissKeyPublication swissKeys) {
        this.slotRepository = slotRepository;
        this.identityRepository = identityRepository;
        this.keyRepository = keyRepository;
        this.versionRepository = versionRepository;
        this.certificateRepository = certificateRepository;
        this.providerRepository = providerRepository;
        this.providerService = providerService;
        this.credentials = credentials;
        this.proofs = proofs;
        this.publications = publications;
        this.swissKeys = swissKeys;
    }

    @Transactional(readOnly = true)
    public List<IdentityKeySlotResponse> list(String tenantId, int identityId) {
        requireIdentity(tenantId, identityId);
        return slotRepository.findAllByIdentityIdOrderByTypeAscOrderAsc(identityId).stream()
                .map(IdentityKeySlotService::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IdentityKeySlotEntity> slots(int identityId) {
        return slotRepository.findAllByIdentityIdOrderByTypeAscOrderAsc(identityId);
    }

    /**
     * Resolves a slot to its active provider key. A logical key id narrows credential slots and
     * lets schemas select an additional key without consulting unrelated identity slots.
     */
    @Transactional(readOnly = true)
    public Optional<ResolvedKey> resolve(
            String tenantId,
            int identityId,
            IdentityKeySlotType type,
            IssuerTrustSystem trustSystem,
            String operation,
            String logicalKeyId,
            SigningCertificateProfile profile) {
        var identity = requireIdentity(tenantId, identityId);
        var normalizedOperation = normalized(operation);
        var candidates = slots(identityId).stream()
                .filter(slot -> slot.getType() == type)
                .filter(slot -> Objects.equals(slot.getTrustSystem(), trustSystem))
                .filter(slot -> Objects.equals(slot.getOperation(), normalizedOperation))
                .toList();
        // Custom became an explicit trust framework after the original generic/default route
        // was introduced. Keep old signing assignments usable while new assignments are saved
        // with the explicit Custom scope. Decryption slots intentionally do not use this fallback:
        // Default is already the documented global decryption scope.
        if (candidates.isEmpty()
                && trustSystem == IssuerTrustSystem.Custom
                && type != IdentityKeySlotType.DECRYPTION) {
            candidates = slots(identityId).stream()
                    .filter(slot -> slot.getType() == type)
                    .filter(slot -> slot.getTrustSystem() == IssuerTrustSystem.Default)
                    .filter(slot -> Objects.equals(slot.getOperation(), normalizedOperation))
                    .toList();
        }
        for (var slot : candidates) {
            if (slot.getKeyId() == null) {
                if (logicalKeyId == null || logicalKeyId.isBlank()) {
                    return Optional.of(new ResolvedKey(slot, null, null, List.of()));
                }
                continue;
            }
            var key = keyRepository.findById(slot.getKeyId()).orElse(null);
            if (key == null || !Objects.equals(identity.getTenantId(), key.getTenantId())) {
                continue;
            }
            if (logicalKeyId != null && !logicalKeyId.isBlank()
                    && !Objects.equals(logicalKeyId, key.getLogicalKeyId())) {
                continue;
            }
            var activeId = key.getActiveVersionId();
            if (activeId == null) continue;
            var version = versionRepository.findById(activeId).orElse(null);
            if (version == null || !Objects.equals(key.getId(), version.getKeyId())
                    || !SigningKeyService.ACTIVE.equals(version.getStatus())) {
                continue;
            }
            var chain = certificateChain(slot, version, profile, trustSystem);
            return Optional.of(new ResolvedKey(slot, key, version, chain));
        }
        return Optional.empty();
    }

    /** Resolves a key through a slot when the owning identity is not part of the request. */
    @Transactional(readOnly = true)
    public Optional<ResolvedKey> resolveByKey(
            String tenantId, UUID keyId, IdentityKeySlotType type) {
        if (keyId == null || type == null) return Optional.empty();
        var key = keyRepository.findById(keyId).orElse(null);
        if (key == null || !Objects.equals(tenantId, key.getTenantId())) {
            return Optional.empty();
        }
        var slot = slotRepository.findAllByKeyId(keyId).stream()
                .filter(candidate -> candidate.getType() == type)
                .findFirst()
                .orElse(null);
        if (slot == null) return Optional.empty();
        var activeId = key.getActiveVersionId();
        if (activeId == null) return Optional.empty();
        var version = versionRepository.findById(activeId).orElse(null);
        if (version == null || !keyId.equals(version.getKeyId())
                || !SigningKeyService.ACTIVE.equals(version.getStatus())) {
            return Optional.empty();
        }
        var profile = slot.getType().certificateProfile(slot.getTrustSystem());
        return Optional.of(new ResolvedKey(
                slot, key, version,
                certificateChain(slot, version, profile, slot.getTrustSystem())));
    }

    public Optional<ResolvedKey> resolve(
            String tenantId,
            int identityId,
            IdentityKeySlotType type,
            IssuerTrustSystem trustSystem,
            String operation,
            String logicalKeyId) {
        return resolve(tenantId, identityId, type, trustSystem, operation, logicalKeyId,
                SigningCertificateProfile.CREDENTIAL_SIGNING);
    }

    private List<String> certificateChain(
            IdentityKeySlotEntity slot, SigningKeyVersionEntity version,
            SigningCertificateProfile profile, IssuerTrustSystem trustSystem) {
        var certificate = selectCertificate(slot.getCertificateId(), version.getId(), profile, trustSystem);
        if (certificate == null && slot.getType().requiresCertificate(trustSystem)) {
            throw new IllegalArgumentException("EUDI slot requires a valid " + profile + " certificate");
        }
        if (certificate == null) return List.of();
        var chain = certificate.getCertificateChain();
        // EUDI x5c stops before a self-signed trust anchor, including imported chains.
        if (trustSystem == IssuerTrustSystem.EUDI && chain.size() > 1
                && selfSigned(chain.getLast())) {
            return chain.subList(0, chain.size() - 1);
        }
        return chain;
    }

    private static boolean selfSigned(String encoded) {
        try {
            var factory = java.security.cert.CertificateFactory.getInstance("X.509");
            var cert = (java.security.cert.X509Certificate) factory.generateCertificate(
                    new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(encoded)));
            if (!cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal())) return false;
            cert.verify(cert.getPublicKey());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private org.heidiverse.heidi.entity.model.entity.SigningCertificateEntity selectCertificate(
            UUID selectedId, UUID versionId, SigningCertificateProfile profile, IssuerTrustSystem trust) {
        if (selectedId != null) {
            var selected = certificateRepository.findById(selectedId)
                    .orElseThrow(() -> new IllegalArgumentException("Slot certificate not found"));
            SigningCertificateRules.requireUsable(selected, versionId, profile, trust);
            return selected;
        }
        var now = Instant.now();
        var eligible = certificateRepository.forProfile(versionId, profile,
                SigningCertificateRules.framework(trust)).stream()
                .filter(record -> SigningCertificateRules.validAt(record, now)).toList();
        if (eligible.size() > 1) throw new IllegalArgumentException("Select a certificate for this identity slot");
        return eligible.isEmpty() ? null : eligible.getFirst();
    }

    @Transactional
    public IdentityKeySlotResponse save(
            String tenantId, int identityId, IdentityKeySlotRequest request) {
        var identity = requireOwnedIdentity(tenantId, identityId);
        // Serialize validation/publication with provider deletion and version activation.
        if (request != null && request.keyId() != null) {
            keyRepository.findByIdForUpdate(request.keyId()).orElseThrow();
        }
        validate(tenantId, request);
        var slot = request.id() == null
                ? new IdentityKeySlotEntity()
                : slotRepository.findById(request.id())
                        .orElseThrow(() -> new IllegalArgumentException("Identity slot not found"));
        if (slot.getIdentityId() != null && !Objects.equals(slot.getIdentityId(), identityId)) {
            throw new SecurityException("Identity slot belongs to another identity");
        }
        slot.setId(slot.getId() == null ? UUID.randomUUID() : slot.getId());
        slot.setIdentityId(identityId);
        slot.setType(request.type());
        slot.setTrustSystem(request.trustSystem());
        slot.setOperation(normalized(request.operation()));
        slot.setConsumer(request.type() == IdentityKeySlotType.OPERATION
                ? (request.consumer() == null
                        ? consumerFor(request.operation()) : request.consumer())
                : null);
        slot.setKeyId(request.keyId());
        slot.setProviderId(request.providerId());
        slot.setCertificateId(request.certificateId());
        if (request.order() != null) slot.setOrder(request.order());
        // Assignment edits must not erase trust identifiers or operation parameters.
        if (request.configuration() != null) {
            slot.setConfiguration(mergeConfiguration(slot, request.configuration()));
        }
        if (slot.getCreatedAt() == null) slot.setCreatedAt(Instant.now());
        slot.setUpdatedAt(Instant.now());
        if (slot.getKeyId() != null && slot.getType() != IdentityKeySlotType.DECRYPTION) {
            var key = keyRepository.findById(slot.getKeyId()).orElseThrow();
            var certificate = selectCertificate(slot.getCertificateId(), key.getActiveVersionId(),
                    slot.getType().certificateProfile(slot.getTrustSystem()), slot.getTrustSystem());
            if (certificate == null && slot.getType().requiresCertificate(slot.getTrustSystem())) {
                throw new IllegalArgumentException("EUDI slot requires a valid "
                        + slot.getType().certificateProfile(slot.getTrustSystem()) + " certificate");
            }
            slot.setCertificateId(certificate == null ? null : certificate.getId());
        }
        ensureUnique(identityId, slot);
        if (slot.getKeyId() != null) {
            var key = keyRepository.findById(slot.getKeyId()).orElseThrow();
            requirePublication(slot, versionRepository.findById(key.getActiveVersionId()).orElseThrow());
        }
        validateClients(identity, slot);
        var saved = slotRepository.saveAndFlush(slot);
        SigningCertificateRules.markUsed(certificateRepository, saved.getCertificateId());
        rememberPublicKey(saved);
        return response(saved);
    }

    public void rememberPublicKey(IdentityKeySlotEntity slot) {
        if (slot.getKeyId() == null || slot.getType() == IdentityKeySlotType.DECRYPTION) return;
        var key = keyRepository.findById(slot.getKeyId()).orElseThrow();
        publications.retain(slot.getIdentityId(), slot.getType(), slot.getTrustSystem(), key.getActiveVersionId());
    }

    public List<String> publicKeys(int identityId, IdentityKeySlotType type) {
        return publications.publicKeys(identityId, type);
    }

    public List<String> publicKeys(
            int identityId, IdentityKeySlotType type, IssuerTrustSystem trustSystem) {
        return publications.publicKeys(identityId, type, trustSystem);
    }

    public boolean published(UUID keyId) { return publications.referencesKey(keyId); }

    public void requireClients(IdentityKeySlotEntity slot) {
        validateClients(identityRepository.findById(slot.getIdentityId()).orElseThrow(), slot);
    }

    public void requirePublication(IdentityKeySlotEntity slot, SigningKeyVersionEntity version) {
        if (slot.getTrustSystem() != IssuerTrustSystem.Switzerland
                || (slot.getType() != IdentityKeySlotType.IDENTITY_STATEMENT
                    && slot.getType() != IdentityKeySlotType.CREDENTIAL_SIGNING)) return;
        var trust = slot.getType() == IdentityKeySlotType.IDENTITY_STATEMENT ? slot
                : slots(slot.getIdentityId()).stream()
                        .filter(candidate -> candidate.getType() == IdentityKeySlotType.IDENTITY_STATEMENT
                                && candidate.getTrustSystem() == IssuerTrustSystem.Switzerland)
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Configure the Swiss identity-statement slot first"));
        var configuration = trust.getConfiguration();
        var did = configuration == null ? null : configuration.path("swissDid").asText();
        if (did == null || did.isBlank()) throw new IllegalArgumentException("Swiss slot requires its published DID");
        swissKeys.requirePublished(did, version.getPublicJwk());
        if (slot.getType() != IdentityKeySlotType.IDENTITY_STATEMENT) return;

        // A DID change must keep every credential key resolvable under the new identity.
        for (var credential : slots(slot.getIdentityId())) {
            if (credential.getType() != IdentityKeySlotType.CREDENTIAL_SIGNING
                    || credential.getTrustSystem() != IssuerTrustSystem.Switzerland) continue;
            var key = keyRepository.findById(credential.getKeyId()).orElseThrow();
            if (key.getActiveVersionId() == null) continue;
            var active = versionRepository.findById(key.getActiveVersionId()).orElseThrow();
            swissKeys.requirePublished(did, active.getPublicJwk());
        }
    }

    private void validateClients(IssuerDefinitionEntity identity, IdentityKeySlotEntity slot) {
        switch (slot.getType()) {
            case CREDENTIAL_SIGNING, DECRYPTION -> requireSlotClient(identity.getTenantId(), slot, IdentityKeySlotConsumer.ISSUER);
            case OPERATION -> requireSlotClient(identity.getTenantId(), slot, slot.getConsumer());
            case IDENTITY_STATEMENT -> {
                if (credentials.findPublishedIssuerIdentityIds().contains(identity.getId())) {
                    requireSlotClient(identity.getTenantId(), slot, IdentityKeySlotConsumer.ISSUER);
                }
                if (proofs.findActiveVerifierIdentityIds().contains(identity.getId())) {
                    requireSlotClient(identity.getTenantId(), slot, IdentityKeySlotConsumer.VERIFIER);
                }
            }
            case PRESENTATION_SIGNING -> requireSlotClient(
                    identity.getTenantId(), slot, IdentityKeySlotConsumer.VERIFIER);
            default -> { }
        }
    }

    /** Schema assignment adds a consumer even when the slots themselves did not change. */
    public void requireClient(int identityId, IdentityKeySlotConsumer consumer) {
        requireClient(identityId, consumer, null);
    }

    /** Validates only the slot roles used by the operation being assigned. */
    public void requireClient(
            int identityId,
            IdentityKeySlotConsumer consumer,
            Set<IdentityKeySlotType> types) {
        var identity = identityRepository.findById(identityId).orElseThrow();
        for (var slot : slots(identityId)) {
            if (slot.getType() == IdentityKeySlotType.FEDERATION || slot.getType() == IdentityKeySlotType.STATUS_LIST) continue;
            if (types != null && !types.contains(slot.getType())) continue;
            if (slot.getType() == IdentityKeySlotType.OPERATION && slot.getConsumer() != consumer) continue;
            if (consumer == IdentityKeySlotConsumer.VERIFIER
                    && slot.getType() != IdentityKeySlotType.IDENTITY_STATEMENT
                    && slot.getType() != IdentityKeySlotType.PRESENTATION_SIGNING
                    && slot.getType() != IdentityKeySlotType.OPERATION) continue;
            requireSlotClient(identity.getTenantId(), slot, consumer);
        }
    }

    private void requireSlotClient(String tenantId, IdentityKeySlotEntity slot, IdentityKeySlotConsumer consumer) {
        var providerId = slot.getProviderId();
        if (providerId == null && slot.getKeyId() != null) {
            providerId = keyRepository.findById(slot.getKeyId()).orElseThrow().getProviderId();
        }
        if (providerId != null) providerService.requireClient(tenantId, providerId, consumer);
    }

    @Transactional
    public void delete(String tenantId, int identityId, UUID slotId) {
        requireOwnedIdentity(tenantId, identityId);
        var slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Identity slot not found"));
        if (!Objects.equals(identityId, slot.getIdentityId())) {
            throw new SecurityException("Identity slot belongs to another identity");
        }
        slotRepository.delete(slot);
    }

    @Transactional
    public void remove(
            String tenantId,
            int identityId,
            IdentityKeySlotType type,
            IssuerTrustSystem trustSystem,
            String operation) {
        requireOwnedIdentity(tenantId, identityId);
        slotRepository.findByIdentityIdAndTypeAndTrustSystemAndOperation(
                        identityId, type, trustSystem, normalized(operation))
                .ifPresent(slotRepository::delete);
    }

    /** Returns whether a key is explicitly reachable from an identity. */
    @Transactional(readOnly = true)
    public boolean ownsKey(int identityId, UUID keyId) {
        return keyId != null && slotRepository
                .findAllByIdentityIdOrderByTypeAscOrderAsc(identityId).stream()
                .anyMatch(slot -> keyId.equals(slot.getKeyId()));
    }

    @Transactional(readOnly = true)
    public boolean hasCredentialKey(
            int identityId, IssuerTrustSystem trustSystem, String logicalKeyId) {
        if (logicalKeyId == null || logicalKeyId.isBlank()) return false;
        return slots(identityId).stream()
                .filter(slot -> slot.getType() == IdentityKeySlotType.CREDENTIAL_SIGNING
                        && Objects.equals(slot.getTrustSystem(), trustSystem))
                .map(IdentityKeySlotEntity::getKeyId)
                .filter(Objects::nonNull)
                .map(keyRepository::findById)
                .flatMap(Optional::stream)
                .anyMatch(key -> logicalKeyId.equals(key.getLogicalKeyId()));
    }

    /** Adds a slot used by a subsystem that does not expose a slot editor of its own. */
    @Transactional
    public void ensureKeySlot(
            String tenantId,
            int identityId,
            IdentityKeySlotType type,
            IssuerTrustSystem trustSystem,
            String operation,
            UUID keyId,
            Integer providerId) {
        var request = new IdentityKeySlotRequest(
                null, type, trustSystem, operation, keyId, providerId, null,
                DEFAULT_ORDER, null);
        var identity = requireOwnedIdentity(tenantId, identityId);
        var existing = slotRepository
                .findByIdentityIdAndTypeAndTrustSystemAndOperation(
                        identityId, type, trustSystem, normalized(operation));
        if (existing.isPresent()) {
            var slot = existing.get();
            if (Objects.equals(slot.getKeyId(), keyId)
                    && Objects.equals(slot.getProviderId(), providerId)) return;
            if (type == IdentityKeySlotType.STATUS_LIST) {
                throw new IllegalArgumentException(
                        "Identity already has a different status-list signing key");
            }
            request = new IdentityKeySlotRequest(
                    slot.getId(), type, trustSystem, operation, keyId, providerId,
                    Objects.equals(slot.getKeyId(), keyId)
                            ? slot.getCertificateId() : null,
                    slot.getOrder(), slot.getConfiguration());
        }
        save(tenantId, identity.getId(), request);
    }

    /** Stores role configuration on the slot that owns it. */
    @Transactional
    public IdentityKeySlotResponse updateConfiguration(
            String tenantId,
            int identityId,
            IdentityKeySlotType type,
            IssuerTrustSystem trustSystem,
            String operation,
            JsonNode configuration) {
        var identity = requireOwnedIdentity(tenantId, identityId);
        var slot = slotRepository.findByIdentityIdAndTypeAndTrustSystemAndOperation(
                        identity.getId(), type, trustSystem, normalized(operation))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Identity has no slot for trust system " + trustSystem));
        return save(tenantId, identityId, new IdentityKeySlotRequest(
                slot.getId(), slot.getType(), slot.getTrustSystem(), slot.getOperation(),
                slot.getKeyId(), slot.getProviderId(), slot.getCertificateId(),
                slot.getOrder(), configuration, slot.getConsumer()));
    }

    private void validate(String tenantId, IdentityKeySlotRequest request) {
        if (request == null || request.type() == null) {
            throw new IllegalArgumentException("Identity slot type is required");
        }
        if (request.order() != null && request.order() < 0) {
            throw new IllegalArgumentException("Identity slot order must not be negative");
        }
        if (request.type() == IdentityKeySlotType.OPERATION) {
            if (request.providerId() == null) {
                throw new IllegalArgumentException("Operation slots require a provider");
            }
            if (request.operation() == null || request.operation().isBlank()) {
                throw new IllegalArgumentException("Operation slots require an operation");
            }
        } else if (request.keyId() == null) {
            throw new IllegalArgumentException("Key slots require a key");
        }
        if ((request.type() == IdentityKeySlotType.IDENTITY_STATEMENT
                || request.type() == IdentityKeySlotType.PRESENTATION_SIGNING)
                && request.trustSystem() == null) {
            throw new IllegalArgumentException("Trust signing slots require a trust system");
        }
        if ((request.type() == IdentityKeySlotType.FEDERATION
                || request.type() == IdentityKeySlotType.STATUS_LIST)
                && request.trustSystem() != null) {
            throw new IllegalArgumentException("This slot type does not use a trust system");
        }
        if (request.type() == IdentityKeySlotType.OPERATION && request.trustSystem() == null) {
            throw new IllegalArgumentException("Operation slots require a trust system");
        }
        if (request.keyId() != null) {
            if (issuingSubcas != null && issuingSubcas.existsByKeyId(request.keyId())) {
                throw new IllegalArgumentException("SubCA keys cannot be assigned to identity slots");
            }
            var key = keyRepository.findById(request.keyId())
                    .orElseThrow(() -> new IllegalArgumentException("Signing key not found"));
            if (!Objects.equals(tenantId, key.getTenantId())) {
                throw new SecurityException("Signing key belongs to another organisation");
            }
            if (request.providerId() != null
                    && !Objects.equals(request.providerId(), key.getProviderId())) {
                throw new IllegalArgumentException("Slot provider does not hold the signing key");
            }
        }
        if (request.providerId() != null) {
            var provider = providerRepository.findById(request.providerId())
                    .orElseThrow(() -> new IllegalArgumentException("Signing provider not found"));
            // A platform-wide provider may hold a tenant-owned key; tenant providers remain isolated.
            if (provider.getTenantId() != null && !Objects.equals(tenantId, provider.getTenantId())) {
                throw new SecurityException("Signing provider belongs to another organisation");
            }
        }
        if (request.type() == IdentityKeySlotType.DECRYPTION && request.keyId() != null) {
            var key = keyRepository.findById(request.keyId())
                    .orElseThrow(() -> new IllegalArgumentException("Signing key not found"));
            var providerId = request.providerId() == null
                    ? key.getProviderId() : request.providerId();
            if (providerService != null) {
                var capabilities = providerService.capabilities(tenantId, providerId);
                var active = key.getActiveVersionId() == null
                        ? null : versionRepository.findById(key.getActiveVersionId()).orElse(null);
                var compatible = false;
                if (active != null) {
                    var ref = providerService.provider(tenantId, providerId).resolve(active.getKeyUri());
                    compatible = capabilities.contentKeyAlgorithms().stream()
                            .filter(algorithm -> compatibleContentKeyAlgorithm(active.getPublicJwk(), algorithm))
                            .anyMatch(algorithm -> ref.usages().contains(requiredUsage(algorithm)));
                }
                if (!compatible) {
                    throw new IllegalArgumentException(
                            "Signing provider cannot derive credential request content keys for this key");
                }
            }
        }
        if (request.type() == IdentityKeySlotType.DECRYPTION && request.certificateId() != null) {
            throw new IllegalArgumentException(
                    "Credential request decryption slots do not use certificates");
        }
        if (request.type() != IdentityKeySlotType.DECRYPTION && request.keyId() != null) {
            var key = keyRepository.findById(request.keyId())
                    .orElseThrow(() -> new IllegalArgumentException("Signing key not found"));
            var active = key.getActiveVersionId() == null
                    ? null : versionRepository.findById(key.getActiveVersionId()).orElse(null);
            if (active == null || !SigningKeyService.ACTIVE.equals(active.getStatus())
                    || !active.getUsages().contains(SigningKeyUsage.SIGN)) {
                throw new IllegalArgumentException(
                        "Signing slot requires an active key version with SIGN usage");
            }
        }
        if (request.certificateId() != null) {
            var certificate = certificateRepository.findById(request.certificateId())
                    .orElseThrow(() -> new IllegalArgumentException("Signing certificate not found"));
            if (request.keyId() == null) {
                throw new IllegalArgumentException("A certificate requires a signing key");
            }
            var version = certificate.getKeyVersionId();
            var key = keyRepository.findById(request.keyId())
                    .orElseThrow(() -> new IllegalArgumentException("Signing key not found"));
            var belongs = versionRepository.findById(version)
                    .map(candidate -> key.getId().equals(candidate.getKeyId()))
                    .orElse(false);
            if (!belongs) {
                throw new SecurityException("Signing certificate belongs to another key");
            }
            SigningCertificateRules.requireUsable(certificate, key.getActiveVersionId(),
                    request.type().certificateProfile(request.trustSystem()), request.trustSystem());
        }
    }


    private void ensureUnique(int identityId, IdentityKeySlotEntity slot) {
        // Credential-request decryption may be global or trust-framework scoped.
        // Keep every assigned key so the issuer can publish all active key versions and
        // support rotation or fallback between multiple decryption keys.
        var same = slotRepository.findAllByIdentityIdOrderByTypeAscOrderAsc(identityId).stream()
                .filter(candidate -> !Objects.equals(candidate.getId(), slot.getId()))
                .filter(candidate -> candidate.getType() == slot.getType())
                .filter(candidate -> slot.getType() == IdentityKeySlotType.DECRYPTION
                        ? candidate.getTrustSystem() == null
                                || slot.getTrustSystem() == null
                                || candidate.getTrustSystem() == slot.getTrustSystem()
                        : candidate.getTrustSystem() == slot.getTrustSystem())
                .filter(candidate -> Objects.equals(candidate.getOperation(), slot.getOperation()))
                .toList();
        if (slot.getType() == IdentityKeySlotType.DECRYPTION) {
            if (same.stream().anyMatch(candidate -> Objects.equals(candidate.getKeyId(), slot.getKeyId()))) {
                throw new IllegalArgumentException(DUPLICATE_DECRYPTION_KEY);
            }
            return;
        }
        if (!same.isEmpty() && (slot.getType() != IdentityKeySlotType.CREDENTIAL_SIGNING
                || same.stream().anyMatch(candidate -> Objects.equals(
                        candidate.getKeyId(), slot.getKeyId())))) {
            throw new IllegalArgumentException("Identity already has this slot");
        }
    }

    private IssuerDefinitionEntity requireIdentity(String tenantId, int identityId) {
        var identity = identityRepository.findByIdAndDeletedFalse(identityId)
                .orElseThrow(() -> new IllegalArgumentException("Identity not found"));
        if (identity.getTenantId() != null && !Objects.equals(tenantId, identity.getTenantId())) {
            throw new SecurityException("Identity belongs to another organisation");
        }
        return identity;
    }

    /** Slot writes must be performed in the identity's own scope. Global identities use null. */
    private IssuerDefinitionEntity requireOwnedIdentity(String tenantId, int identityId) {
        var identity = requireIdentity(tenantId, identityId);
        if (!Objects.equals(tenantId, identity.getTenantId())) {
            throw new SecurityException("Identity slots can only be changed in the identity scope");
        }
        return identity;
    }

    private static IdentityKeySlotResponse response(IdentityKeySlotEntity slot) {
        return new IdentityKeySlotResponse(
                slot.getId(), slot.getType(), slot.getTrustSystem(), slot.getOperation(),
                slot.getKeyId(), slot.getProviderId(), slot.getCertificateId(), slot.getOrder(),
                publicConfiguration(slot), slot.getUpdatedAt(), slot.getConsumer());
    }

    private static JsonNode mergeConfiguration(IdentityKeySlotEntity slot, JsonNode update) {
        if (slot.getType() != IdentityKeySlotType.IDENTITY_STATEMENT
                || slot.getTrustSystem() != IssuerTrustSystem.Switzerland
                || slot.getConfiguration() == null
                || !slot.getConfiguration().isObject()
                || !update.isObject()) return update;
        var merged = (ObjectNode) slot.getConfiguration().deepCopy();
        merged.setAll((ObjectNode) update);
        return merged;
    }

    private static JsonNode publicConfiguration(IdentityKeySlotEntity slot) {
        var configuration = slot.getConfiguration();
        if (slot.getType() != IdentityKeySlotType.IDENTITY_STATEMENT
                || slot.getTrustSystem() != IssuerTrustSystem.Switzerland
                || configuration == null
                || !configuration.isObject()) return configuration;
        var publicConfiguration = (ObjectNode) configuration.deepCopy();
        publicConfiguration.remove("swissTrustRegistryClientSecret");
        publicConfiguration.remove("swissTrustRegistryRefreshToken");
        return publicConfiguration;
    }

    private static IdentityKeySlotConsumer consumerFor(String operation) {
        return BBS_PRESENTATION_SETUP_OPERATION.equals(operation)
                ? IdentityKeySlotConsumer.VERIFIER : IdentityKeySlotConsumer.ISSUER;
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean compatibleContentKeyAlgorithm(String publicJwk, String algorithm) {
        try {
            var key = JWK.parse(publicJwk);
            if (algorithm.startsWith("ECDH-ES")) {
                return key instanceof ECKey ec && Curve.P_256.equals(ec.getCurve());
            }
            if ("RSA-OAEP-256".equals(algorithm)) {
                return key instanceof RSAKey rsa
                        && new java.math.BigInteger(1, rsa.getModulus().decode()).bitLength() >= 2048;
            }
        } catch (Exception ignored) {
            // Invalid legacy key material is rejected by the provider when it is used.
        }
        return false;
    }

    private static SigningKeyUsage requiredUsage(String algorithm) {
        return algorithm.startsWith("ECDH-ES")
                ? SigningKeyUsage.KEY_AGREEMENT : SigningKeyUsage.UNWRAP;
    }
}
