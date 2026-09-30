// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import org.heidiverse.heidi.entity.data.repository.SigningKeyVersionRepository;
import org.heidiverse.heidi.entity.data.repository.SigningFlowRepository;
import org.heidiverse.heidi.entity.data.repository.SigningKeyRepository;
import org.heidiverse.heidi.entity.data.repository.SigningCertificateRepository;
import org.heidiverse.heidi.entity.data.repository.IdentityKeySlotRepository;
import org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyEntity;
import org.heidiverse.heidi.entity.model.entity.SigningCertificateEntity;
import org.heidiverse.heidi.entity.model.exceptions.CertificateInUseException;
import org.heidiverse.heidi.entity.model.signing.SigningKeyId;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.issuer.KeyRotationMode;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateSource;
import org.heidiverse.heidi.shared.signing.SigningKeyCapabilities;
import org.heidiverse.heidi.shared.signing.SigningKeyCreator;
import org.heidiverse.heidi.shared.signing.SigningKeyImporter;
import org.heidiverse.heidi.shared.signing.SigningKeyDeleter;
import org.heidiverse.heidi.shared.signing.SigningKeyProvider;
import org.heidiverse.heidi.shared.signing.SigningKeyRef;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;
import org.heidiverse.heidi.signing.adapters.RemoteSigningKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Owns platform key metadata while provider implementations retain private key material. */
@Service
public class SigningKeyService {
    private static final long DEFAULT_ROTATION_GRACE_SECONDS = SigningKeyEntity.DEFAULT_GRACE_SECONDS;
    private static final Logger LOGGER = LoggerFactory.getLogger(SigningKeyService.class);
    public static final String ACTIVE = "ACTIVE";
    public static final String PREPARED = "PREPARED";
    public static final String PREVIOUS = "PREVIOUS";
    public static final String REVOKED = "REVOKED";

    private final SigningKeyRepository keyRepository;
    @Autowired(required = false)
    private org.heidiverse.heidi.entity.data.repository.IssuingSubcaRepository issuingSubcas;
    private final SigningKeyVersionRepository versionRepository;
    private final SigningProviderService providerService;
    private final ObjectMapper objectMapper;
    private final SigningCertificateRepository certificateRepository;
    private final IdentityKeySlotRepository slotRepository;
    private final SigningFlowRepository signingFlows;
    private final IdentityKeySlotService identitySlots;

    @Autowired
    public SigningKeyService(
            SigningKeyRepository keyRepository,
            SigningKeyVersionRepository versionRepository,
            SigningProviderService providerService,
            ObjectMapper objectMapper,
            SigningCertificateRepository certificateRepository,
            IdentityKeySlotRepository slotRepository,
            SigningFlowRepository signingFlows, IdentityKeySlotService identitySlots) {
        this.keyRepository = keyRepository;
        this.versionRepository = versionRepository;
        this.providerService = providerService;
        this.objectMapper = objectMapper;
        this.certificateRepository = certificateRepository;
        this.slotRepository = slotRepository;
        this.signingFlows = signingFlows;
        this.identitySlots = identitySlots;
    }

    /** Creates the metadata row used by a provisioning operation. */
    private SigningKeyEntity newKey(
            String tenantId, String logicalKeyId, Integer requestedProviderId) {
        SigningKeyId.requireValid(logicalKeyId);
        var key = new SigningKeyEntity();
        key.setId(UUID.randomUUID());
        key.setTenantId(tenantId);
        key.setLogicalKeyId(logicalKeyId);
        key.setProviderId(selectProvider(tenantId, requestedProviderId).getId());
        key.setCreatedAt(Instant.now());
        return key;
    }

    @Transactional
    public ProvisionedKey create(
            String tenantId,
            String logicalKeyId,
            String algorithm,
            Integer requestedProviderId,
            String existingKeyUri) {
        return create(tenantId, logicalKeyId, Set.of(SigningKeyUsage.SIGN), algorithm,
                requestedProviderId, existingKeyUri);
    }

    @Transactional
    public ProvisionedKey create(
            String tenantId,
            String logicalKeyId,
            Set<SigningKeyUsage> usages,
            String algorithm,
            Integer requestedProviderId,
            String existingKeyUri) {
        var key = newKey(tenantId, logicalKeyId, requestedProviderId);
        var provider = providerService.provider(tenantId, key.getProviderId());
        SigningKeyRef ref;
        var versionId = UUID.randomUUID();
        if (existingKeyUri != null && !existingKeyUri.isBlank()) {
            ref = provider.resolve(existingKeyUri);
            requireUsages(ref, usages);
        } else {
            ref = created(
                    provider,
                    createProviderKey(
                            provider, key.getId(), versionId, algorithm, usages));
        }
        keyRepository.save(key);
        return addVersion(key, versionId, ref, algorithm, 1, ACTIVE, true, null);
    }

