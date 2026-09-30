// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.heidiverse.heidi.entity.data.repository.CredentialSchemeRepository;
import org.heidiverse.heidi.entity.data.repository.IdentityKeySlotRepository;
import org.heidiverse.heidi.entity.data.repository.IssuerDefinitionRepository;
import org.heidiverse.heidi.entity.data.repository.ProofSchemeRepository;
import org.heidiverse.heidi.entity.data.repository.SigningKeyVersionRepository;
import org.heidiverse.heidi.entity.data.repository.StatusListRepository;
import org.heidiverse.heidi.entity.data.repository.SigningKeyRepository;
import org.heidiverse.heidi.entity.data.repository.SigningCertificateRepository;
import org.heidiverse.heidi.entity.data.repository.SigningProviderRepository;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeEntity;
import org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeState;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeyIntegrityReport;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeyViolation;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Audits identity-owned key slots and reports references that cannot be used safely. */
@Service
public class IdentityKeyIntegrityService {
    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityKeyIntegrityService.class);

    private final IssuerDefinitionRepository identityRepository;
    private final CredentialSchemeRepository credentialRepository;
    private final ProofSchemeRepository proofRepository;
    private final IdentityKeySlotRepository slotRepository;
    private final SigningKeyVersionRepository versionRepository;
    private final StatusListRepository statusListRepository;
    private final SigningKeyRepository keyRepository;
    private final SigningProviderRepository providerRepository;
    private final SigningCertificateRepository certificateRepository;
    private final ObjectProvider<SigningGrantService> signingGrantService;
    private final SigningProviderService signingProviderService;

    private volatile IdentityKeyIntegrityReport latest =
            new IdentityKeyIntegrityReport(Instant.EPOCH, List.of());

    private final ObjectProvider<IssuerService> issuerService;

    public IdentityKeyIntegrityService(
            IssuerDefinitionRepository identityRepository,
            CredentialSchemeRepository credentialRepository,
            ProofSchemeRepository proofRepository,
            IdentityKeySlotRepository slotRepository,
            SigningKeyVersionRepository versionRepository,
            StatusListRepository statusListRepository,
            SigningKeyRepository keyRepository,
            SigningProviderRepository providerRepository,
            SigningCertificateRepository certificateRepository,
            ObjectProvider<IssuerService> issuerService,
            ObjectProvider<SigningGrantService> signingGrantService,
            SigningProviderService signingProviderService) {
        this.identityRepository = identityRepository;
        this.credentialRepository = credentialRepository;
        this.proofRepository = proofRepository;
        this.slotRepository = slotRepository;
        this.versionRepository = versionRepository;
        this.statusListRepository = statusListRepository;
        this.keyRepository = keyRepository;
        this.providerRepository = providerRepository;
        this.certificateRepository = certificateRepository;
        this.issuerService = issuerService;
        this.signingGrantService = signingGrantService;
        this.signingProviderService = signingProviderService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void auditAtStartup() {
        var report = scan();
        var issuer = issuerService.getIfAvailable();
        if (issuer != null) {
            issuer.reconcileSigningGrants();
            report = latest();
        }
        if (report.clean() && !grantsPending()) {
            LOGGER.info("Identity key integrity report is clean; slot enforcement may proceed");
            return;
        }
        LOGGER.error("Identity key integrity report has {} violation(s) and {} pending grant reconciliation; "
                        + "slot enforcement remains disabled",
                report.violations().size(), grantsPending() ? 1 : 0);
        report.violations().forEach(violation -> LOGGER.error(
                "identity-key violation {} [{}]: {}",
                violation.code(), violation.subject(), violation.detail()));
    }

    @Transactional(readOnly = true)
    public IdentityKeyIntegrityReport scan() {
        var violations = new ArrayList<IdentityKeyViolation>();
        var identities = identityRepository.findAllByDeletedFalse();
        for (var identity : identities) auditIdentity(identity, violations);
        for (var credential : credentialRepository.findAll()) {
            if (credential.getState() != CredentialSchemeState.ARCHIVED) {
                auditCredential(credential, violations);
            }
        }
        proofRepository.findAll().forEach(proof -> {
            if (!proof.isArchived() && proof.getVerifierIdentity() == null) {
                violations.add(new IdentityKeyViolation(
                        "PROOF_IDENTITY_MISSING", String.valueOf(proof.getId()),
                        "proof schema must name a verifier identity"));
            }
        });
        latest = new IdentityKeyIntegrityReport(Instant.now(), violations);
        return latest;
    }

    public IdentityKeyIntegrityReport latest() {
        return latest;
    }

    public boolean enforcementAllowed() {
        return latest.clean() && !Instant.EPOCH.equals(latest.checkedAt()) && !grantsPending();
    }

    private boolean grantsPending() {
        var grants = signingGrantService.getIfAvailable();
        return grants != null && grants.hasPending();
    }

    private void auditIdentity(
            IssuerDefinitionEntity identity, List<IdentityKeyViolation> violations) {
        var slots = slotRepository.findAllByIdentityIdOrderByTypeAscOrderAsc(identity.getId());
        auditSlots(identity, slots, violations);
        if (identity.getFederation().enabled()
                && slots.stream().noneMatch(slot ->
                        slot.getType() == IdentityKeySlotType.IDENTITY_STATEMENT
                                && slot.getTrustSystem() == IssuerTrustSystem.OIDF
                                && slot.getKeyId() != null)) {
            violations.add(new IdentityKeyViolation(
                    "OIDF_IDENTITY_SLOT_MISSING", identity.getSlug(),
                    "federation names a key without an OIDF identity-statement slot"));
        }
    }

    private void auditSlots(
            IssuerDefinitionEntity identity,
            List<IdentityKeySlotEntity> slots,
            List<IdentityKeyViolation> violations) {
        for (var slot : slots) {
            if (slot.getKeyId() != null) {
                var key = keyRepository.findById(slot.getKeyId()).orElse(null);
                if (key == null) {
                    violations.add(new IdentityKeyViolation(
                            "SLOT_KEY_MISSING", identity.getSlug(),
                            "slot references a missing signing key"));
                } else {
                    if (!Objects.equals(identity.getTenantId(), key.getTenantId())) {
                        violations.add(new IdentityKeyViolation(
                                "SLOT_KEY_SCOPE", identity.getSlug(),
                                "slot references a key owned by another organisation"));
                    }
                    if (slot.getProviderId() != null
                            && !Objects.equals(slot.getProviderId(), key.getProviderId())) {
                        violations.add(new IdentityKeyViolation(
                                "SLOT_PROVIDER_MISMATCH", identity.getSlug(),
                                "slot provider does not hold its signing key"));
                    }
                    var activeVersion = key.getActiveVersionId() == null
                            ? null
                            : versionRepository.findById(key.getActiveVersionId()).orElse(null);
                    if (activeVersion == null) {
                        violations.add(new IdentityKeyViolation(
                                "SLOT_ACTIVE_VERSION_MISSING", identity.getSlug(),
                                "slot key has no active provider version"));
                    } else if (!Objects.equals(key.getId(), activeVersion.getKeyId())) {
                        violations.add(new IdentityKeyViolation(
                                "SLOT_ACTIVE_VERSION_MISMATCH", identity.getSlug(),
                                "slot key points at a version from another signing key"));
                    } else if (!SigningKeyService.ACTIVE.equals(activeVersion.getStatus())) {
                        violations.add(new IdentityKeyViolation(
                                "SLOT_ACTIVE_VERSION_STATUS", identity.getSlug(),
                                "slot key points at a provider version that is not active"));
                    } else if (slot.getType() != IdentityKeySlotType.DECRYPTION
                            && !activeVersion.getUsages().contains(SigningKeyUsage.SIGN)) {
                        violations.add(new IdentityKeyViolation(
                                "SLOT_SIGN_CAPABILITY_MISSING", identity.getSlug(),
                                "signing slot key has no SIGN usage"));
                    } else if (slot.getType() == IdentityKeySlotType.DECRYPTION
                            && !supportsDecryption(identity, key, activeVersion)) {
                        violations.add(new IdentityKeyViolation(
                                "DECRYPTION_CAPABILITY_MISSING", identity.getSlug(),
                                "decryption slot key cannot derive or unwrap a content key"));
                    }
                }
            }
            if (slot.getProviderId() != null) {
                var provider = providerRepository.findById(slot.getProviderId()).orElse(null);
                if (provider == null) {
                    violations.add(new IdentityKeyViolation(
                            "SLOT_PROVIDER_MISSING", identity.getSlug(),
                            "slot references a missing signing provider"));
                } else if (provider.getTenantId() != null
                        && !Objects.equals(identity.getTenantId(), provider.getTenantId())) {
                    violations.add(new IdentityKeyViolation(
                            "SLOT_PROVIDER_SCOPE", identity.getSlug(),
                            "slot references a provider owned by another organisation"));
                }
            }
            if (slot.getCertificateId() != null) {
                var certificate = certificateRepository.findById(slot.getCertificateId()).orElse(null);
                var version = certificate == null
                        ? null : versionRepository.findById(certificate.getKeyVersionId()).orElse(null);
                if (certificate == null || version == null) {
                    violations.add(new IdentityKeyViolation(
                            "SLOT_CERTIFICATE_MISSING", identity.getSlug(),
                            "slot references a missing signing certificate"));
                } else if (!Objects.equals(slot.getKeyId(), version.getKeyId())) {
                    violations.add(new IdentityKeyViolation(
                            "SLOT_CERTIFICATE_MISMATCH", identity.getSlug(),
                            "slot certificate belongs to another signing key"));
                } else {
                    var expectedProfile = slot.getType().certificateProfile(slot.getTrustSystem());
                    if (certificate.getProfile() != expectedProfile
                            || (certificate.getProfile() == SigningCertificateProfile.ACCESS
                                && certificate.getTrustSystem() != slot.getTrustSystem())) {
                        violations.add(new IdentityKeyViolation(
                                "SLOT_CERTIFICATE_PROFILE", identity.getSlug(),
                                "slot certificate profile does not match its role"));
                    }
                }
            } else if (slot.getType().requiresCertificate(slot.getTrustSystem())
                    && slot.getKeyId() != null) {
                var key = keyRepository.findById(slot.getKeyId()).orElse(null);
                var activeVersion = key == null || key.getActiveVersionId() == null
                        ? null : versionRepository.findById(key.getActiveVersionId()).orElse(null);
                var profile = slot.getType().certificateProfile(slot.getTrustSystem());
                var hasCertificate = activeVersion != null
                        && !certificateRepository
                                .forProfile(
                                        activeVersion.getId(), profile, slot.getTrustSystem())
                                .isEmpty();
                if (!hasCertificate) {
                    violations.add(new IdentityKeyViolation(
                            "SLOT_CERTIFICATE_MISSING", identity.getSlug(),
                            "Certificate-required slot has no active certificate for its role"));
                }
            }
        }
    }

    private boolean supportsDecryption(
            IssuerDefinitionEntity identity,
            org.heidiverse.heidi.entity.model.entity.SigningKeyEntity key,
            org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity version) {
        try {
            var capabilities = signingProviderService.runtimeConfiguration(
                    identity.getTenantId(), key.getProviderId());
            return capabilities.contentKeyAlgorithms().stream()
                    .filter(algorithm -> IssuerService.compatibleContentKeyAlgorithm(
                            version.getPublicJwk(), algorithm))
                    .anyMatch(algorithm -> version.getUsages().contains(
                            algorithm.startsWith("RSA")
                                    ? SigningKeyUsage.UNWRAP
                                    : SigningKeyUsage.KEY_AGREEMENT));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void auditCredential(
            CredentialSchemeEntity credential, List<IdentityKeyViolation> violations) {
        if (credential.getIssuerDefinition() == null) {
            violations.add(new IdentityKeyViolation(
                    "CREDENTIAL_IDENTITY_MISSING", String.valueOf(credential.getId()),
                    "credential schema has no issuer identity"));
            return;
        }
        var slots = slotRepository.findAllByIdentityIdOrderByTypeAscOrderAsc(
                credential.getIssuerDefinition().getId());
        var configured = credential.getSigningKeyIds();
        if (configured != null) {
            configured.forEach((trustSystem, keyId) -> {
                if (keyId == null || keyId.isBlank()) return;
                if (!keyIdHasSlot(credential.getIssuerDefinition(), slots, trustSystem, keyId)) {
                    violations.add(new IdentityKeyViolation(
                            "SCHEMA_KEY_OUTSIDE_IDENTITY", String.valueOf(credential.getId()),
                            "credential override " + keyId + " is outside its identity slots"));
                }
            });
        }
        var statusListId = credential.getStatusListId();
        if (statusListId != null) {
            var statusList = statusListRepository.findById(statusListId).orElse(null);
            if (statusList == null) {
                violations.add(new IdentityKeyViolation(
                        "STATUS_LIST_MISSING", String.valueOf(credential.getId()),
                        "credential schema references a missing status list"));
            } else if (credential.getIssuerDefinition() != null
                    && !hasSlot(slots, IdentityKeySlotType.STATUS_LIST, statusList.getSigningKeyId())) {
                violations.add(new IdentityKeyViolation(
                        "STATUS_LIST_SLOT_MISSING", String.valueOf(credential.getId()),
                        "status list key is not in the issuer identity slots"));
            }
        }
    }

    private static boolean hasSlot(
            List<IdentityKeySlotEntity> slots, IdentityKeySlotType type, UUID keyId) {
        return keyId != null && slots.stream().anyMatch(slot ->
                slot.getType() == type && Objects.equals(slot.getKeyId(), keyId));
    }

    private static boolean hasKeySlot(
            List<IdentityKeySlotEntity> slots,
            IdentityKeySlotType type,
            IssuerTrustSystem trustSystem,
            UUID keyId) {
        return keyId != null && slots.stream().anyMatch(slot ->
                slot.getType() == type
                        && Objects.equals(slot.getTrustSystem(), trustSystem)
                        && Objects.equals(slot.getKeyId(), keyId));
    }

    private boolean keyIdHasSlot(
            IssuerDefinitionEntity identity,
            List<IdentityKeySlotEntity> slots,
            Object trustSystem,
            String keyId) {
        return slots.stream()
                .filter(slot -> slot.getType() == IdentityKeySlotType.CREDENTIAL_SIGNING)
                .filter(slot -> Objects.equals(slot.getTrustSystem(), trustSystem))
                .map(IdentityKeySlotEntity::getKeyId)
                .filter(Objects::nonNull)
                .anyMatch(id -> keyRepository.findById(id)
                                .map(key -> keyId.equals(key.getLogicalKeyId()))
                                .orElse(false));
    }
}
