// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import org.heidiverse.heidi.entity.data.repository.IssuerDefinitionRepository;
import org.heidiverse.heidi.entity.data.repository.TenantRepository;
import org.heidiverse.heidi.entity.data.service.TrustRegistryDataService;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.entity.TenantEntity;
import org.heidiverse.heidi.entity.model.entity.TrustRegistryEntity;
import org.heidiverse.heidi.entity.model.exceptions.TenantNotFoundException;
import org.heidiverse.heidi.shared.signing.SigningKeyCreator;
import org.heidiverse.heidi.shared.signing.SigningKeyCapabilities;
import org.heidiverse.heidi.shared.signing.SigningKeyRef;
import org.heidiverse.heidi.shared.signing.SigningKeyProvider;
import com.nimbusds.jose.jwk.JWK;
import org.heidiverse.heidi.entity.model.tenant.TenantFeatures;
import org.heidiverse.heidi.entity.model.tenant.TenantRequest;
import org.heidiverse.heidi.entity.model.tenant.TrustRegistryType;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Base64;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final IssuerDefinitionRepository issuerDefinitionRepository;
    private final RPRegistrarService rpRegistrarService;
    private final CertificateService certificateService;
    private final TrustRegistryDataService trustRegistryDataService;
    private final SigningProviderService signingProviderService;
    private final SigningKeyService signingKeyService;
    private final String defaultLanguage;

    public TenantService(
            TenantRepository tenantRepository,
            IssuerDefinitionRepository issuerDefinitionRepository,
            RPRegistrarService rpRegistrarService,
            CertificateService certificateService,
            TrustRegistryDataService trustRegistryDataService,
            SigningProviderService signingProviderService) {
        this(tenantRepository, issuerDefinitionRepository, rpRegistrarService, certificateService,
                trustRegistryDataService, signingProviderService, null, TenantLanguages.FALLBACK);
    }

    public TenantService(
            TenantRepository tenantRepository,
            IssuerDefinitionRepository issuerDefinitionRepository,
            RPRegistrarService rpRegistrarService,
            CertificateService certificateService,
            TrustRegistryDataService trustRegistryDataService,
            SigningProviderService signingProviderService,
            SigningKeyService signingKeyService) {
        this(tenantRepository, issuerDefinitionRepository, rpRegistrarService, certificateService,
                trustRegistryDataService, signingProviderService, signingKeyService,
                TenantLanguages.FALLBACK);
    }

    @Autowired
    public TenantService(
            TenantRepository tenantRepository,
            IssuerDefinitionRepository issuerDefinitionRepository,
            RPRegistrarService rpRegistrarService,
            CertificateService certificateService,
            TrustRegistryDataService trustRegistryDataService,
            SigningProviderService signingProviderService,
            SigningKeyService signingKeyService,
            @Value("${heidi.platform.localization.default-language:en}") String defaultLanguage) {
        this.tenantRepository = tenantRepository;
        this.issuerDefinitionRepository = issuerDefinitionRepository;
        this.rpRegistrarService = rpRegistrarService;
        this.certificateService = certificateService;
        this.trustRegistryDataService = trustRegistryDataService;
        this.signingProviderService = signingProviderService;
        this.signingKeyService = signingKeyService;
        this.defaultLanguage = TenantLanguages.tag(defaultLanguage);
    }

    @Transactional
    public Optional<TenantEntity> getTenant(String tenantId) {
        return tenantRepository.findByTenantIdAndDeletedFalse(tenantId);
    }

    /**
     * Reads an tenant whether or not it is deactivated. Administration screens need
     * to show and reactivate deactivated tenants, so they read through this rather
     * than {@link #getTenant(String)}, which hides them.
     */
    public Optional<TenantEntity> getTenantIncludingDeleted(String tenantId) {
        return tenantRepository.findById(tenantId);
    }

    /** All tenants, deactivated ones included. */
    public List<TenantEntity> getTenants() {
        return tenantRepository.findAllByOrderByTenantIdAsc();
    }

    @Transactional
    public void upsertTenant(String tenantId, TenantRequest request) {
        var existing = tenantRepository.findById(tenantId);
        var tenant = existing.orElseGet(TenantEntity::new);
        tenant.setTenantId(tenantId);
        if (existing.isEmpty()) {
            tenant.setTranslations(List.of(defaultLanguage));
            tenant.setDefaultLanguage(defaultLanguage);
        }
        if (request.getDisplayName() != null) {
            tenant.setDisplayName(request.getDisplayName());
        }
        if (request.getThumbnail() != null) {
            tenant.setThumbnail(request.getThumbnail());
        }
        updateLanguages(tenant, request);
        if (request.getIssuerIds() != null) {
            List<IssuerDefinitionEntity> issuers =
                    issuerDefinitionRepository.findAllById(request.getIssuerIds());
            if (issuers.size() != request.getIssuerIds().size()) {
                throw new IllegalArgumentException("One or more identities do not exist.");
            }
            if (issuers.stream().anyMatch(
                    identity -> identity.getTenantId() != null
                            && !identity.getTenantId().equals(tenantId))) {
                throw new SecurityException(
                        "An organisation cannot use another organisation's identity.");
            }
            tenant.setIssuers(issuers);
        }
        if (request.isClearClientConfiguration()) {
            tenant.setClientConfiguration(null);
        } else if (request.getClientConfiguration() != null) {
            if (!request.getClientConfiguration().isObject()) {
                throw new IllegalArgumentException("clientConfiguration must be a JSON object");
            }
            tenant.setClientConfiguration(request.getClientConfiguration().deepCopy());
        }
        tenant.setDeleted(false);
        tenantRepository.save(tenant);
    }

    private static void updateLanguages(TenantEntity tenant, TenantRequest request) {
        var languages = request.getTranslations();
        var fallback = request.getDefaultLanguage();

        if (languages == null && fallback == null) return;

        if (languages != null) {
            languages = TenantLanguages.normalize(languages, tenant.getDefaultLanguage());
            tenant.setTranslations(languages);
        } else {
            languages = tenant.getTranslations();
        }

        if (fallback == null) {
            fallback = languages.contains(tenant.getDefaultLanguage())
                    ? tenant.getDefaultLanguage()
                    : languages.getFirst();
        }
        tenant.setDefaultLanguage(TenantLanguages.fallback(languages, fallback));
    }

    @Transactional
    public void addIdentity(String tenantId, int identityId) {
        var tenant = tenantRepository.findByTenantIdAndDeletedFalse(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        var identities = new java.util.ArrayList<>(tenant.getIssuers());
        var identity = issuerDefinitionRepository.findByIdAndDeletedFalse(identityId)
                .orElseThrow(() -> new IllegalArgumentException("Identity not found: " + identityId));
        identities.add(identity);
        tenant.setIssuers(identities);
        tenantRepository.save(tenant);
    }

    @Transactional
    public void removeIdentity(String tenantId, int identityId) {
        var tenant = tenantRepository.findByTenantIdAndDeletedFalse(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        var identities = new java.util.ArrayList<>(tenant.getIssuers());
        identities.removeIf(identity -> identity.getId().equals(identityId));
        tenant.setIssuers(identities);
        tenantRepository.save(tenant);
    }

    public TenantFeatures getTenantFeatures(String tenantId)
            throws TenantNotFoundException {
        var entity =
                tenantRepository
                        .findByTenantIdAndDeletedFalse(tenantId)
                        .orElseThrow(() -> new TenantNotFoundException(tenantId));
        if (entity.getFeatures() != null) {
            return entity.getFeatures();
        }
        return TenantFeatures.allEnabled();
    }

    @Transactional
    public void updateTenantFeatures(String tenantId, TenantFeatures features)
            throws TenantNotFoundException {
        var entity =
                tenantRepository
                        .findByTenantIdAndDeletedFalse(tenantId)
                        .orElseThrow(() -> new TenantNotFoundException(tenantId));
        entity.setFeatures(features);
        tenantRepository.save(entity);
    }

    @Transactional
    public void deleteTenant(String tenantId) {
        var tenant =
                tenantRepository
                        .findById(tenantId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Tenant with tenantId "
                                                        + tenantId
                                                        + " does not exist."));
        tenant.setDeleted(true);
        tenantRepository.save(tenant);
    }

    @Transactional
    public void insertTenantIntoGermanTrustRegistry(String tenantId) throws Exception {
        var tenant =
                tenantRepository
                        .findByTenantIdAndDeletedFalse(tenantId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Tenant with tenantId "
                                                        + tenantId
                                                        + " does not exist."));

        // Check if the tenant is already registered in the Relying Party Registrar
        var rpId = findRpId(tenantId);
        var currentTrustRegistryEntities = tenant.getTrustRegistries();
        var currentTrustRegistries =
                currentTrustRegistryEntities.stream()
                        .map(TrustRegistryEntity::getTrustRegistry)
                        .toList();

        if (rpId.isEmpty() && !currentTrustRegistries.contains(TrustRegistryType.DE)) {
            // Register the tenant in the Relying Party Registrar
            UUID newRpId = rpRegistrarService.addNewRelyingParty(tenantId);
            tenant.setRegistrarRpId(newRpId);
            tenantRepository.save(tenant);

            // Generate a public key and register verifier certificate at Relying Party Registry
            var newEntity = generateAndSaveCertificateForVerifier(tenantId, newRpId);

            // Add trust registry to tenant
            final var germanTrustRegistryEntity =
                    trustRegistryDataService
                            .findByTrustRegistry(TrustRegistryType.DE)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Trust Registry "
                                                            + TrustRegistryType.DE
                                                            + " does not exist in DB."));

            // Add the trust registry to the tenant
            newEntity.addTrustRegistry(germanTrustRegistryEntity);
            tenantRepository.save(newEntity);
        }
    }

    @Transactional
    public void updateTenantCertificateInGermanTrustRegistry(String tenantId)
            throws Exception {
        var tenant =
                tenantRepository
                        .findByTenantIdAndDeletedFalse(tenantId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Tenant with tenantId "
                                                        + tenantId
                                                        + " does not exist."));

        // Check if the tenant is already registered in the Relying Party Registrar
        var rpId = findRpId(tenantId);
        var currentTrustRegistryEntities = tenant.getTrustRegistries();
        var currentTrustRegistries =
                currentTrustRegistryEntities.stream()
                        .map(TrustRegistryEntity::getTrustRegistry)
                        .toList();

        if (rpId.isEmpty() && !currentTrustRegistries.contains(TrustRegistryType.DE)) {
            throw new TenantNotFoundException(
                    "tenantId (" + tenantId + ") not found in german trust registry.");
        }

        generateAndSaveCertificateForVerifier(
                tenantId, rpId.orElseThrow(() -> new TenantNotFoundException(tenantId)));
    }

    @Transactional
    public TenantEntity generateAndSaveCertificateForVerifier(String tenantId, UUID rpId)
            throws Exception {
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(
                        "Tenant not found for tenantId: " + tenantId));
        SigningKeyProvider signingProvider;
        SigningKeyRef ref;
        Integer providerId = tenant.getVerifierProviderId();
        if (signingKeyService != null && tenant.getVerifierKeyId() != null
                && tenant.getVerifierKeyUri() != null && !tenant.getVerifierKeyUri().isBlank()
                && providerId != null) {
            var version = signingKeyService.activeVersion(tenantId, tenant.getVerifierKeyId());
            signingProvider = signingProviderService.provider(tenantId, providerId);
            ref = signingProvider.resolve(version.getKeyUri());
        } else if (tenant.getVerifierKeyUri() != null && !tenant.getVerifierKeyUri().isBlank()
                && providerId != null) {
            // Certificate refresh reuses the stable verifier key. Key rotation is a separate
            // operation and will deliberately create a new key reference later.
            signingProvider = signingProviderService.provider(tenantId, providerId);
            ref = signingProvider.resolve(tenant.getVerifierKeyUri());
        } else {
            var provider = signingProviderService.defaultProvider(tenantId);
            if (provider == null) {
                throw new IllegalStateException(
                        "Tenant has no default signing provider: " + tenantId);
            }
            providerId = provider.getId();
            if (signingKeyService != null) {
                var provisioned = signingKeyService.create(
                        tenantId, "verifier-" + tenantId, "ES256", providerId, null);
                ref = new SigningKeyRef(
                        provisioned.keyUri(), provisioned.publicJwk(), provisioned.algorithm());
                tenant.setVerifierKeyId(provisioned.keyId());
                tenant.setVerifierKeyVersionId(provisioned.keyVersionId());
            } else {
                signingProvider = signingProviderService.provider(tenantId, providerId);
                if (!(signingProvider instanceof SigningKeyCreator creator)
                        || !SigningKeyCapabilities.of(signingProvider).canCreate()) {
                    throw new IllegalStateException("Verifier signing provider cannot create keys");
                }
                ref = creator.createKey("verifier-" + tenantId, "ES256");
            }
        }
        if (!"ES256".equals(ref.algorithm())) {
            throw new IllegalStateException(
                    "Verifier signing key uses algorithm '" + ref.algorithm() + "', expected 'ES256'");
        }
        var publicKey = JWK.parse(ref.publicJwk()).toECKey().toPublicKey();
        var certificate = certificateService.generateAccessCertificate(rpId, publicKey);
        var certificateChain = Base64.getEncoder().encodeToString(certificate.getEncoded());
        tenant.setVerifierKeyUri(ref.uri());
        tenant.setVerifierProviderId(providerId);
        tenant.setVerifierPublicJwk(ref.publicJwk());
        tenant.setVerifierCertificateChain(certificateChain);
        if (signingKeyService != null && tenant.getVerifierKeyId() != null
                && tenant.getVerifierKeyVersionId() != null) {
            signingKeyService.setCertificateChain(
                    tenantId, tenant.getVerifierKeyId(), tenant.getVerifierKeyVersionId(),
                    List.of(certificateChain));
        }
        return tenantRepository.save(tenant);
    }

    public Optional<UUID> findRpId(String tenantId) {
        return tenantRepository
                .findByTenantIdAndDeletedFalse(tenantId)
                .map(TenantEntity::getRegistrarRpId);
    }
}