    /** Imports a private JWK directly into the configured provider; it is never persisted here. */
    @Transactional
    public ProvisionedKey importKey(
            String tenantId,
            String logicalKeyId,
            String algorithm,
            Integer requestedProviderId,
            String privateJwk) {
        return importKey(tenantId, logicalKeyId, Set.of(SigningKeyUsage.SIGN), algorithm,
                requestedProviderId, privateJwk);
    }

    @Transactional
    public ProvisionedKey importKey(
            String tenantId,
            String logicalKeyId,
            Set<SigningKeyUsage> usages,
            String algorithm,
            Integer requestedProviderId,
            String privateJwk) {
        var key = newKey(tenantId, logicalKeyId, requestedProviderId);
        var provider = providerService.provider(tenantId, key.getProviderId());
        if (!(provider instanceof SigningKeyImporter importer)
                || !SigningKeyCapabilities.of(provider).canImport()) {
            throw new IllegalArgumentException("Signing provider cannot import keys");
        }
        var versionId = UUID.randomUUID();
        var ref = created(provider, importProviderKey(
                provider, importer, key.getId(), versionId, privateJwk, algorithm,
                usages));
        keyRepository.save(key);
        return addVersion(
                key, versionId, ref, algorithm, 1, ACTIVE, true, importedKid(privateJwk));
    }

    /** Creates the next key version without changing the version used for signing. */
    @Transactional
    public ProvisionedKey prepareRotation(String tenantId, UUID keyId) {
        requireNotSubca(keyId);
        var key = ownedForUpdate(tenantId, keyId);
        var versions = versionRepository.findAllByKeyIdOrderByVersion(keyId);
        if (versions.stream().anyMatch(version -> PREPARED.equals(version.getStatus()))) {
            throw new IllegalStateException(
                    "Signing key already has a prepared version: " + keyId);
        }
        var active = versions.stream()
                .filter(version -> version.getId().equals(key.getActiveVersionId()))
                .findFirst()
                .orElseGet(() -> versions.stream().max(java.util.Comparator.comparingInt(SigningKeyVersionEntity::getVersion))
                        .orElseThrow(() -> new IllegalStateException("Key has no versions")));
        var nextVersion = versions.stream()
                .mapToInt(SigningKeyVersionEntity::getVersion)
                .max()
                .orElse(0) + 1;
        var algorithm = active.getAlgorithm();
        var provider = providerService.provider(tenantId, key.getProviderId());
        var versionId = UUID.randomUUID();
        var ref = created(provider, createProviderKey(
                provider, keyId, versionId, algorithm, active.getUsages()));
        return addVersion(key, versionId, ref, algorithm, nextVersion, PREPARED, false, null);
    }

    @Transactional
    public void updateRotationPolicy(String tenantId, UUID keyId,
            org.heidiverse.heidi.entity.model.issuer.KeyRotationPolicy policy) {
        requireNotSubca(keyId);
        var key = ownedForUpdate(tenantId, keyId);
        if (policy.mode() == KeyRotationMode.AUTOMATIC) {
            var issue = rotationIssue(key);
            if (issue.isPresent()) throw new IllegalArgumentException(issue.get());
        }
        key.setRotationMode(policy.mode());
        key.setRotationIntervalSeconds(policy.intervalSeconds());
        key.setRotationGracePeriodSeconds(policy.gracePeriodSeconds());
        keyRepository.save(key);
    }

    /** Lock and recheck age so concurrent scheduler instances cannot rotate the same key twice. */
    @Transactional
    public Optional<ProvisionedKey> rotateDue(String tenantId, UUID keyId, Instant now) {
        var key = ownedForUpdate(tenantId, keyId);
        if (key.getRotationMode() != KeyRotationMode.AUTOMATIC) return Optional.empty();
        if (key.getActiveVersionId() == null) return Optional.empty();
        if (rotationIssue(key).isPresent()) return Optional.empty();
        var active = activeVersion(tenantId, keyId);
        if (java.time.Duration.between(active.getCreatedAt(), now).getSeconds() < key.getRotationIntervalSeconds()) {
            return Optional.empty();
        }
        var prepared = allVersions(keyId).stream().filter(version -> PREPARED.equals(version.getStatus())).findFirst();
        var versionId = prepared.map(SigningKeyVersionEntity::getId)
                .orElseGet(() -> prepareRotation(tenantId, keyId).keyVersionId());
        return Optional.of(activate(tenantId, keyId, versionId));
    }

