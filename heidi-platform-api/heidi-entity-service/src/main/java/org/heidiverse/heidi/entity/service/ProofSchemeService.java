// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import jakarta.transaction.Transactional;

import org.heidiverse.heidi.coordinator.model.ProofSchemeResponse;
import org.heidiverse.heidi.coordinator.model.exceptions.DoctypeNotFoundException;
import org.heidiverse.heidi.coordinator.model.exceptions.VctNotFoundException;
import org.heidiverse.heidi.coordinator.service.DcqlQueryService;
import org.heidiverse.heidi.entity.data.repository.IssuerDefinitionRepository;
import org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService;
import org.heidiverse.heidi.entity.data.service.ProofSchemeDataService;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialScheme;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeAttribute;
import org.heidiverse.heidi.entity.model.credentialscheme.ReducedCredentialSchemeDetail;
import org.heidiverse.heidi.entity.model.entity.*;
import org.heidiverse.heidi.entity.model.exceptions.*;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileCatalog;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileFamily;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfile;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileRole;
import org.heidiverse.heidi.entity.model.proofscheme.ProofSchemeDetail;
import org.heidiverse.heidi.entity.model.proofscheme.BbsIssuerMetadata;
import org.heidiverse.heidi.entity.model.proofscheme.ProofSchemeOverview;
import org.heidiverse.heidi.entity.model.proofscheme.ProofSchemePayload;
import org.heidiverse.heidi.entity.model.proofscheme.TrustedAuthorityQuery;
import org.heidiverse.heidi.entity.model.proofscheme.VerifierClientIdScheme;
import org.heidiverse.heidi.entity.model.proofscheme.VerifierInfo;
import org.heidiverse.heidi.entity.model.tenant.TrustRegistryType;
import org.heidiverse.heidi.entity.model.user.UserRole;
import org.heidiverse.heidi.entity.service.utils.CredentialSchemeUtils;
import org.heidiverse.heidi.entity.service.utils.JwtUtils;
import org.heidiverse.heidi.entity.service.utils.ProofSchemeCredentialSchemeUtils;
import org.heidiverse.heidi.shared.signing.SigningOperationProvider;
import org.heidiverse.heidi.shared.signing.SigningOperationRequest;
import org.heidiverse.heidi.shared.signing.SigningOperationResult;
import org.kapunsdk.DcqlQuerySerializer;
import org.kapunsdk.presentation.request.model.OID4VPVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ProofSchemeService {

    private static final Logger log = LoggerFactory.getLogger(ProofSchemeService.class);
    private static final String BBS_PRESENTATION_SETUP_OPERATION =
            "w3c.bbs-data-integrity-presentation-setup";
    private static final String BBS_ISSUANCE_OPERATION =
            "w3c.bbs-data-integrity-credential-issuance";
    private final ProofSchemeDataService proofSchemeDataService;
    private final CredentialSchemeDataService credentialSchemeDataService;
    private final RPRegistrarService rpRegistrarService;
    private final IssuerDefinitionRepository issuerDefinitionRepository;
    private final SigningProviderService signingProviderService;
    private final IssuerService issuerService;
    private final IssuerOperationConfigurationService operationConfigurationService;
    private final IdentityKeyIntegrityService identityKeyIntegrityService;
    private final IdentityKeySlotService identityKeySlotService;
    private final DcqlQueryService dcqlQueryService;
    private final ObjectMapper objectMapper;
    private final SwissTrustStatementRefreshService swissTrustStatementRefreshService;
    @Autowired(required = false)
    private IssuingPkiService issuingPki;

    @Value("${heidi.platform.web-base-url}")
    private String heidiWebBaseUrl;

    @Autowired
    public ProofSchemeService(
            final ProofSchemeDataService proofSchemeDataService,
            final CredentialSchemeDataService credentialSchemeDataService,
            final RPRegistrarService rpRegistrarService,
            final IssuerDefinitionRepository issuerDefinitionRepository,
            final SigningProviderService signingProviderService,
            final IssuerService issuerService,
            final IssuerOperationConfigurationService operationConfigurationService,
            IdentityKeyIntegrityService identityKeyIntegrityService,
            IdentityKeySlotService identityKeySlotService,
            DcqlQueryService dcqlQueryService,
            ObjectMapper objectMapper,
            SwissTrustStatementRefreshService swissTrustStatementRefreshService) {
        this.proofSchemeDataService = proofSchemeDataService;
        this.credentialSchemeDataService = credentialSchemeDataService;
        this.rpRegistrarService = rpRegistrarService;
        this.issuerDefinitionRepository = issuerDefinitionRepository;
        this.signingProviderService = signingProviderService;
        this.issuerService = issuerService;
        this.operationConfigurationService = operationConfigurationService;
        this.identityKeyIntegrityService = identityKeyIntegrityService;
        this.identityKeySlotService = identityKeySlotService;
        this.dcqlQueryService = dcqlQueryService;
        this.objectMapper = objectMapper;
        this.swissTrustStatementRefreshService = swissTrustStatementRefreshService;
    }

    public ProofSchemeService(
            final ProofSchemeDataService proofSchemeDataService,
            final CredentialSchemeDataService credentialSchemeDataService,
            final RPRegistrarService rpRegistrarService,
            final IssuerDefinitionRepository issuerDefinitionRepository,
            final SigningProviderService signingProviderService,
            final IssuerService issuerService,
            final IssuerOperationConfigurationService operationConfigurationService) {
        this(proofSchemeDataService, credentialSchemeDataService, rpRegistrarService,
                issuerDefinitionRepository, signingProviderService, issuerService,
                operationConfigurationService, null, null, null, null, null);
    }

    @Transactional
    public ProofSchemeOverview findAll(
            boolean requireAuthentication, List<String> credentialIdentifiers) {
        List<ProofSchemeEntity> proofSchemeEntities;
        if (requireAuthentication) {
            String tenantId = JwtUtils.getUserProfile().tenantId();
            boolean isSuperAdmin =
                    JwtUtils.getUserProfile().permissions().contains(UserRole.SUPER_ADMIN);
            proofSchemeEntities =
                    isSuperAdmin
                            ? proofSchemeDataService.findAllByArchivedFalse()
                            : proofSchemeDataService.findAllByTenantIdAndArchivedFalse(tenantId);
        } else {
            proofSchemeEntities = proofSchemeDataService.findAllByArchivedFalse();
        }

        // Post-filtering based on credential IDs if provided
        if (credentialIdentifiers != null && !credentialIdentifiers.isEmpty()) {
            proofSchemeEntities =
                    proofSchemeEntities.stream()
                            .filter(
                                    proofScheme -> {
                                        var credentialEntities =
                                                proofSchemeDataService.findAllByProofScheme(
                                                        proofScheme);
                                        return credentialEntities.stream()
                                                .map(
                                                        e ->
                                                                e.getCredentialSchemeEntity()
                                                                        .getCredentialIdentifier())
                                                .allMatch(credentialIdentifiers::contains);
                                    })
                            .toList();
        }

        return new ProofSchemeOverview(
                proofSchemeEntities.stream().map(this::toProofScheme).toList());
    }

    @Transactional
    public ProofSchemeDetail findById(final UUID id, boolean requireAuthentication) {
        final var proofSchemeEntity =
                proofSchemeDataService
                        .findByIdAndArchivedFalse(id)
                        .orElseThrow(() -> new ProofSchemeNotFoundException(id));

        // Validate access
        if (requireAuthentication) {
            validateTenantIdAccess(proofSchemeEntity);
        }

        return toProofScheme(proofSchemeEntity);
    }

    public void archiveProofScheme(final UUID id) {
        final var proofSchemeEntity =
                proofSchemeDataService
                        .findByIdAndArchivedFalse(id)
                        .orElseThrow(() -> new ProofSchemeNotFoundException(id));

        // Validate access
        validateTenantIdAccess(proofSchemeEntity);

        proofSchemeEntity.setUpdatedAt(Instant.now());
        proofSchemeEntity.setArchived(true);
        proofSchemeDataService.insertProofScheme(proofSchemeEntity);
        if (issuerService != null) issuerService.reconcileSigningGrants();
    }

    @Transactional
    public void restoreProofScheme(final UUID id) {
        final var proofSchemeEntity =
                proofSchemeDataService
                        .findById(id)
                        .orElseThrow(() -> new ProofSchemeNotFoundException(id));

        // Validate access
        validateTenantIdAccess(proofSchemeEntity);

        proofSchemeEntity.setUpdatedAt(Instant.now());
        proofSchemeEntity.setArchived(false);
        proofSchemeDataService.insertProofScheme(proofSchemeEntity);
        if (issuerService != null) issuerService.reconcileSigningGrants();
    }

    @Transactional
    public ProofSchemeDetail updateProofScheme(final UUID id, final ProofSchemePayload payload)
            throws ProofSchemeNotFoundException,
                    SchemaNotFoundException,
                    InvalidAttributeException,
                    TenantNotFoundException {

        final var proofSchemeEntity =
                proofSchemeDataService
                        .findByIdAndArchivedFalse(id)
                        .orElseThrow(() -> new ProofSchemeNotFoundException(id));

        // Validate access
        validateTenantIdAccess(proofSchemeEntity);
        validateCredentialSchemes(payload, proofSchemeEntity.getVerifierTrustSystem());

        proofSchemeEntity.setTitle(payload.title());
        proofSchemeEntity.setPurpose(payload.purpose());
        proofSchemeEntity.setUpdatedAt(Instant.now());
        proofSchemeEntity.setValidationLogic(payload.validationLogic());
        proofSchemeEntity.setValidationMode(payload.effectiveValidationMode());
        proofSchemeEntity.setProofSchemeCredentialSchemeEntities(new ArrayList<>());
        proofSchemeEntity.setRedirectUri(payload.redirectUri());
        applyVerifierConfiguration(proofSchemeEntity, payload);
        proofSchemeEntity.setPresentationProfileId(payload.presentationProfileId());
        var profile = EcosystemProfileCatalog.require(
                proofSchemeEntity.getPresentationProfileId(), EcosystemProfileRole.PRESENTATION);
        proofSchemeEntity.setVerifierTrustSystem(
                resolveTrustSystem(profile, requestedTrustSystem(payload, proofSchemeEntity)));
        validateProfileOverrides(profile, payload);
        validatePresentationProfile(proofSchemeEntity);

        var updated = proofSchemeDataService.insertProofScheme(proofSchemeEntity);
        final var credentialSchemes = payload.credentialSchemes();
        final var credentialSchemeDetails =
                upsertProofSchemeCredentialSchemeAndRequestedAttributes(credentialSchemes, updated);

        final var credentialSchemeIds =
                credentialSchemes.stream().map(CredentialScheme::id).toList();
        deleteOldProofSchemeCredentialSchemeAndRequestedAttributes(
                credentialSchemeIds, proofSchemeEntity);
        requestSwissVerificationQueryStatement(updated);
        if (issuerService != null) issuerService.reconcileSigningGrants();

        return new ProofSchemeDetail(
                updated.getUuid(),
                updated.getTitle(),
                updated.getPurpose(),
                updated.getPresentationProfileId(),
                updated.getValidationLogic(),
                updated.getValidationMode(),
                updated.getRedirectUri(),
                updated.getTenantId(),
                identityDefinition(effectiveIdentity(updated)),
                updated.getVerifierIdentity() != null,
                updated.getVerifierSigningKeyId(),
                providerId(updated),
                effectiveTrustSystem(updated),
                effectiveClientIdScheme(updated),
                updated.getVerifierClientIdScheme() != null,
                effectiveRegistrationCertificate(updated),
                effectiveSwissIdentityStatement(updated),
                updated.getSwissVerificationQueryStatement(),
                updated.isSwissVerificationQueryEnabled(),
                updated.isAlwaysIncludeDcqlQuery(),
                updated.getSwissProtectedVerificationStatements(),
                effectiveEudiVerificationTrustAnchors(updated),
                effectiveSwissVerificationTrustAnchor(updated),
                effectiveSwissTrustRegistryBaseUrl(updated),
                updated.getTrustedAuthorities(),
                updated.getVerifierInfos(),
                updated.getCreatedAt(),
                updated.getUpdatedAt(),
                credentialSchemeDetails);
    }

    @Transactional
    public ProofSchemeDetail insertProofScheme(
            final ProofSchemePayload payload, final String tenantId)
            throws ProofSchemeNotFoundException,
                    SchemaNotFoundException,
                    InvalidAttributeException,
                    TenantNotFoundException {
        validateCredentialSchemes(payload);
        UUID proofSchemeUUID = UUID.randomUUID();
        String redirectUri =
                payload.redirectUri() != null && !payload.redirectUri().isBlank()
                        ? payload.redirectUri()
                        : String.format(
                                "%s/public/verifier/detail/%s", heidiWebBaseUrl, proofSchemeUUID);

        final var proofSchemeEntity = new ProofSchemeEntity();
        proofSchemeEntity.setTitle(payload.title());
        proofSchemeEntity.setPurpose(payload.purpose());
        proofSchemeEntity.setValidationLogic(payload.validationLogic());
        proofSchemeEntity.setValidationMode(payload.effectiveValidationMode());
        proofSchemeEntity.setCreatedAt(Instant.now());
        proofSchemeEntity.setUuid(proofSchemeUUID);
        proofSchemeEntity.setRedirectUri(redirectUri);
        proofSchemeEntity.setTenantId(tenantId);
        applyVerifierConfiguration(proofSchemeEntity, payload);
        proofSchemeEntity.setPresentationProfileId(payload.presentationProfileId());
        var profile = EcosystemProfileCatalog.require(
                proofSchemeEntity.getPresentationProfileId(), EcosystemProfileRole.PRESENTATION);
        proofSchemeEntity.setVerifierTrustSystem(
                resolveTrustSystem(profile, payload.verifierTrustSystem()));
        validateProfileOverrides(profile, payload);
        validatePresentationProfile(proofSchemeEntity);

        var inserted = proofSchemeDataService.insertProofScheme(proofSchemeEntity);
        final var credentialSchemes = payload.credentialSchemes();
        final var credentialSchemeDetails =
                upsertProofSchemeCredentialSchemeAndRequestedAttributes(
                        credentialSchemes, inserted);
        requestSwissVerificationQueryStatement(inserted);
        if (issuerService != null) issuerService.reconcileSigningGrants();

        return new ProofSchemeDetail(
                inserted.getUuid(),
                inserted.getTitle(),
                inserted.getPurpose(),
                inserted.getPresentationProfileId(),
                inserted.getValidationLogic(),
                inserted.getValidationMode(),
                inserted.getRedirectUri(),
                tenantId,
                identityDefinition(effectiveIdentity(inserted)),
                inserted.getVerifierIdentity() != null,
                inserted.getVerifierSigningKeyId(),
                providerId(inserted),
                effectiveTrustSystem(inserted),
                effectiveClientIdScheme(inserted),
                inserted.getVerifierClientIdScheme() != null,
                effectiveRegistrationCertificate(inserted),
                effectiveSwissIdentityStatement(inserted),
                inserted.getSwissVerificationQueryStatement(),
                inserted.isSwissVerificationQueryEnabled(),
                inserted.isAlwaysIncludeDcqlQuery(),
                inserted.getSwissProtectedVerificationStatements(),
                effectiveEudiVerificationTrustAnchors(inserted),
                effectiveSwissVerificationTrustAnchor(inserted),
                effectiveSwissTrustRegistryBaseUrl(inserted),
                inserted.getTrustedAuthorities(),
                inserted.getVerifierInfos(),
                inserted.getCreatedAt(),
                inserted.getUpdatedAt(),
                credentialSchemeDetails);
    }

    @Transactional
    public void publishProofSchemeToTrustRegistry(
            final UUID proofschemeId, final TrustRegistryType trustRegistry)
            throws TenantNotFoundException, ProofSchemeNotFoundException {
        final var proofSchemeEntity =
                proofSchemeDataService
                        .findByIdAndArchivedFalse(proofschemeId)
                        .orElseThrow(() -> new ProofSchemeNotFoundException(proofschemeId));

        validateTenantIdAccess(proofSchemeEntity);
        validatePresentationReadiness(proofSchemeEntity, trustRegistry);

        final var proofSchemeDetail = toProofScheme(proofSchemeEntity);

        switch (trustRegistry) {
            case DE:
                String registrationCertificate =
                        rpRegistrarService.addNewRegistrationCertificate(proofSchemeDetail);

                proofSchemeEntity.setRegistrationCertificate(registrationCertificate);
                proofSchemeDataService.insertProofScheme(proofSchemeEntity);
                break;

            case CH:
                requestSwissVerificationQueryStatement(proofSchemeEntity);
                log.error("CH Trust Registry proof Scheme publication not available.");
        }
    }

    private void validatePresentationReadiness(
            ProofSchemeEntity entity, TrustRegistryType trustRegistry) {
        validatePresentationProfile(entity);
        var profile = EcosystemProfileCatalog.require(
                effectivePresentationProfileId(entity), EcosystemProfileRole.PRESENTATION);
        if (trustRegistry == TrustRegistryType.DE
                && profile.family() != EcosystemProfileFamily.EUDI_WALLET) {
            throw new IllegalArgumentException(
                    "German trust registry publication requires an EUDI presentation profile");
        }
        if (profile.family() == EcosystemProfileFamily.EUDI_WALLET
                && effectiveEudiVerificationTrustAnchors(entity).isEmpty()) {
            throw new IllegalArgumentException(
                    "EUDI presentation requires verification trust anchors");
        }
        var identity = effectiveIdentity(entity);
        if (identity == null) return;

        if (identityKeySlotService != null) identityKeySlotService.requireClient(
                identity.getId(),
                org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotConsumer.VERIFIER,
                Set.of(IdentityKeySlotType.PRESENTATION_SIGNING));
        if (issuerService == null) return;

        var requestedTrust = profile.policy().trustSystem() == null
                ? IssuerTrustSystem.Default : profile.policy().trustSystem();
        issuerService.getPresentationSigningConfiguration(
                        identity.getSlug(), requestedTrust,
                        entity.getVerifierSigningKeyId(), effectivePresentationProfileId(entity))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Verifier identity has no signing configuration for profile "
                                + profile.id()));
    }

    private void validateTenantIdAccess(ProofSchemeEntity proofSchemeEntity) {
        String tenantId = JwtUtils.getUserProfile().tenantId();
        boolean isSuperAdmin =
                JwtUtils.getUserProfile().permissions().contains(UserRole.SUPER_ADMIN);

        if (!isSuperAdmin
                && proofSchemeEntity.getTenantId() != null
                && !proofSchemeEntity.getTenantId().equals(tenantId)) {
            throw new UnauthorizedAccessException("Access denied: Tenant ID mismatch.");
        }
    }

    private void validateCredentialSchemes(final ProofSchemePayload payload) {
        validateCredentialSchemes(payload, null);
    }

    private void validateCredentialSchemes(
            final ProofSchemePayload payload, final IssuerTrustSystem inheritedTrustSystem) {
        var profile = EcosystemProfileCatalog.require(
                payload.presentationProfileId(), EcosystemProfileRole.PRESENTATION);
        resolveTrustSystem(profile, requestedTrustSystem(payload, inheritedTrustSystem));
        validateProfileOverrides(profile, payload);
        if (payload.credentialSchemes() == null || payload.credentialSchemes().isEmpty()) {
            throw new IllegalArgumentException("At least one credential schema is required");
        }
    }

    private IssuerTrustSystem resolveTrustSystem(
            EcosystemProfile profile, IssuerTrustSystem requested) {
        var profileTrust = profile.policy().trustSystem();
        if (profile.family() == EcosystemProfileFamily.CUSTOM) {
            if (requested != null && requested != IssuerTrustSystem.Default
                    && requested != IssuerTrustSystem.Custom) {
                throw new IllegalArgumentException(
                        "Custom presentation profiles require the Custom trust system");
            }
            return IssuerTrustSystem.Custom;
        }
        if (requested != null && requested != IssuerTrustSystem.Default
                && profileTrust != null && requested != profileTrust) {
            throw new IllegalArgumentException(
                "Trust system is incompatible with presentation profile " + profile.id());
        }
        return profileTrust != null ? profileTrust : requested;
    }

    private IssuerTrustSystem requestedTrustSystem(
            ProofSchemePayload payload, ProofSchemeEntity entity) {
        return requestedTrustSystem(payload, entity.getVerifierTrustSystem());
    }

    private IssuerTrustSystem requestedTrustSystem(
            ProofSchemePayload payload, IssuerTrustSystem inheritedTrustSystem) {
        return payload.verifierTrustSystem() != null
                ? payload.verifierTrustSystem() : inheritedTrustSystem;
    }

    private void validateProfileOverrides(
            EcosystemProfile profile, ProofSchemePayload payload) {
        var expectedClientIdScheme = profile.policy().clientIdScheme();
        if (payload.verifierClientIdScheme() != null
                && expectedClientIdScheme != null
                && payload.verifierClientIdScheme() != expectedClientIdScheme) {
            throw new IllegalArgumentException(
                    "Verifier client ID scheme is incompatible with presentation profile "
                            + profile.id());
        }

        boolean hasSwissConfiguration = payload.swissIdentityStatement() != null
                || payload.swissVerificationQueryStatement() != null
                || (payload.swissProtectedVerificationStatements() != null
                        && !payload.swissProtectedVerificationStatements().isEmpty());
        if (hasSwissConfiguration && profile.family() != EcosystemProfileFamily.SWISS_SWIYU) {
            throw new IllegalArgumentException(
                    "Swiss verification statements require a Swiss presentation profile");
        }

        if (payload.registrationCertificate() != null
                && !payload.registrationCertificate().isBlank()
                && profile.family() != EcosystemProfileFamily.EUDI_WALLET) {
            throw new IllegalArgumentException(
                    "Registration certificates require an EUDI presentation profile");
        }
    }

    private void validatePresentationProfile(ProofSchemeEntity entity) {
        var profile = EcosystemProfileCatalog.require(
                entity.getPresentationProfileId(), EcosystemProfileRole.PRESENTATION);
        var identity = effectiveIdentity(entity);
        if (identity == null || identityKeySlotService == null) return;

        var trustSystem = entity.getVerifierTrustSystem() != null
                ? entity.getVerifierTrustSystem() : profile.policy().trustSystem();
        var slotTrustSystem = trustSystem == null ? IssuerTrustSystem.Default : trustSystem;
        var slotType = IdentityKeySlotType.PRESENTATION_SIGNING;
        var requiredSlotTrustSystem = profile.family() == EcosystemProfileFamily.OIDF
                ? IssuerTrustSystem.OIDF : slotTrustSystem;
        if (!hasSlot(identity, slotType, requiredSlotTrustSystem)
                && !(slotTrustSystem == IssuerTrustSystem.Custom
                    && hasSlot(identity, slotType, IssuerTrustSystem.Default))) {
            throw new IllegalArgumentException(
                    "Verifier identity has no signing material for profile "
                            + profile.id());
        }

    }

    private void requestSwissVerificationQueryStatement(ProofSchemeEntity entity) {
        if (dcqlQueryService == null
                || objectMapper == null
                || swissTrustStatementRefreshService == null) return;

        var profile = EcosystemProfileCatalog.require(
                entity.getPresentationProfileId(), EcosystemProfileRole.PRESENTATION);
        if (profile.family() != EcosystemProfileFamily.SWISS_SWIYU) return;

        var identity = effectiveIdentity(entity);
        var trust = identityTrustRequest(identity, IssuerTrustSystem.Switzerland).orElse(null);
        if (trust == null || !hasText(trust.swissDid())) return;

        try {
            var proofScheme = toProofScheme(entity);
            var coordinatorProofScheme = objectMapper.convertValue(
                    proofScheme, ProofSchemeResponse.class);
            var dcql = dcqlQueryService.generate(
                    coordinatorProofScheme, OID4VPVersion.DRAFT_28);
            JsonNode query = objectMapper.readTree(DcqlQuerySerializer.toJson(dcql));
            if (swissTrustStatementRefreshService.matchesVerificationQuery(
                    entity.getSwissVerificationQueryStatement(),
                    "heidi.proof." + entity.getUuid(), query)) return;
            entity.setSwissVerificationQueryStatement(null);
            proofSchemeDataService.insertProofScheme(entity);
            var statement = swissTrustStatementRefreshService.requestVerificationQueryStatement(
                    identity, trust,
                    entity.getTitle(), entity.getPurpose(),
                    "heidi.proof." + entity.getUuid(), query);
            statement.ifPresent(value -> {
                entity.setSwissVerificationQueryStatement(value);
                proofSchemeDataService.insertProofScheme(entity);
            });
        } catch (DoctypeNotFoundException | VctNotFoundException | RuntimeException exception) {
            log.warn("Could not request Swiss verification query statement for proof scheme {}",
                    entity.getUuid(), exception);
        }
    }

    private boolean hasSlot(
            IssuerDefinitionEntity identity, IdentityKeySlotType type,
            IssuerTrustSystem trustSystem) {
        return identityKeySlotService.slots(identity.getId()).stream()
                .anyMatch(slot -> slot.getType() == type && slot.getTrustSystem() == trustSystem);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String effectivePresentationProfileId(ProofSchemeEntity entity) {
        return EcosystemProfileCatalog.require(
                entity.getPresentationProfileId(), EcosystemProfileRole.PRESENTATION).id();
    }

    private List<ReducedCredentialSchemeDetail>
            upsertProofSchemeCredentialSchemeAndRequestedAttributes(
                    final List<CredentialScheme> credentialSchemes,
                    final ProofSchemeEntity proofSchemeEntity)
                    throws SchemaNotFoundException, InvalidAttributeException {

        final var credentialSchemeDetails = new ArrayList<ReducedCredentialSchemeDetail>();
        for (int credentialPosition = 0;
                credentialPosition < credentialSchemes.size();
                credentialPosition++) {
            final var scheme = credentialSchemes.get(credentialPosition);
            final var credentialSchemeEntity =
                    credentialSchemeDataService
                            .findById(scheme.id())
                            .orElseThrow(() -> new SchemaNotFoundException(scheme.id()));

            validateCredentialProfileCompatibility(proofSchemeEntity, credentialSchemeEntity);

            if (!CredentialSchemeUtils.isPublished(credentialSchemeEntity)) {
                throw new SchemaNotFoundException(scheme.id());
            }

            final var insertedProofSchemeCredentialSchemeEntity =
                    insertProofSchemeCredentialScheme(
                            proofSchemeEntity, credentialSchemeEntity, credentialPosition);

            proofSchemeDataService.deleteAllRequestedAttributesByProofSchemeCredentialSchemes(
                    List.of(insertedProofSchemeCredentialSchemeEntity));
            final var attributes =
                    insertRequestedAttributesForProofScheme(
                            credentialSchemeEntity,
                            insertedProofSchemeCredentialSchemeEntity,
                            scheme.attributes());

            final var credentialScheme =
                    new ReducedCredentialSchemeDetail(
                            credentialSchemeEntity.getUuid(),
                            credentialSchemeEntity.getCredentialIdentifier(),
                            credentialSchemeEntity.getVersion(),
                            credentialSchemeEntity.getDisplayName(),
                            ProofSchemeCredentialSchemeUtils.toIssuerDefinition(
                                    credentialSchemeEntity),
                            attributes,
                            CredentialSchemeUtils.toIssuerSettings(credentialSchemeEntity));
            credentialSchemeDetails.add(credentialScheme);
        }
        return credentialSchemeDetails;
    }

    private void validateCredentialProfileCompatibility(
            ProofSchemeEntity proofScheme, CredentialSchemeEntity credentialScheme) {
        var presentation = EcosystemProfileCatalog.require(
                proofScheme.getPresentationProfileId(), EcosystemProfileRole.PRESENTATION);
        var issuance = EcosystemProfileCatalog.require(
                credentialScheme.getIssuanceProfileId(), EcosystemProfileRole.ISSUANCE);
        var presentationTrust = presentation.policy().trustSystem() == null
                ? IssuerTrustSystem.Default : presentation.policy().trustSystem();
        var issuanceTrust = issuance.policy().trustSystem() == null
                ? IssuerTrustSystem.Default : issuance.policy().trustSystem();
        if (presentation.family() != issuance.family()
                || (presentationTrust != null && issuanceTrust != null
                        && presentationTrust != issuanceTrust)) {
            throw new IllegalArgumentException(
                    "Credential schema profile is incompatible with the proof schema profile");
        }
    }

    private void deleteOldProofSchemeCredentialSchemeAndRequestedAttributes(
            final List<UUID> credentialSchemeIds, final ProofSchemeEntity proofSchemeEntity) {
        final var allCredentialSchemesAssociatedWithProofScheme =
                proofSchemeDataService.findAllByProofScheme(proofSchemeEntity);
        final var relationsToDelete =
                allCredentialSchemesAssociatedWithProofScheme.stream()
                        .filter(
                                scheme ->
                                        !credentialSchemeIds.contains(
                                                scheme.getCredentialSchemeEntity().getUuid()))
                        .toList();
        final var relationsToDeleteIds =
                relationsToDelete.stream().map(ProofSchemeCredentialSchemeEntity::getId).toList();
        proofSchemeDataService.deleteAllRequestedAttributesByProofSchemeCredentialSchemes(
                relationsToDelete);
        proofSchemeDataService.deleteAllProofSchemeCredentialSchemeById(relationsToDeleteIds);
    }

    private ProofSchemeCredentialSchemeEntity insertProofSchemeCredentialScheme(
            final ProofSchemeEntity proofSchemeEntity,
            final CredentialSchemeEntity credentialSchemeEntity,
            final int credentialPosition) {
        final var existingRelation =
                proofSchemeDataService.findByProofSchemeAndCredentialScheme(
                        proofSchemeEntity, credentialSchemeEntity);
        ProofSchemeCredentialSchemeEntity proofSchemeCredentialSchemeEntity;
        proofSchemeCredentialSchemeEntity =
                existingRelation.orElseGet(ProofSchemeCredentialSchemeEntity::new);
        proofSchemeCredentialSchemeEntity.setCredentialSchemeEntity(credentialSchemeEntity);
        proofSchemeCredentialSchemeEntity.setProofSchemeEntity(proofSchemeEntity);
        proofSchemeCredentialSchemeEntity.setCredentialPosition(credentialPosition);
        return proofSchemeDataService.insertProofSchemeCredentialScheme(
                proofSchemeCredentialSchemeEntity);
    }

    private List<CredentialSchemeAttribute> insertRequestedAttributesForProofScheme(
            final CredentialSchemeEntity credentialSchemeEntity,
            final ProofSchemeCredentialSchemeEntity proofSchemeCredentialSchemeEntity,
            final List<Integer> requestedAttributeIds)
            throws InvalidAttributeException {

        final var attributeSuperset =
                credentialSchemeDataService.findAttributesByCredentialScheme(
                        credentialSchemeEntity);
        final var validAttributeIds =
                attributeSuperset.stream().map(CredentialSchemeAttributeEntity::getId).toList();

        for (final var id : requestedAttributeIds) {
            if (!validAttributeIds.contains(id)) {
                throw new InvalidAttributeException(id);
            }
        }
        final var requestedAttributeEntitiesToInsert =
                attributeSuperset.stream()
                        .filter(attribute -> requestedAttributeIds.contains(attribute.getId()))
                        .map(
                                attribute ->
                                        ProofSchemeCredentialSchemeUtils
                                                .fromCredentialSchemeAttributeEntity(
                                                        attribute,
                                                        proofSchemeCredentialSchemeEntity))
                        .toList();

        proofSchemeDataService.insertRequestedAttributes(requestedAttributeEntitiesToInsert);
        proofSchemeCredentialSchemeEntity.setProofSchemeCredentialSchemeRequestedAttributeEntityList(
                requestedAttributeEntitiesToInsert);
        return requestedAttributeEntitiesToInsert.stream()
                .map(
                        ProofSchemeCredentialSchemeRequestedAttributeEntity
                                ::getCredentialSchemeAttributeEntity)
                .map(
                        attribute ->
                                new CredentialSchemeAttribute(
                                        attribute.getId(),
                                        attribute.getFieldName(),
                                        attribute.getFieldType(),
                                        attribute.isArray(),
                                        attribute.isSensitive(),
                                        attribute.isDisclosable(),
                                        CredentialSchemeUtils.getLocalizedDisplayName(
                                                attribute.getDisplayName()),
                                        attribute.getFormatSpecificAttributeName()))
                .toList();
    }

    public ProofSchemeDetail toProofScheme(final ProofSchemeEntity proofSchemeEntity) {
        final var proofSchemeCredentialSchemeEntities =
                proofSchemeDataService.findAllByProofScheme(proofSchemeEntity);
        return new ProofSchemeDetail(
                proofSchemeEntity.getUuid(),
                proofSchemeEntity.getTitle(),
                proofSchemeEntity.getPurpose(),
                effectivePresentationProfileId(proofSchemeEntity),
                proofSchemeEntity.getValidationLogic(),
                proofSchemeEntity.getValidationMode(),
                proofSchemeEntity.getRedirectUri(),
                proofSchemeEntity.getTenantId(),
                identityDefinition(effectiveIdentity(proofSchemeEntity)),
                proofSchemeEntity.getVerifierIdentity() != null,
                proofSchemeEntity.getVerifierSigningKeyId(),
                providerId(proofSchemeEntity),
                effectiveTrustSystem(proofSchemeEntity),
                effectiveClientIdScheme(proofSchemeEntity),
                proofSchemeEntity.getVerifierClientIdScheme() != null,
                effectiveRegistrationCertificate(proofSchemeEntity),
                effectiveSwissIdentityStatement(proofSchemeEntity),
                proofSchemeEntity.getSwissVerificationQueryStatement(),
                proofSchemeEntity.isSwissVerificationQueryEnabled(),
                proofSchemeEntity.isAlwaysIncludeDcqlQuery(),
                proofSchemeEntity.getSwissProtectedVerificationStatements(),
                effectiveEudiVerificationTrustAnchors(proofSchemeEntity),
                effectiveSwissVerificationTrustAnchor(proofSchemeEntity),
                effectiveSwissTrustRegistryBaseUrl(proofSchemeEntity),
                proofSchemeEntity.getTrustedAuthorities(),
                proofSchemeEntity.getVerifierInfos(),
                proofSchemeEntity.getCreatedAt(),
                proofSchemeEntity.getUpdatedAt(),
                proofSchemeCredentialSchemeEntities.stream()
                        .map(ProofSchemeCredentialSchemeUtils::toCredentialScheme)
                        .toList());
    }

    private void applyVerifierConfiguration(ProofSchemeEntity entity, ProofSchemePayload payload) {
        var identity =
                payload.verifierIdentityId() == null
                        ? null
                        : issuerDefinitionRepository
                                .findByIdAndDeletedFalse(payload.verifierIdentityId())
                                .orElseThrow(
                                        () ->
                                new IllegalArgumentException(
                                        "Verifier identity not found"));
        if (identity == null && identityKeySlotService != null) {
            throw new IllegalArgumentException("A proof schema must name a verifier identity");
        }
        if (identity != null
                && identity.getTenantId() != null
                && !identity.getTenantId().equals(entity.getTenantId())) {
            throw new SecurityException(
                    "An organisation cannot use another organisation's identity.");
        }
        entity.setVerifierIdentity(identity);
        entity.setVerifierSigningKeyId(
                payload.verifierSigningKeyId() == null || payload.verifierSigningKeyId().isBlank()
                        ? null
                        : payload.verifierSigningKeyId());
        entity.setProofSigningProvider(resolveProofProvider(
                entity.getTenantId(), payload.proofSigningProviderId()));
        if (identityKeySlotService != null && identity != null
                && payload.proofSigningProviderId() != null) {
            identityKeySlotService.ensureKeySlot(
                    identity.getTenantId(), identity.getId(),
                    org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.OPERATION,
                    IssuerTrustSystem.Default, BBS_PRESENTATION_SETUP_OPERATION,
                    null, payload.proofSigningProviderId());
        }
        entity.setVerifierClientIdScheme(payload.verifierClientIdScheme());
        entity.setRegistrationCertificate(payload.registrationCertificate());
        entity.setSwissIdentityStatement(payload.swissIdentityStatement());
        entity.setSwissVerificationQueryStatement(payload.swissVerificationQueryStatement());
        entity.setSwissVerificationQueryEnabled(payload.effectiveSwissVerificationQueryEnabled());
        entity.setAlwaysIncludeDcqlQuery(payload.effectiveAlwaysIncludeDcqlQuery());
        entity.setSwissProtectedVerificationStatements(
                payload.swissProtectedVerificationStatements());
        entity.setTrustedAuthorities(validateTrustedAuthorities(payload.trustedAuthorities()));
        entity.setVerifierInfos(validateVerifierInfos(payload.verifierInfos()));
    }

    private SigningProviderEntity resolveProofProvider(String tenantId, Integer providerId) {
        if (providerId == null) return null;

        var provider = signingProviderService.resolve(tenantId, providerId);
        var configuration = signingProviderService.runtimeConfiguration(tenantId, providerId);
        if (!configuration.keylessOperations().contains(BBS_PRESENTATION_SETUP_OPERATION)) {
            throw new IllegalArgumentException(
                    "Proof signing provider does not support BBS presentation setup");
        }
        return provider;
    }

    private static Integer providerId(ProofSchemeEntity entity) {
        return entity.getProofSigningProvider() == null
                ? null : entity.getProofSigningProvider().getId();
    }

    public SigningOperationResult executeOperation(
            UUID proofSchemeId, String operation, String inputJson) {
        var proofScheme = proofSchemeDataService.findById(proofSchemeId)
                .orElseThrow(() -> new ProofSchemeNotFoundException(proofSchemeId));
        var configuredProvider = proofScheme.getProofSigningProvider();
        if (configuredProvider == null) {
            throw new IllegalStateException("Proof scheme has no signing provider");
        }
        var provider = signingProviderService.provider(
                proofScheme.getTenantId(), configuredProvider.getId());
        if (!(provider instanceof SigningOperationProvider operations)
                || !operations.keylessOperations().contains(operation)) {
            throw new IllegalArgumentException(
                    "Proof signing provider does not support keyless operation: " + operation);
        }
        return operations.executeKeyless(new SigningOperationRequest(operation, null, inputJson));
    }

    @Transactional
    public BbsIssuerMetadata bbsIssuerMetadata(
            UUID proofSchemeId, List<String> credentialQueryIds) {
        var proofScheme = proofSchemeDataService.findById(proofSchemeId)
                .orElseThrow(() -> new ProofSchemeNotFoundException(proofSchemeId));
        var requestedIds = credentialQueryIds == null
                ? Set.<String>of() : Set.copyOf(credentialQueryIds);
        var metadata = proofSchemeDataService.findAllByProofScheme(proofScheme).stream()
                .map(ProofSchemeCredentialSchemeEntity::getCredentialSchemeEntity)
                .filter(scheme -> requestedIds.contains(
                        scheme.getCredentialIdentifier() + "_bbs-termwise"))
                .map(this::bbsIssuerMetadata)
                .distinct()
                .toList();
        if (metadata.isEmpty()) {
            throw new IllegalArgumentException(
                    "Proof scheme has no BBS credential matching the requested query IDs");
        }
        if (metadata.size() != 1) {
            throw new IllegalArgumentException(
                    "Selected BBS credentials use different issuer signing configurations");
        }
        return metadata.getFirst();
    }

    private BbsIssuerMetadata bbsIssuerMetadata(CredentialSchemeEntity scheme) {
        var issuer = scheme.getIssuerDefinition();
        var requestedTrustSystem = scheme.getDefaultTrustSystem() == null
                ? org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem.Default
                : scheme.getDefaultTrustSystem();
        var signing = issuerService.getOperationSigningConfiguration(
                        issuer.getSlug(), requestedTrustSystem, BBS_ISSUANCE_OPERATION,
                        scheme.getCredentialIdentifier(), scheme.getVersion(),
                        scheme.getIssuanceProfileId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Issuer '" + issuer.getSlug() + "' has no BBS issuance key"));
        var configured = operationConfigurationService.getForIssuer(
                        issuer.getSlug(), signing.trustSystem(), BBS_ISSUANCE_OPERATION,
                        signing.profileId())
                .map(response -> response.configuration())
                .orElse(null);
        var issuerId = configuredText(configured, "issuerId");
        if (issuerId == null) issuerId = "did:heidi:" + issuer.getSlug();
        var issuerKeyId = configuredText(configured, "issuerKeyId");
        if (issuerKeyId == null) issuerKeyId = issuerId + "#" + signing.keyId();
        var issuerPk = configuredText(configured, "issuerPk");
        if (issuerPk == null) issuerPk = publicKey(signing.issuerJwk(), issuer.getSlug());
        return new BbsIssuerMetadata(issuerPk, issuerId, issuerKeyId);
    }

    private static String configuredText(
            tools.jackson.databind.JsonNode configuration, String name) {
        if (configuration == null) return null;
        var value = configuration.get(name);
        return value == null || !value.isString() || value.asString().isBlank()
                ? null : value.asString();
    }

    private static String publicKey(String publicKeyDocument, String issuerSlug) {
        if (publicKeyDocument == null || publicKeyDocument.isBlank()) {
            throw new IllegalArgumentException(
                    "BBS key for issuer '" + issuerSlug + "' has no public key");
        }
        try {
            var document = new tools.jackson.databind.ObjectMapper().readTree(publicKeyDocument);
            var key = document.get("x");
            if (key == null || !key.isString() || key.asString().isBlank()) {
                throw new IllegalArgumentException(
                    "BBS key for issuer '" + issuerSlug + "' has no 'x' public key");
            }
            return key.asString();
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalArgumentException(
                    "BBS key for issuer '" + issuerSlug
                            + "' has an invalid public key document",
                    exception);
        }
    }

    private List<TrustedAuthorityQuery> validateTrustedAuthorities(
            List<TrustedAuthorityQuery> trustedAuthorities) {
        if (trustedAuthorities == null) return List.of();
        for (var authority : trustedAuthorities) {
            if (authority == null || authority.type() == null || authority.type().isBlank()) {
                throw new IllegalArgumentException("Trusted authority type must not be empty");
            }
            if (authority.values() == null
                    || authority.values().isEmpty()
                    || authority.values().stream()
                            .anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException(
                        "Trusted authority values must be a non-empty list of non-empty strings");
            }
            authority
                    .values()
                    .forEach(value -> validateTrustedAuthorityValue(authority.type(), value));
        }
        return List.copyOf(trustedAuthorities);
    }

    private List<VerifierInfo> validateVerifierInfos(List<VerifierInfo> verifierInfos) {
        if (verifierInfos == null) return List.of();
        for (var verifierInfo : verifierInfos) {
            if (verifierInfo == null || !hasText(verifierInfo.format())
                    || !hasText(verifierInfo.data())) {
                throw new IllegalArgumentException(
                        "Verifier info format and data must not be empty");
            }
            if (verifierInfo.credentialIds() != null
                    && verifierInfo.credentialIds().stream().anyMatch(id -> !hasText(id))) {
                throw new IllegalArgumentException(
                        "Verifier info credential IDs must not be empty");
            }
        }
        return List.copyOf(verifierInfos);
    }

    private void validateTrustedAuthorityValue(String type, String value) {
        if ("aki".equals(type)) {
            if (!value.matches("[A-Za-z0-9_-]+")) {
                throw new IllegalArgumentException(
                        "aki trusted authority values must use unpadded base64url encoding");
            }
            try {
                if (Base64.getUrlDecoder().decode(value).length == 0) {
                    throw new IllegalArgumentException("aki trusted authority value is empty");
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "aki trusted authority values must use unpadded base64url encoding",
                        exception);
            }
            return;
        }
        if (Set.of("etsi_tl", "openid_federation").contains(type)) {
            try {
                var identifier = URI.create(value);
                if (!identifier.isAbsolute() || !"https".equalsIgnoreCase(identifier.getScheme())) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        type + " trusted authority values must be absolute HTTPS identifiers",
                        exception);
            }
        }
    }

    private IssuerDefinitionEntity effectiveIdentity(ProofSchemeEntity entity) {
        if (entity.getVerifierIdentity() != null) return entity.getVerifierIdentity();
        return proofSchemeDataService.findAllByProofScheme(entity).stream()
                .sorted(
                        java.util.Comparator.comparingInt(
                                ProofSchemeCredentialSchemeEntity::getCredentialPosition))
                .findFirst()
                .map(ProofSchemeCredentialSchemeEntity::getCredentialSchemeEntity)
                .map(CredentialSchemeEntity::getIssuerDefinition)
                .orElse(null);
    }

    private org.heidiverse.heidi.entity.model.issuer.IssuerDefinition identityDefinition(
            IssuerDefinitionEntity identity) {
        if (identity == null) return null;
        var names = identity.getDisplayName();
        return new org.heidiverse.heidi.entity.model.issuer.IssuerDefinition(
                identity.getId(),
                identity.getLogo(),
                identity.getSlug(),
                names == null || names.isEmpty()
                        ? null
                        : org.heidiverse.heidi.shared.localized.LocalizedValue.fromStringMap(
                                names));
    }

    private org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem effectiveTrustSystem(
            ProofSchemeEntity entity) {
        var profile = EcosystemProfileCatalog.find(
                entity.getPresentationProfileId(), EcosystemProfileRole.PRESENTATION);
        if (profile.isPresent()) {
            var trustSystem = profile.get().policy().trustSystem();
            return trustSystem == null ? IssuerTrustSystem.Default : trustSystem;
        }
        return entity.getVerifierTrustSystem() == null
                ? IssuerTrustSystem.Default : entity.getVerifierTrustSystem();
    }

    private VerifierClientIdScheme effectiveClientIdScheme(ProofSchemeEntity entity) {
        if (entity.getVerifierClientIdScheme() != null) {
            return entity.getVerifierClientIdScheme();
        }

        var profile = EcosystemProfileCatalog.find(
                entity.getPresentationProfileId(), EcosystemProfileRole.PRESENTATION);
        if (profile.isPresent()) {
            return profile.get().policy().clientIdScheme();
        }

        var trustSystem = effectiveTrustSystem(entity);
        if (trustSystem == IssuerTrustSystem.Switzerland) {
            return VerifierClientIdScheme.DECENTRALIZED_IDENTIFIER;
        }
        if (trustSystem == IssuerTrustSystem.EUDI) {
            return VerifierClientIdScheme.X509_HASH;
        }
        return VerifierClientIdScheme.X509_SAN_DNS;
    }

    private String effectiveRegistrationCertificate(ProofSchemeEntity entity) {
        return entity.getRegistrationCertificate();
    }

    private String effectiveSwissIdentityStatement(ProofSchemeEntity entity) {
        if (entity.getSwissIdentityStatement() != null
                && !entity.getSwissIdentityStatement().isBlank()) {
            return entity.getSwissIdentityStatement();
        }
        var identity = effectiveIdentity(entity);
        return identityTrustRequest(identity, IssuerTrustSystem.Switzerland)
                .map(org.heidiverse.heidi.entity.model.issuer.IssuerTrustConfigurationRequest::swissIdentityStatement)
                .orElse(null);
    }

    private List<String> effectiveEudiVerificationTrustAnchors(ProofSchemeEntity entity) {
        var identity = effectiveIdentity(entity);
        return identity == null ? List.of() : eudiTrustAnchors(identity);
    }

    /** Manual anchors plus, when enabled, the identity organisation's Issuing PKI roots. */
    List<String> eudiTrustAnchors(IssuerDefinitionEntity identity) {
        var manual = identity.getEudiVerificationTrustAnchors();
        if (!identity.isEudiTrustOwnPki() || issuingPki == null) return manual;

        // Resolved per request, so renewed or re-imported SubCA chains apply immediately.
        var anchors = new java.util.LinkedHashSet<>(manual);
        anchors.addAll(issuingPki.trustAnchors(identity.getTenantId()));
        return List.copyOf(anchors);
    }

    private String effectiveSwissVerificationTrustAnchor(ProofSchemeEntity entity) {
        var identity = effectiveIdentity(entity);
        return identity == null ? null : identity.getSwissVerificationTrustAnchor();
    }

    private String effectiveSwissTrustRegistryBaseUrl(ProofSchemeEntity entity) {
        var identity = effectiveIdentity(entity);
        if (identity == null) return null;
        return identityTrustRequest(identity, IssuerTrustSystem.Switzerland)
                .map(org.heidiverse.heidi.entity.model.issuer.IssuerTrustConfigurationRequest::swissRegistryBaseUrl)
                .filter(value -> value != null && !value.isBlank())
                .orElse(null);
    }

    private java.util.Optional<org.heidiverse.heidi.entity.model.issuer.IssuerTrustConfigurationRequest>
            identityTrustRequest(IssuerDefinitionEntity identity, IssuerTrustSystem trustSystem) {
        if (identity == null || identityKeySlotService == null) return java.util.Optional.empty();
        return identityKeySlotService.slots(identity.getId()).stream()
                .filter(slot -> slot.getType()
                        == org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.IDENTITY_STATEMENT)
                .filter(slot -> slot.getTrustSystem() == trustSystem)
                .findFirst()
                .flatMap(slot -> {
                    try {
                        return java.util.Optional.ofNullable(new tools.jackson.databind.ObjectMapper()
                                .treeToValue(slot.getConfiguration(),
                                        org.heidiverse.heidi.entity.model.issuer.IssuerTrustConfigurationRequest.class));
                    } catch (Exception exception) {
                        return java.util.Optional.empty();
                    }
                });
    }
}