    private Optional<String> rotationIssue(SigningKeyEntity key) {
        var slots = slotRepository.findAllByKeyId(key.getId());
        if (slots.isEmpty() || slots.stream().anyMatch(slot -> slot.getType()
                != org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.DECRYPTION)) {
            return Optional.of(
                    "Automatic rotation requires assignment exclusively to request decryption slots");
        }
        var provider = providerService.provider(key.getTenantId(), key.getProviderId());
        if (!SigningKeyCapabilities.of(provider).canCreate()) {
            return Optional.of("Signing provider cannot create key versions for automatic rotation");
        }
        return Optional.empty();
    }

    /** Activates a previously prepared version and demotes the current version to previous. */
    @Transactional
    public ProvisionedKey activate(
            String tenantId, UUID keyId, UUID keyVersionId) {
        return activate(tenantId, keyId, keyVersionId, Map.of());
    }

    @Transactional
    public ProvisionedKey activate(String tenantId, UUID keyId, UUID keyVersionId,
            Map<UUID, UUID> certificatesBySlot) {
        requireNotSubca(keyId);
        var key = ownedForUpdate(tenantId, keyId);
        var target = version(tenantId, keyId, keyVersionId);
        if (!PREPARED.equals(target.getStatus())) {
            throw new IllegalArgumentException(
                    "Signing key version is not prepared for activation: " + keyVersionId);
        }
        selectActivationChains(key, target, certificatesBySlot);
        if (key.getActiveVersionId() != null) {
            var active = versionRepository.findById(key.getActiveVersionId()).orElseThrow();
            active.setPreviousUntil(Instant.now().plusSeconds(graceSeconds(key)));
            active.setStatus(PREVIOUS);
            versionRepository.save(active);
        }
        target.setPreviousUntil(null);
        target.setStatus(ACTIVE);
        key.setActiveVersionId(target.getId());
        versionRepository.save(target);
        keyRepository.saveAndFlush(key);
        slotRepository.findAllByKeyId(keyId).forEach(identitySlots::rememberPublicKey);
        return toProvisionedKey(key, target);
    }

    /** Stop new use; existing flows and the configured grace period retain authority. */
    @Transactional
    public void retire(String tenantId, UUID keyId, UUID versionId) {
        requireNotSubca(keyId);
        var key = ownedForUpdate(tenantId, keyId);
        var version = version(tenantId, keyId, versionId);
        if (PREVIOUS.equals(version.getStatus())) return;
        if (!ACTIVE.equals(version.getStatus())) {
            throw new IllegalArgumentException("Only an active key version can be retired");
        }
        version.setStatus(PREVIOUS);
        version.setPreviousUntil(Instant.now().plusSeconds(graceSeconds(key)));
        key.setActiveVersionId(null);
        versionRepository.save(version);
        keyRepository.save(key);
    }

    /** Disable provider authority first so retained flows and delayed grants cannot revive it. */
    @Transactional
    public void revoke(String tenantId, UUID keyId, UUID versionId) {
        requireNotSubca(keyId);
        var key = ownedForUpdate(tenantId, keyId);
        var version = version(tenantId, keyId, versionId);
        if (REVOKED.equals(version.getStatus())) return;
        var provider = providerService.provider(tenantId, key.getProviderId());
        if (!(provider instanceof org.heidiverse.heidi.shared.signing.SigningKeyRevoker revoker)) {
            throw new IllegalArgumentException("Signing provider cannot revoke keys");
        }
        revoker.revokeKey(new SigningKeyRef(version.getKeyUri(), version.getPublicJwk(), version.getAlgorithm()));
        version.setStatus(REVOKED);
        if (versionId.equals(key.getActiveVersionId())) key.setActiveVersionId(null);
        versionRepository.save(version);
        keyRepository.save(key);
    }

    public record ActivationSlot(UUID slotId, Integer identityId,
            org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType type,
            IssuerTrustSystem trustSystem, SigningCertificateProfile profile,
            boolean required, List<UUID> certificates) {}

    /** Resolve choices from the prepared version, without changing live slots. */
    public List<ActivationSlot> activationSlots(String tenantId, UUID keyId, UUID versionId) {
        version(tenantId, keyId, versionId);
        var now = Instant.now();
        return slotRepository.findAllByKeyId(keyId).stream()
                .filter(slot -> slot.getType() != org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.DECRYPTION)
                .map(slot -> {
                    var profile = slot.getType().certificateProfile(slot.getTrustSystem());
                    var certificates = certificateRepository.forProfile(versionId, profile,
                                    SigningCertificateRules.framework(slot.getTrustSystem())).stream()
                            .filter(certificate -> SigningCertificateRules.validAt(certificate, now))
                            .map(SigningCertificateEntity::getId).toList();
                    return new ActivationSlot(slot.getId(), slot.getIdentityId(), slot.getType(),
                            slot.getTrustSystem(), profile,
                            slot.getType().requiresCertificate(slot.getTrustSystem()), certificates);
                }).toList();
    }

    private void selectActivationChains(SigningKeyEntity key, SigningKeyVersionEntity target,
            Map<UUID, UUID> selections) {
        var slots = slotRepository.findAllByKeyId(key.getId());
        slots.forEach(identitySlots::requireClients);
        slots.forEach(slot -> identitySlots.requirePublication(slot, target));
        var choices = activationSlots(key.getTenantId(), key.getId(), target.getId());
        if (selections.keySet().stream().anyMatch(id -> choices.stream()
                .noneMatch(choice -> Objects.equals(choice.slotId(), id)))) {
            throw new IllegalArgumentException("Certificate selection names an unrelated identity slot");
        }
        var selected = new LinkedHashMap<UUID, UUID>();
        for (var choice : choices) {
            var certificateId = selections.get(choice.slotId());
            if (certificateId != null && !choice.certificates().contains(certificateId)) {
                throw new IllegalArgumentException("Select a valid " + choice.profile() + " certificate for " + choice.trustSystem());
            }
            if (certificateId == null && choice.certificates().size() == 1) {
                certificateId = choice.certificates().getFirst();
            }
            if (certificateId == null && choice.certificates().size() > 1) {
                throw new IllegalArgumentException("Select the prepared key's certificate for slot " + choice.slotId());
            }
            if (certificateId == null && choice.required()) {
                throw new IllegalArgumentException("Prepared key needs a valid " + choice.profile() + " certificate for " + choice.trustSystem());
            }
            selected.put(choice.slotId(), certificateId);
        }
        // Switch certificate references atomically with the key; existing flows keep their snapshots.
        for (var slot : slots) {
            if (!selected.containsKey(slot.getId())) continue;
            slot.setCertificateId(selected.get(slot.getId()));
            slotRepository.save(slot);
            SigningCertificateRules.markUsed(certificateRepository, slot.getCertificateId());
        }
    }

    public SigningKeyEntity owned(String tenantId, UUID keyId) {
        var key = keyRepository.findById(keyId)
                .orElseThrow(() -> new IllegalArgumentException("Signing key not found: " + keyId));
        if (!Objects.equals(tenantId, key.getTenantId())) {
            throw new SecurityException("Signing key does not belong to the tenant");
        }
        return key;
    }

    @Transactional(readOnly = true)
    public Optional<SigningKeyEntity> findOwnedByLogicalKeyId(
            String tenantId, String logicalKeyId) {
        return keyRepository.findByTenantIdAndLogicalKeyId(tenantId, logicalKeyId);
    }

    public SigningKeyVersionEntity version(
            String tenantId, UUID keyId, UUID keyVersionId) {
        owned(tenantId, keyId);
        var version = versionRepository.findById(keyVersionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Signing key version not found: " + keyVersionId));
        if (!keyId.equals(version.getKeyId())) {
            throw new SecurityException("Signing key version does not belong to the key");
        }
        return version;
    }

    private SigningKeyEntity ownedForUpdate(String tenantId, UUID keyId) {
        var key = keyRepository.findByIdForUpdate(keyId)
                .orElseThrow(() -> new IllegalArgumentException("Signing key not found: " + keyId));
        if (!Objects.equals(tenantId, key.getTenantId())) {
            throw new SecurityException("Signing key does not belong to the tenant");
        }
        return key;
    }

    public SigningKeyVersionEntity activeVersion(String tenantId, UUID keyId) {
        var key = owned(tenantId, keyId);
        if (key.getActiveVersionId() == null) {
            throw new IllegalArgumentException("Signing key has no active version: " + keyId);
        }
        var version = versionRepository.findById(key.getActiveVersionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Active signing key version is missing: " + key.getActiveVersionId()));
        if (!keyId.equals(version.getKeyId())) {
            throw new IllegalStateException("Active signing key version belongs to another key");
        }
        if (!ACTIVE.equals(version.getStatus())) {
            throw new IllegalStateException(
                    "Signing key active version is not active: " + version.getId());
        }
        return version;
    }

    public List<SigningKeyVersionEntity> versions(String tenantId, UUID keyId) {
        owned(tenantId, keyId);
        return allVersions(keyId);
    }

    /** Returns all immutable versions, including prepared and revoked tombstones. */
    @Transactional(readOnly = true)
    public List<SigningKeyVersionEntity> allVersions(UUID keyId) {
        return versionRepository.findAllByKeyIdOrderByVersion(keyId);
    }

    private static long graceSeconds(SigningKeyEntity key) {
        var configured = key.getRotationGracePeriodSeconds();
        return configured == null ? DEFAULT_ROTATION_GRACE_SECONDS : Math.max(0, configured);
    }

    /** All platform keys, used to clear grants left by removed bindings. */
    @Transactional(readOnly = true)
    public List<SigningKeyEntity> keys() {
        return keyRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<SigningKeyEntity> keys(String tenantId) {
        return keyRepository.findAllByTenantIdOrderByLogicalKeyId(tenantId);
    }

    @Transactional(readOnly = true)
    public Optional<String> keyScope(UUID keyId) {
        return keyId == null
                ? Optional.empty()
                : Optional.of("kc/" + keyId);
    }

    public List<String> previousPublicJwks(String tenantId, UUID keyId, UUID activeVersionId) {
        // Verification material outlives private signing authority, including after revocation.
        return versions(tenantId, keyId).stream()
                .filter(version -> PREVIOUS.equals(version.getStatus())
                        || (REVOKED.equals(version.getStatus()) && version.getPreviousUntil() != null))
                .map(SigningKeyVersionEntity::getPublicJwk)
                .toList();
    }

    @Transactional
    public UUID setCertificateChain(
            String tenantId, UUID keyId, UUID keyVersionId, List<String> certificateChain) {
        return setCertificateChain(tenantId, keyId, keyVersionId, certificateChain,
                SigningCertificateProfile.CREDENTIAL_SIGNING, SigningCertificateSource.IMPORTED, null);
    }

    @Transactional
    public UUID setCertificateChain(
            String tenantId,
            UUID keyId,
            UUID keyVersionId,
            List<String> certificateChain,
            SigningCertificateProfile profile,
            SigningCertificateSource source,
            IssuerTrustSystem trustSystem) {
        return setCertificateChain(tenantId, keyId, keyVersionId, certificateChain,
                profile, source, trustSystem, null);
    }

    @Transactional
    public UUID setCertificateChain(
            String tenantId, UUID keyId, UUID keyVersionId, List<String> certificateChain,
            SigningCertificateProfile profile, SigningCertificateSource source,
            IssuerTrustSystem trustSystem, String eudiLeafProfile) {
        var key = ownedForUpdate(tenantId, keyId);
        var version = versionRepository.findById(keyVersionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Signing key version not found: " + keyVersionId));
        if (!keyId.equals(version.getKeyId())) {
            throw new SecurityException("Signing key version does not belong to the key");
        }
        if (certificateRepository == null) {
            throw new IllegalStateException("Certificate storage is not configured");
        }
        var leaf = validateChain(version, certificateChain);
        var existing = certificateRepository.forProfile(keyVersionId, profile, trustSystem);
        var duplicate = existing.stream()
                .filter(record -> record.getCertificateChain().equals(certificateChain)).findFirst();
        if (duplicate.isPresent()) return duplicate.get().getId();

        // Renewal retains the previous certificate for outstanding requests and historical verification.
        var entity = new SigningCertificateEntity();
        entity.setId(UUID.randomUUID());
        entity.setKeyVersionId(keyVersionId);
        entity.setProfile(profile);
        entity.setSource(source);
        entity.setTrustSystem(trustSystem);
        entity.setEudiLeafProfile(eudiLeafProfile);
        entity.setCertificateChain(certificateChain);
        entity.setNotBefore(leaf.getNotBefore().toInstant());
        entity.setNotAfter(leaf.getNotAfter().toInstant());
        entity.setCreatedAt(Instant.now());
        certificateRepository.save(entity);
        return entity.getId();
    }

    private java.security.cert.X509Certificate validateChain(
            SigningKeyVersionEntity version, List<String> chain) {
        if (chain == null || chain.isEmpty()) {
            throw new IllegalArgumentException("A certificate chain is required");
        }
        try {
            var factory = java.security.cert.CertificateFactory.getInstance("X.509");
            var parsed = new java.util.ArrayList<java.security.cert.X509Certificate>();
            for (var encoded : chain) {
                var certificate = (java.security.cert.X509Certificate) factory.generateCertificate(
                        new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(encoded)));
                certificate.checkValidity();
                parsed.add(certificate);
            }
            for (int index = 0; index < parsed.size() - 1; index++) {
                var child = parsed.get(index);
                var issuer = parsed.get(index + 1);
                if (!child.getIssuerX500Principal().equals(issuer.getSubjectX500Principal())) {
                    throw new IllegalArgumentException("Certificate issuer does not match chain order");
                }
                var usage = issuer.getKeyUsage();
                if (issuer.getBasicConstraints() < 0 || usage == null
                        || usage.length <= 5 || !usage[5]) {
                    throw new IllegalArgumentException("Issuer certificate is not a signing CA");
                }
                child.verify(issuer.getPublicKey());
            }
            var anchor = parsed.getLast();
            if (anchor.getSubjectX500Principal().equals(anchor.getIssuerX500Principal())) {
                anchor.verify(anchor.getPublicKey());
            }
            var publicKey = com.nimbusds.jose.jwk.JWK.parse(version.getPublicJwk());
            if (!(publicKey instanceof com.nimbusds.jose.jwk.AsymmetricJWK asymmetric)
                    || !java.util.Arrays.equals(asymmetric.toPublicKey().getEncoded(),
                            parsed.getFirst().getPublicKey().getEncoded())) {
                throw new IllegalArgumentException("Certificate does not match the key version");
            }
            return parsed.getFirst();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid certificate chain: " + exception.getMessage(), exception);
        }
    }

    public List<SigningCertificateEntity> certificates(
            String tenantId, UUID keyId, UUID versionId) {
        version(tenantId, keyId, versionId);
        return certificateRepository.forVersion(versionId);
    }

    /** Deletes never-used material or retires previously used material for audit history. */
    @Transactional
    public void removeCertificate(
            String tenantId, UUID keyId, UUID versionId, UUID certificateId) {
        var key = ownedForUpdate(tenantId, keyId);
        var version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Signing key version not found: " + versionId));
        if (!key.getId().equals(version.getKeyId())) {
            throw new SecurityException("Signing key version does not belong to the key");
        }
        var certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Signing certificate not found: " + certificateId));
        if (!versionId.equals(certificate.getKeyVersionId())) {
            throw new SecurityException("Signing certificate does not belong to the key version");
        }
        if (!slotRepository.findAllByCertificateId(certificateId).isEmpty()) {
            throw new CertificateInUseException(
                    "Certificate is assigned to an identity. Remove the assignment first.");
        }
        if (certificate.getFirstUsedAt() == null) {
            certificateRepository.delete(certificate);
            return;
        }
        if (certificate.getRetiredAt() != null) return;

        // Previously assigned certificates can have signed material in flight; retain their audit record.
        certificate.setRetiredAt(Instant.now());
        certificateRepository.save(certificate);
    }

    public String createCsr(String tenantId, UUID keyId, UUID versionId,
            org.heidiverse.heidi.shared.signing.SigningCsrRequest request) {
        var key = owned(tenantId, keyId);
        var version = version(tenantId, keyId, versionId);
        if (!ACTIVE.equals(version.getStatus()) && !PREPARED.equals(version.getStatus())) {
            throw new IllegalArgumentException("Select an active or prepared key version");
        }
        var provider = providerService.provider(tenantId, key.getProviderId());
        if (!(provider instanceof org.heidiverse.heidi.shared.signing.SigningCsrProvider csr)) {
            throw new IllegalArgumentException("Signing provider cannot create certificate requests");
        }
        return csr.createCsr(new SigningKeyRef(version.getKeyUri(), version.getPublicJwk(), version.getAlgorithm()), request);
    }

    /** Deletes a prepared version without affecting the active key version. */
    @Transactional
    public void deletePreparedVersion(String tenantId, UUID keyId, UUID keyVersionId) {
        var key = ownedForUpdate(tenantId, keyId);
        var version = versionRepository.findById(keyVersionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Signing key version not found: " + keyVersionId));
        if (!keyId.equals(version.getKeyId())) {
            throw new SecurityException("Signing key version does not belong to the key");
        }
        if (!PREPARED.equals(version.getStatus())) {
            throw new IllegalArgumentException(
                    "Only prepared signing key versions can be deleted: " + keyVersionId);
        }
        var provider = providerService.provider(tenantId, key.getProviderId());
        if (!(provider instanceof SigningKeyDeleter deleter)
                || !SigningKeyCapabilities.of(provider).canDelete()) {
            throw new IllegalArgumentException(
                    "Signing provider cannot delete key version: " + version.getKeyUri());
        }
        deleter.deleteKey(new SigningKeyRef(
                version.getKeyUri(), version.getPublicJwk(), version.getAlgorithm()));
        // Retain a tombstone so this version number and its public kid are never reused.
        version.setStatus(REVOKED);
        versionRepository.save(version);
    }

    @Transactional
    public void delete(String tenantId, UUID keyId) {
        requireNotSubca(keyId);
        var key = ownedForUpdate(tenantId, keyId);
        if (identitySlots.published(keyId)) {
            throw new IllegalStateException("Published public keys must remain available; retire or revoke the key instead");
        }
        // Provider deletion is irreversible; reject retained versions before touching their material.
        if (signingFlows.referencesKey(keyId)) {
            throw new IllegalStateException("Signing key is still referenced by an issuance or presentation flow");
        }
        if (slotRepository != null && !slotRepository.findAllByKeyId(keyId).isEmpty()) {
            throw new IllegalStateException(
                    "Signing key is still assigned to an identity slot: " + keyId);
        }
        var provider = providerService.provider(tenantId, key.getProviderId());
        var capabilities = SigningKeyCapabilities.of(provider);
        var versions = versionRepository.findAllByKeyIdOrderByVersion(keyId);
        if (versions.isEmpty()) {
            throw new IllegalStateException(
                    "Signing key is incomplete and cannot be deleted: " + keyId);
        }
        for (var version : versions) {
            if (!capabilities.canDelete() || !(provider instanceof SigningKeyDeleter deleter)) {
                throw new IllegalArgumentException(
                        "Signing provider cannot delete key version: " + version.getKeyUri());
            }
            deleter.deleteKey(new SigningKeyRef(
                    version.getKeyUri(), version.getPublicJwk(), version.getAlgorithm()));
        }
        keyRepository.delete(key);
    }

    public String providerKeyId(UUID keyId, UUID versionId) {
        return "kc/" + keyId + "/" + versionId;
    }

    private ProvisionedKey addVersion(
            SigningKeyEntity key,
            UUID versionId,
            SigningKeyRef ref,
            String requestedAlgorithm,
            int version,
            String status,
            boolean activate,
            String publicationKid) {
        if (!Objects.equals(requestedAlgorithm, ref.algorithm())) {
            throw new IllegalArgumentException(
                    "Signing key uses algorithm '" + ref.algorithm()
                            + "', but configuration requires '" + requestedAlgorithm + "'");
        }
        var versionEntity = new SigningKeyVersionEntity();
        versionEntity.setId(versionId);
        versionEntity.setKeyId(key.getId());
        versionEntity.setVersion(version);
        versionEntity.setProviderKeyId(providerKeyId(ref));
        versionEntity.setKeyUri(ref.uri());
        versionEntity.setAlgorithm(ref.algorithm());
        versionEntity.setPublicJwk(publicJwk(
                ref.publicJwk(), key.getLogicalKeyId(), version, ref.algorithm(), publicationKid));
        versionEntity.setStatus(status);
        versionEntity.setUsages(ref.usages());
        versionEntity.setCreatedAt(Instant.now());
        var saved = versionRepository.save(versionEntity);
        if (activate) {
            key.setActiveVersionId(saved.getId());
            keyRepository.save(key);
        }
        return toProvisionedKey(key, saved);
    }

    private ProvisionedKey toProvisionedKey(
            SigningKeyEntity key, SigningKeyVersionEntity version) {
        return new ProvisionedKey(
                key.getId(), version.getId(), key.getLogicalKeyId(), version.getVersion(),
                key.getProviderId(), version.getProviderKeyId(), version.getKeyUri(),
                version.getAlgorithm(), version.getPublicJwk());
    }

    private SigningKeyRef createProviderKey(
            SigningKeyProvider provider,
            UUID keyId,
            UUID versionId,
            String algorithm,
            Set<SigningKeyUsage> usages) {
        if (!(provider instanceof SigningKeyCreator creator)
                || !SigningKeyCapabilities.of(provider).canCreate()) {
            throw new IllegalArgumentException("Signing provider cannot create keys: " + provider.scheme());
        }
        SigningKeyRef ref;
        if (provider instanceof RemoteSigningKeyProvider remote) {
            ref = remote.createKey("kc/" + keyId, versionId.toString(), algorithm, usages);
        } else {
            ref = creator.createKey(providerKeyId(keyId, versionId), algorithm, usages);
        }
        requireUsages(ref, usages);
        return ref;
    }

    private SigningKeyRef importProviderKey(
            SigningKeyProvider provider,
            SigningKeyImporter importer,
            UUID keyId,
            UUID versionId,
            String privateJwk,
            String algorithm,
            Set<SigningKeyUsage> usages) {
        SigningKeyRef ref;
        if (provider instanceof RemoteSigningKeyProvider remote) {
            ref = remote.importKey(
                    "kc/" + keyId, versionId.toString(), privateJwk, algorithm, usages);
        } else {
            ref = importer.importKey(providerKeyId(keyId, versionId), privateJwk, algorithm, usages);
        }
        requireUsages(ref, usages);
        return ref;
    }

    private static void requireUsages(SigningKeyRef ref, Set<SigningKeyUsage> requested) {
        if (!requested.equals(ref.usages())) {
            throw new IllegalArgumentException(
                    "Signing provider returned different key usages");
        }
    }

    /** Removes provider state when its metadata transaction fails. */
    private SigningKeyRef created(SigningKeyProvider provider, SigningKeyRef ref) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return ref;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) return;
                if (!(provider instanceof SigningKeyDeleter deleter)
                        || !SigningKeyCapabilities.of(provider).canDelete()) {
                    LOGGER.warn("Signing provider cannot roll back key '{}'", ref.uri());
                    return;
                }
                try {
                    deleter.deleteKey(ref);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Could not roll back signing key '{}'", ref.uri(), exception);
                }
            }
        });
        return ref;
    }

    private org.heidiverse.heidi.entity.model.entity.SigningProviderEntity selectProvider(
            String tenantId, Integer requestedProviderId) {
        var provider = requestedProviderId == null
                ? providerService.defaultProvider(tenantId)
                : providerService.resolve(tenantId, requestedProviderId);
        if (provider == null) {
            throw new IllegalArgumentException("No signing provider is configured for this scope");
        }
        return provider;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private String publicJwk(
            String source, String logicalKeyId, int version, String algorithm, String publicationKid) {
        try {
            var parsed = objectMapper.readTree(source);
            if (!(parsed instanceof ObjectNode json)) {
                throw new IllegalArgumentException("Provider returned a non-object public JWK");
            }
            json.put("kid", publicationKid == null || publicationKid.isBlank()
                    ? logicalKeyId + "-v" + version : publicationKid);
            json.put("alg", algorithm);
            return objectMapper.writeValueAsString(json);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not normalize provider public JWK", exception);
        }
    }

    private String importedKid(String privateJwk) {
        if (privateJwk == null || privateJwk.isBlank()) return null;
        try {
            var key = objectMapper.readTree(privateJwk);
            var kid = key == null ? null : key.get("kid");
            return kid == null || !kid.isTextual() || kid.asText().isBlank()
                    ? null : kid.asText();
        } catch (Exception exception) {
            // The provider gives the authoritative parse error and keeps the import failure clear.
            return null;
        }
    }

    private String providerKeyId(SigningKeyRef ref) {
        var separator = ref.uri().indexOf("://");
        return separator < 0 ? ref.uri() : ref.uri().substring(separator + 3);
    }

    public record ProvisionedKey(
            UUID keyId,
            UUID keyVersionId,
            String logicalKeyId,
            int version,
            Integer providerId,
            String providerKeyId,
            String keyUri,
            String algorithm,
            String publicJwk) {}

    /** SubCA keys sign certificates on the platform itself. */
    public boolean isSubca(UUID keyId) {
        return issuingSubcas != null && issuingSubcas.existsByKeyId(keyId);
    }

    private void requireNotSubca(UUID keyId) {
        if (isSubca(keyId)) {
            throw new IllegalArgumentException("SubCA key lifecycle is managed by Issuing PKI");
        }
    }
}
