// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.heidiverse.heidi.entity.service.utils.OcaUtilsKt.getOcaBundleHash;

import org.kapunsdk.visualization.oca.model.OcaBundleJson;
import org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService;
import org.heidiverse.heidi.entity.data.service.IssuerDataService;
import org.heidiverse.heidi.entity.data.service.MetadataDataService;
import org.heidiverse.heidi.entity.data.repository.StatusListRepository;
import org.heidiverse.heidi.entity.model.context.ClaimContext;
import org.heidiverse.heidi.entity.model.context.CredentialContext;
import org.heidiverse.heidi.entity.model.context.CredentialContextResponse;
import org.heidiverse.heidi.entity.model.credentialscheme.*;
import org.heidiverse.heidi.entity.model.entity.*;
import org.heidiverse.heidi.entity.model.exceptions.InvalidCredentialSchemeException;
import org.heidiverse.heidi.entity.model.exceptions.InvalidIssuerException;
import org.heidiverse.heidi.entity.model.exceptions.SchemaNotFoundException;
import org.heidiverse.heidi.entity.model.exceptions.UnauthorizedAccessException;
import org.heidiverse.heidi.entity.model.issuer.IssuerSettings;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileCatalog;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfile;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileFamily;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileRole;
import org.heidiverse.heidi.entity.model.metadata.MetaAttribute;
import org.heidiverse.heidi.entity.model.user.JwtUserProfile;
import org.heidiverse.heidi.entity.model.user.UserRole;
import org.heidiverse.heidi.entity.service.cache.CredentialSchemeStyleCache;
import org.heidiverse.heidi.entity.service.utils.CredentialSchemeUtils;
import org.heidiverse.heidi.entity.service.utils.JwtUtils;
import org.heidiverse.heidi.entity.service.utils.OcaUtilsKt;
import org.heidiverse.heidi.entity.service.utils.SwiyuBundleCreator;
import org.heidiverse.heidi.entity.service.utils.TypeMetadataUtils;
import org.heidiverse.heidi.shared.oca.OcaFormat;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
public class CredentialSchemeService {

    private static final Logger logger = LoggerFactory.getLogger(CredentialSchemeService.class);
    // Protected attribute names from https://www.w3.org/ns/credentials/v2
    private static final Set<String> protectedAttributeNames = Set.of(
            "id", "type", "description", "digestMultibase", "digestSRI",
            "mediaType", "name", "...", "_sd", "_sd_alg", "aud", "cnf",
            "exp", "iat", "iss", "jku", "kid", "nbf", "status", "status_list", "sub", "x5u",
            "VerifiableCredential", "EnvelopedVerifiableCredential",
            "VerifiablePresentation", "EnvelopedVerifiablePresentation",
            "JsonSchemaCredential", "JsonSchema", "BitstringStatusListCredential",
            "BitstringStatusList", "BitstringStatusListEntry", "DataIntegrityProof");

    private final CredentialSchemeDataService credentialSchemeDataService;
    private final TemplateService templateService;
    private final IssuerDataService issuerDataService;
    private final MetadataDataService metadataDataService;
    private final ObjectMapper objectMapper;
    private final StatusListRepository statusListRepository;
    private final IdentityKeySlotService identityKeySlotService;
    private final IssuerService issuerService;

    @Value("${heidi.platform.public-base-url}")
    String entityBaseUrl;

    @Value("${heidi.issuer.public-base-url}")
    String issuerBaseUrl;

    @Autowired
    public CredentialSchemeService(
            final CredentialSchemeDataService credentialSchemeDataService,
            TemplateService templateService,
            final IssuerDataService issuerDataService,
            final MetadataDataService metadataDataService,
            final ObjectMapper objectMapper,
            StatusListRepository statusListRepository,
            IdentityKeySlotService identityKeySlotService,
            IssuerService issuerService) {
        this.credentialSchemeDataService = credentialSchemeDataService;
        this.templateService = templateService;
        this.issuerDataService = issuerDataService;
        this.metadataDataService = metadataDataService;
        this.objectMapper = objectMapper;
        this.statusListRepository = statusListRepository;
        this.identityKeySlotService = identityKeySlotService;
        this.issuerService = issuerService;
    }

    public CredentialSchemeService(
            CredentialSchemeDataService credentialSchemeDataService,
            TemplateService templateService,
            IssuerDataService issuerDataService,
            MetadataDataService metadataDataService,
            ObjectMapper objectMapper) {
        this(credentialSchemeDataService, templateService, issuerDataService,
                metadataDataService, objectMapper, null, null, null);
    }

    @Transactional
    public CredentialSchemeOverview getOverview(
            Collection<CredentialSchemeState> states,
            boolean includeStyle,
            boolean includeImages,
            String credentialIdentifier,
            boolean requireAuthentication) {
        List<CredentialSchemeEntity> credentialSchemeEntities;
        if (requireAuthentication) {
            String tenantId = JwtUtils.getUserProfile().tenantId();
            boolean isSuperAdmin =
                    JwtUtils.getUserProfile().permissions().contains(UserRole.SUPER_ADMIN);

            credentialSchemeEntities =
                    isSuperAdmin
                            ? credentialSchemeDataService.findAllFilteredByState(states)
                            : credentialSchemeDataService.findAllByTenantIdAndState(
                                    tenantId, states);
        } else {
            credentialSchemeEntities = credentialSchemeDataService.findAllFilteredByState(states);
        }
        // Filter by credential identifier (if given)
        if (!Objects.requireNonNullElse(credentialIdentifier, "").isEmpty()) {
            credentialSchemeEntities =
                    credentialSchemeEntities.stream()
                            .filter(
                                    scheme ->
                                            credentialIdentifier.equals(
                                                    scheme.getCredentialIdentifier()))
                            .toList();
        }
        return getOverviewForEntities(credentialSchemeEntities, includeStyle, includeImages);
    }

    @Transactional
    public CredentialSchemeOverview getOverviewForIssuer(
            String slug,
            Collection<CredentialSchemeState> states,
            boolean includeStyle,
            boolean includeImages,
            String credentialIdentifier,
            boolean requireAuthentication) {
        // Fetch all credential schemas matching the slug and states
        final var credentialSchemeEntities =
                credentialSchemeDataService.findByIssuerDefinitionSlugFilteredByState(slug, states);

        if (!requireAuthentication) {
            return getOverviewForEntities(credentialSchemeEntities, includeStyle, includeImages);
        }

        JwtUserProfile userProfile = JwtUtils.getUserProfile();
        String tenantId = userProfile.tenantId();
        boolean isSuperAdmin = userProfile.permissions().contains(UserRole.SUPER_ADMIN);

        // Filter by tenantId if the user is not a super-admin
        List<CredentialSchemeEntity> filteredEntities =
                credentialSchemeEntities.stream()
                        .filter(scheme -> isSuperAdmin || scheme.getTenantId().equals(tenantId))
                        .toList();

        // Filter by credential identifier (if given)
        if (!Objects.requireNonNullElse(credentialIdentifier, "").isEmpty()) {
            filteredEntities =
                    filteredEntities.stream()
                            .filter(
                                    scheme ->
                                            credentialIdentifier.equals(
                                                    scheme.getCredentialIdentifier()))
                            .toList();
        }
        return getOverviewForEntities(filteredEntities, includeStyle, includeImages);
    }

    private CredentialSchemeOverview getOverviewForEntities(
            List<CredentialSchemeEntity> credentialSchemeEntities,
            boolean includeStyle,
            boolean includeImages) {

        return new CredentialSchemeOverview(
                credentialSchemeEntities.stream()
                        .map(
                                credentialSchemeEntity -> {
                                    final var styles =
                                            includeStyle
                                                    ? credentialSchemeEntity.getStyles().stream()
                                                            .map(
                                                                    CredentialSchemeUtils
                                                                            ::toCredentialSchemeStyleDetail)
                                                            .map(
                                                                    styleDetail -> {
                                                                        if (includeImages) {
                                                                            return styleDetail;
                                                                        } else {
                                                                            return CredentialSchemeStyleCache
                                                                                    .getOrCacheWithoutImages(
                                                                                            styleDetail);
                                                                        }
                                                                    })
                                                            .toList()
                                                    : null;
                                    return CredentialSchemeUtils.toCredentialSchemeOverviewEntry(
                                            credentialSchemeEntity, styles);
                                })
                        .toList());
    }

    public CredentialSchemeDetailResponse findById(final UUID id, boolean requireAuthentication)
            throws SchemaNotFoundException {
        if (requireAuthentication) {
            String tenantId = JwtUtils.getUserProfile().tenantId();
            boolean isSuperAdmin =
                    JwtUtils.getUserProfile().permissions().contains(UserRole.SUPER_ADMIN);
            return credentialSchemeDataService
                    .findById(id)
                    .filter(scheme -> isSuperAdmin || scheme.getTenantId().equals(tenantId))
                    .map(this::toCredentialSchemeDetailResponse)
                    .orElseThrow(() -> new SchemaNotFoundException(id));
        }
        return credentialSchemeDataService
                .findById(id)
                .map(this::toCredentialSchemeDetailResponse)
                .orElseThrow(() -> new SchemaNotFoundException(id));
    }

    @Transactional
    public String getOcaBundleForOcaBundleFileName(String ocaBundleFileName) throws SchemaNotFoundException {
        return credentialSchemeDataService.findBundleByOcaBundleFileName(ocaBundleFileName)
                .orElseThrow(() -> new SchemaNotFoundException(ocaBundleFileName));
    }

    @Transactional
    public String getOcaBundle(String fileName, OcaFormat format) throws SchemaNotFoundException {
        var style = credentialSchemeDataService.findSwiyuStyle(fileName)
                .orElseThrow(() -> new SchemaNotFoundException(fileName));
        if (format == OcaFormat.SWIYU) {
            ensureSwiyuBundle(style, credentialSchemeDataService.findAttributesByCredentialScheme(
                    style.getCredentialSchemeEntity()));
            return style.getSwiyuOcaBundle();
        }
        return style.getOcaBundle();
    }

    @Transactional
    public String getOcaBundle(String fileName, String format, String userAgent)
            throws SchemaNotFoundException {
        var style = credentialSchemeDataService.findSwiyuStyle(fileName)
                .orElseThrow(() -> new SchemaNotFoundException(fileName));
        var filenameFormat = toOcaFormat(style.getOcaVersion());
        if (fileName.equals(style.getSwiyuOcaFileName())) {
            filenameFormat = OcaFormat.SWIYU;
        } else if (fileName.equals(style.getOcaBundleFileName())) {
            filenameFormat = OcaFormat.LEGACY;
        }
        var selected = OcaFormat.select(format, userAgent, filenameFormat);
        return getOcaBundle(fileName, selected);
    }

    public CredentialSchemeDetailResponse findByCredentialIdentifierAndVersion(
            final String identifier, final String version, boolean requireAuthentication)
            throws SchemaNotFoundException {
        if (requireAuthentication) {
            String tenantId = JwtUtils.getUserProfile().tenantId();
            boolean isSuperAdmin =
                    JwtUtils.getUserProfile().permissions().contains(UserRole.SUPER_ADMIN);
            return credentialSchemeDataService
                    .findByCredentialIdentifierAndVersion(identifier, version)
                    .filter(scheme -> isSuperAdmin || scheme.getTenantId().equals(tenantId))
                    .map(this::toCredentialSchemeDetailResponse)
                    .orElseThrow(() -> new SchemaNotFoundException(identifier));
        }
        return credentialSchemeDataService
                .findByCredentialIdentifierAndVersion(identifier, version)
                .map(this::toCredentialSchemeDetailResponse)
                .orElseThrow(() -> new SchemaNotFoundException(identifier));
    }

    @Transactional
    public UUID create(final CredentialSchemeDetail credentialScheme, String tenantId)
            throws InvalidIssuerException, InvalidCredentialSchemeException {

        validateIssuanceProfile(credentialScheme.issuerSettings());
        validateMaxBatchSize(credentialScheme.maxBatchSize());
        validateStatusList(credentialScheme.issuerSettings(), tenantId);

        // Check if credential schema with the same identifier and version already exists
        if (credentialSchemeDataService
                .findByCredentialIdentifierAndVersion(
                        credentialScheme.credentialIdentifier(), credentialScheme.version())
                .isPresent()) {
            throw new InvalidCredentialSchemeException(
                    "An credential schema with this identifier and version already exists.");
        }

        // Find existing credential schemas with the same identifier
        List<CredentialSchemeEntity> existingSchemes =
                credentialSchemeDataService.findByCredentialIdentifier(
                        credentialScheme.credentialIdentifier());

        if (!existingSchemes.isEmpty()) {
            // Ensure that all existing credential schemas have the same tenantId
            for (CredentialSchemeEntity existingScheme : existingSchemes) {
                if (existingScheme.getTenantId() != null
                        && !existingScheme.getTenantId().equals(tenantId)) {
                    throw new InvalidCredentialSchemeException(
                            "An credential schema with this identifier already exists under a different tenant.");
                }
            }
        }

        // Validate template if templateId exists
        if (credentialScheme.templateId() != null) {
            templateService.validateTemplate(
                    credentialScheme.templateId(), credentialScheme.attributes());
        }
        // Insert the new credential schema
        final var inserted =
                insertCredentialScheme(
                        credentialScheme, credentialScheme.issuerSettings().id(), tenantId);
        ensureStatusSlot(inserted);
        final var insertedAttributes =
                insertCredentialSchemeAttributes(inserted, credentialScheme.attributes());
        // metadata attributes
        insertMetadata(credentialScheme.metadata(), inserted);

        insertStyles(
                inserted, credentialScheme.credentialSchemeStylePayloads(), insertedAttributes);
        if (issuerService != null) issuerService.reconcileSigningGrants();

        return inserted.getUuid();
    }

    @Transactional
    public void archive(final UUID id) throws SchemaNotFoundException {
        final var credentialSchemeEntity =
                credentialSchemeDataService
                        .findById(id)
                        .orElseThrow(() -> new SchemaNotFoundException(id));

        // Validate access
        validateTenantIdAccess(credentialSchemeEntity);

        // Archive the credential schema
        credentialSchemeEntity.setState(CredentialSchemeState.ARCHIVED);
        credentialSchemeDataService.insertScheme(credentialSchemeEntity);
        if (issuerService != null) issuerService.reconcileSigningGrants();
    }

    @Transactional
    public void publish(final UUID id, final String version) throws SchemaNotFoundException {
        final var credentialSchemeEntity =
                credentialSchemeDataService
                        .findById(id)
                        .orElseThrow(() -> new SchemaNotFoundException(id));

        validateTenantIdAccess(credentialSchemeEntity);
        publish(credentialSchemeEntity, version);
    }

    @Transactional
    public void publishForTenant(final UUID id, final String version, final String tenantId)
            throws SchemaNotFoundException {
        final var credentialSchemeEntity =
                credentialSchemeDataService
                        .findById(id)
                        .orElseThrow(() -> new SchemaNotFoundException(id));
        if (credentialSchemeEntity.getTenantId() != null
                && !credentialSchemeEntity.getTenantId().equals(tenantId)) {
            throw new UnauthorizedAccessException("Access denied: Tenant ID mismatch.");
        }

        publish(credentialSchemeEntity, version);
    }

    private void publish(CredentialSchemeEntity credentialSchemeEntity, String version)
            throws SchemaNotFoundException {
        if (credentialSchemeEntity.getState() != CredentialSchemeState.CREATED) {
            throw new SchemaNotFoundException(credentialSchemeEntity.getUuid());
        }
        validateIssuanceReadiness(credentialSchemeEntity, version);
        credentialSchemeEntity.setState(CredentialSchemeState.PUBLISHED);
        credentialSchemeEntity.setVersion(version);
        credentialSchemeDataService.insertScheme(credentialSchemeEntity);
        if (issuerService != null) issuerService.reconcileSigningGrants();
    }

    @Transactional
    public void update(final UUID id, final CredentialSchemeDetail credentialScheme)
            throws SchemaNotFoundException,
                    InvalidIssuerException,
                    InvalidCredentialSchemeException {

        validateIssuanceProfile(credentialScheme.issuerSettings());
        validateMaxBatchSize(credentialScheme.maxBatchSize());

        final var credentialSchemeEntity =
                credentialSchemeDataService
                        .findById(id)
                        .orElseThrow(() -> new SchemaNotFoundException(id));

        // Validate access
        validateTenantIdAccess(credentialSchemeEntity);
        validatePublishedBinding(credentialSchemeEntity, credentialScheme);
        validateStatusList(
                credentialScheme.issuerSettings(), credentialSchemeEntity.getTenantId());

        // Validate template if templateId exists
        if (credentialScheme.templateId() != null) {
            templateService.validateTemplate(
                    credentialScheme.templateId(), credentialScheme.attributes());
        }

        // Proceed with the update
        final var updatedIssuer = updateIssuerDefinition(
                credentialScheme.issuerSettings(), credentialSchemeEntity.getTenantId());
        validateIssuanceProfileCompatibility(
                issuanceProfileId(credentialScheme.issuerSettings()), updatedIssuer);
        final var updatedCredentialScheme =
                updateCredentialScheme(credentialScheme, id, updatedIssuer);
        ensureStatusSlot(updatedCredentialScheme);
        final var updatedAttributes =
                updateCredentialSchemeAttributes(
                        updatedCredentialScheme, credentialScheme.attributes());

        // NOTE: existing credentialSchemeStyleDetails are deleted and new
        // credentialSchemeStyleDetails are inserted
        final var existingStyles =
                credentialSchemeDataService.findAllStylesByCredentialSchemeEntity(
                        updatedCredentialScheme);
        credentialSchemeDataService.deleteStyles(existingStyles);
        updateMetadata(credentialScheme.metadata(), updatedCredentialScheme);
        insertStyles(
                updatedCredentialScheme,
                credentialScheme.credentialSchemeStylePayloads(),
                updatedAttributes);
        if (issuerService != null) issuerService.reconcileSigningGrants();
    }

    @Transactional
    public TypeMetadata getTypeMetadata(String identifier, String version)
            throws SchemaNotFoundException {
        return getTypeMetadata(identifier, version, null, null);
    }

    @Transactional
    public TypeMetadata getTypeMetadata(String identifier, String version, OcaFormat format)
            throws SchemaNotFoundException {
        CredentialSchemeEntity credentialSchemeEntity =
                credentialSchemeDataService
                        .findByCredentialIdentifierAndVersion(identifier, version)
                        .orElseThrow(() -> new SchemaNotFoundException(identifier));

        var attributes = credentialSchemeDataService.findAttributesByCredentialScheme(credentialSchemeEntity);
        var styles = credentialSchemeDataService.findAllStylesByCredentialSchemeEntity(credentialSchemeEntity);
        if (format == OcaFormat.SWIYU) {
            styles.forEach(style -> ensureSwiyuBundle(style, attributes));
        }
        return TypeMetadataUtils.buildTypeMetadata(
                credentialSchemeEntity,
                attributes,
                styles,
                entityBaseUrl,
                issuerBaseUrl,
                format);
    }

    @Transactional
    public TypeMetadata getTypeMetadata(
            String identifier, String version, String format, String userAgent)
            throws SchemaNotFoundException {
        CredentialSchemeEntity credentialSchemeEntity =
                credentialSchemeDataService
                        .findByCredentialIdentifierAndVersion(identifier, version)
                        .orElseThrow(() -> new SchemaNotFoundException(identifier));
        var styles = credentialSchemeDataService.findAllStylesByCredentialSchemeEntity(credentialSchemeEntity);
        var defaultFormat = styles.isEmpty()
                ? OcaFormat.LEGACY
                : toOcaFormat(styles.get(styles.size() - 1).getOcaVersion());
        var selected = OcaFormat.select(format, userAgent, defaultFormat);
        return getTypeMetadata(identifier, version, selected);
    }

    private static OcaFormat toOcaFormat(OcaVersion version) {
        return version == OcaVersion.SWIYU ? OcaFormat.SWIYU : OcaFormat.LEGACY;
    }

    @Transactional
    public String getSvgTemplate(String identifier, String version)
            throws SchemaNotFoundException, JacksonException {
        // Fetch the credential schema entity
        CredentialSchemeEntity credentialSchemeEntity =
                credentialSchemeDataService
                        .findByCredentialIdentifierAndVersion(identifier, version)
                        .orElseThrow(() -> new SchemaNotFoundException(identifier));

        // Fetch the latest style entity (assuming one style per credential schema)
        var styles =
                credentialSchemeDataService.findAllStylesByCredentialSchemeEntity(
                        credentialSchemeEntity);
        if (styles.isEmpty()) {
            throw new IllegalStateException("No style defined for this credential schema.");
        }
        CredentialSchemeStyleEntity styleEntity = styles.get(styles.size() - 1);
        JsonNode styleJson = objectMapper.readTree(styleEntity.getStyle());

        // Generate SVG using utility method
        return TypeMetadataUtils.generateSvgFromStyleJson(styleJson);
    }

    public Optional<String> findTenantIdByCredentialIdentifier(String credentialIdentifier) {
        return credentialSchemeDataService.findByCredentialIdentifier(credentialIdentifier).stream()
                .map(CredentialSchemeEntity::getTenantId)
                .filter(Objects::nonNull)
                .findFirst();
    }

    @Transactional
    public Optional<CredentialSchemeIssuerResponse> findIssuerSlugByCredentialIdentifierAndVersion(
            String credentialIdentifier, String version) {
        return credentialSchemeDataService
                .findByCredentialIdentifierAndVersion(credentialIdentifier, version)
                .filter(scheme -> scheme.getState() == CredentialSchemeState.PUBLISHED)
                .map(
                        scheme ->
                                new CredentialSchemeIssuerResponse(
                                        scheme.getIssuerDefinition().getSlug(),
                                        scheme.getTenantId(),
                                        scheme.getCredentialOfferType(),
                                        scheme.getIssuanceProfileId()))
                .filter(
                        response ->
                                response.issuerSlug() != null
                                        && !response.issuerSlug().isBlank());
    }

    public CredentialContextResponse getCredentialContextByCredentialIdentifierAndVersion(
            String identifier, String version, boolean requireAuthentication) throws SchemaNotFoundException {
        // Fetch the credential schema entity
        var schema = this.findByCredentialIdentifierAndVersion(identifier, version, requireAuthentication);

        final String contextPrefix = String.format(
                "%s/public/v2/schema/%s/%s/context",
                entityBaseUrl,
                identifier,
                version);

        final Map<String, ClaimContext> claims = new HashMap<>();
        for (var attribute : schema.attributes()) {
            // Skip redefining protected attributes
            if (protectedAttributeNames.contains(attribute.name()))
                continue;

            final String attributeId = String.format(
                    "%s/attributes#%s",
                    contextPrefix,
                    attribute.name());
            claims.put(attribute.name(),
                    new ClaimContext(
                            attributeId,
                            getAttributeJsonLDType(attribute.type())));
        }

        final String type = "CredentialSubject";

        final String credentialIdentifier = String.format(
                "%s/types#%s", contextPrefix, type);

        return new CredentialContextResponse(Map.of(
                type,
                new CredentialContext(credentialIdentifier, claims)
        ));
    }

    private String getAttributeJsonLDType(final AttributeType type) {
        return switch (type) {
            case STRING,
                 MAIL,
                 PHONE,
                 OTHER,
                 LOCATION -> "http://www.w3.org/2001/XMLSchema#string";
            case LINK,
                 FILE_DOWNLOAD -> "http://www.w3.org/2001/XMLSchema#anyURI";
            case NUMBER -> "http://www.w3.org/2001/XMLSchema#decimal";
            case BOOLEAN -> "http://www.w3.org/2001/XMLSchema#boolean";
            case DATE,
                 DATEOFBIRTH -> "http://www.w3.org/2001/XMLSchema#date";
            case TIME -> "http://www.w3.org/2001/XMLSchema#time";
            case DATETIME -> "http://www.w3.org/2001/XMLSchema#dateTime";
            case IMAGE -> "http://www.w3.org/2001/XMLSchema#base64Binary";
        };
    }

    private void validateTenantIdAccess(CredentialSchemeEntity credentialSchemeEntity) {
        String tenantId = JwtUtils.getUserProfile().tenantId();
        boolean isSuperAdmin =
                JwtUtils.getUserProfile().permissions().contains(UserRole.SUPER_ADMIN);

        if (!isSuperAdmin
                && credentialSchemeEntity.getTenantId() != null
                && !credentialSchemeEntity.getTenantId().equals(tenantId)) {
            throw new UnauthorizedAccessException("Access denied: Tenant ID mismatch.");
        }
    }

    private IssuerDefinitionEntity updateIssuerDefinition(
            final IssuerSettings issuerSettings, String tenantId)
            throws InvalidIssuerException {
        final var issuerId = issuerSettings.id();
        final var issuerDefinition =
                issuerDataService
                        .findById(issuerId)
                        .orElseThrow(() -> new InvalidIssuerException(issuerId));
        validateIdentityScope(issuerDefinition, tenantId);
        validateSigningKeyAssignments(issuerSettings, issuerDefinition);
        return issuerDataService.save(issuerDefinition);
    }

    private void validateSigningKeyAssignments(
            IssuerSettings issuerSettings, IssuerDefinitionEntity issuerDefinition) {
        var defaultTrustSystem = issuerSettings.defaultTrustSystem() == IssuerTrustSystem.Default
                ? null : issuerSettings.defaultTrustSystem();
        if (defaultTrustSystem != null
                && (identityKeySlotService == null || identityKeySlotService.slots(issuerDefinition.getId()).stream()
                        .noneMatch(slot -> slot.getType()
                                == org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.CREDENTIAL_SIGNING
                                && (slot.getTrustSystem() == defaultTrustSystem
                                        || (defaultTrustSystem == IssuerTrustSystem.Custom
                                                && slot.getTrustSystem() == IssuerTrustSystem.Default))))) {
            throw new IllegalArgumentException(
                    "Issuer has no credential signing key for default trust system "
                            + defaultTrustSystem);
        }
        if (issuerSettings.signingKeyIds() == null) return;
        issuerSettings.signingKeyIds().forEach((trustSystem, keyId) -> {
            if (keyId == null || keyId.isBlank()) return;
            if (identityKeySlotService == null
                    || !identityKeySlotService.hasCredentialKey(
                            issuerDefinition.getId(), trustSystem, keyId)) {
                throw new IllegalArgumentException(
                        "Signing key '" + keyId + "' is not in the issuer identity slots");
            }
        });
    }

    private void ensureStatusSlot(CredentialSchemeEntity scheme) {
        if (identityKeySlotService == null || scheme.getStatusListId() == null
                || scheme.getIssuerDefinition() == null) return;
        var statusList = statusListRepository.findById(scheme.getStatusListId())
                .orElseThrow(() -> new IllegalArgumentException("Status list not found"));
        identityKeySlotService.ensureKeySlot(
                scheme.getIssuerDefinition().getTenantId(),
                scheme.getIssuerDefinition().getId(),
                org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.STATUS_LIST,
                null, null, statusList.getSigningKeyId(), null);
    }

    private void validateStatusList(IssuerSettings settings, String tenantId) {
        if (settings.statusListId() == null || statusListRepository == null) return;
        if (settings.supportedCredentialTypes() == null
                || !settings.supportedCredentialTypes().contains(CredentialType.SD_JWT)) {
            throw new IllegalArgumentException("Status lists are only supported for SD-JWT VC");
        }

        var statusList = statusListRepository.findById(settings.statusListId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Status list not found: " + settings.statusListId()));
        if (!Objects.equals(statusList.getTenantId(), tenantId)) {
            throw new SecurityException("Status list does not belong to the tenant");
        }
        var profile = EcosystemProfileCatalog.require(
                settings.issuanceProfileId(), EcosystemProfileRole.ISSUANCE);
        if (profile.family() == EcosystemProfileFamily.SWISS_SWIYU
                && (statusList.getSwissStatusListUrl() == null
                        || statusList.getSwissPublishedAt() == null)) {
            throw new IllegalArgumentException(
                    "Swiss issuance requires a status list published in the Swiss registry");
        }
    }

    private CredentialSchemeEntity updateCredentialScheme(
            final CredentialSchemeDetail credentialScheme,
            final UUID id,
            final IssuerDefinitionEntity issuerDefinition)
            throws SchemaNotFoundException {
        final var credentialSchemeEntity =
                credentialSchemeDataService
                        .findById(id)
                        .orElseThrow(() -> new SchemaNotFoundException(id));
        credentialSchemeEntity.setCredentialIdentifier(credentialScheme.credentialIdentifier());
        credentialSchemeEntity.setVersion(credentialScheme.version());
        credentialSchemeEntity.setDisplayName(credentialScheme.displayName());
        credentialSchemeEntity.setUpdatedAt(Instant.now());
        credentialSchemeEntity.setIssuerDefinition(issuerDefinition);
        credentialSchemeEntity.setKeyType(credentialScheme.issuerSettings().issuerKeyType());
        credentialSchemeEntity.setDoctype(credentialScheme.issuerSettings().doctype());
        credentialSchemeEntity.setNamespace(credentialScheme.issuerSettings().namespace());
        credentialSchemeEntity.setVct(credentialScheme.issuerSettings().vct());
        credentialSchemeEntity.setTemplateId(credentialScheme.templateId());
        if (credentialScheme.maxBatchSize() != null) {
            credentialSchemeEntity.setMaxBatchSize(credentialScheme.maxBatchSize());
        }
        credentialSchemeEntity.setSupportedCredentialTypes(
                credentialScheme.issuerSettings().supportedCredentialTypes());
        credentialSchemeEntity.setIssClaimOverride(
                credentialScheme.issuerSettings().issClaimOverride());
        credentialSchemeEntity.setKidOverride(credentialScheme.issuerSettings().kidOverride());
        credentialSchemeEntity.setSigningKeyIds(credentialScheme.issuerSettings().signingKeyIds());
        credentialSchemeEntity.setStatusListId(credentialScheme.issuerSettings().statusListId());
        credentialSchemeEntity.setDefaultTrustSystem(
                effectiveTrustSystem(credentialScheme.issuerSettings()));
        credentialSchemeEntity.setCredentialOfferType(
                credentialScheme.issuerSettings().credentialOfferType());
        credentialSchemeEntity.setIssuanceProfileId(
                issuanceProfileId(credentialScheme.issuerSettings()));
        return credentialSchemeDataService.insertScheme(credentialSchemeEntity);
    }

    private List<CredentialSchemeAttributeEntity> updateCredentialSchemeAttributes(
            final CredentialSchemeEntity credentialSchemeEntity,
            final List<CredentialSchemeAttribute> attributes) {
        final var existingAttributes =
                credentialSchemeDataService.findAttributesByCredentialScheme(
                        credentialSchemeEntity);
        if (existingAttributes.isEmpty()) {
            return insertCredentialSchemeAttributes(credentialSchemeEntity, attributes);
        }
        final var existingAttributeFieldNames =
                existingAttributes.stream()
                        .map(CredentialSchemeAttributeEntity::getFieldName)
                        .toList();
        final var attributesEntities =
                new ArrayList<>(
                        attributes.stream()
                                .map(
                                        attribute -> {
                                            if (existingAttributeFieldNames.contains(
                                                    attribute.name())) {
                                                return updateAttribute(
                                                        existingAttributes, attribute);
                                            }
                                            return createNewAttribute(
                                                    credentialSchemeEntity, attribute);
                                        })
                                .toList());
        final var toDelete =
                existingAttributes.stream()
                        .filter(
                                attribute ->
                                        !attributesEntities.stream()
                                                .map(CredentialSchemeAttributeEntity::getFieldName)
                                                .toList()
                                                .contains(attribute.getFieldName()))
                        .toList();
        credentialSchemeDataService.deleteAttributes(toDelete);
        return credentialSchemeDataService.insertCredentialSchemeAttributes(attributesEntities);
    }

    private void updateMetadata(
            final CredentialSchemeMetadata metadata,
            final CredentialSchemeEntity credentialSchemeEntity) {
        final var metadataEntityOptional =
                metadataDataService.findByCredentialScheme(credentialSchemeEntity);
        if (!metadataEntityOptional.isPresent()) {
            insertMetadata(metadata, credentialSchemeEntity);
        } else {
            final var metadataEntity = metadataEntityOptional.get();
            metadataDataService.insertMetadata(metadataEntity);
            final var existingMetadataAttributes =
                    metadataDataService.findAllByMetadata(metadataEntity);
            final var existingAttributeKeys =
                    existingMetadataAttributes.stream()
                            .map(MetadataAttributeEntity::getAttributeKey)
                            .toList();
            final var attributesEntities =
                    new ArrayList<>(
                            metadata.metaAttributes().stream()
                                    .map(
                                            attribute -> {
                                                if (existingAttributeKeys.contains(
                                                        attribute.attributeKey())) {
                                                    return updateAttribute(
                                                            existingMetadataAttributes, attribute);
                                                }
                                                return createNewAttribute(
                                                        metadataEntity, attribute);
                                            })
                                    .toList());
            final var toDelete =
                    existingMetadataAttributes.stream()
                            .filter(
                                    attribute ->
                                            !attributesEntities.stream()
                                                    .map(MetadataAttributeEntity::getAttributeKey)
                                                    .toList()
                                                    .contains(attribute.getAttributeKey()))
                            .toList();
            metadataDataService.deleteMetadataAttributes(toDelete);
            metadataDataService.insertMetadataAttributes(attributesEntities);
        }
    }

    private void insertMetadata(
            final CredentialSchemeMetadata metadata,
            final CredentialSchemeEntity credentialSchemeEntity) {
        final var metadataEntity = new MetadataEntity();
        metadataEntity.setCredentialSchemeEntity(credentialSchemeEntity);
        metadataDataService.insertMetadata(metadataEntity);

        final var metadataAttributes =
                metadata.metaAttributes().stream()
                        .map(
                                metaAttribute -> {
                                    final var entity = new MetadataAttributeEntity();
                                    entity.setMetadataEntity(metadataEntity);
                                    entity.setAttributeKey(metaAttribute.attributeKey());
                                    entity.setDisplayName(
                                            metaAttribute.displayName().toMapWithLanguageTagKeys());
                                    return entity;
                                })
                        .toList();
        metadataDataService.insertMetadataAttributes(metadataAttributes);
    }

    private CredentialSchemeAttributeEntity updateAttribute(
            final List<CredentialSchemeAttributeEntity> existingAttributes,
            final CredentialSchemeAttribute attribute) {
        final var attributeEntity =
                existingAttributes.stream()
                        .filter(
                                existingAttribute ->
                                        existingAttribute.getFieldName().equals(attribute.name()))
                        .findFirst()
                        .get();
        attributeEntity.setDisplayName(attribute.displayName().toMapWithLanguageTagKeys());
        attributeEntity.setFieldType(attribute.type());
        attributeEntity.setSensitive(attribute.isSensitive());
        attributeEntity.setArray(attribute.isArray());
        attributeEntity.setDisclosable(attribute.isDisclosable());
        attributeEntity.setFormatSpecificAttributeName(attribute.attributeNameOverrides());
        return attributeEntity;
    }

    private MetadataAttributeEntity updateAttribute(
            final List<MetadataAttributeEntity> existingAttributes, final MetaAttribute attribute) {
        final var attributeEntity =
                existingAttributes.stream()
                        .filter(
                                existingAttribute ->
                                        existingAttribute
                                                .getAttributeKey()
                                                .equals(attribute.attributeKey()))
                        .findFirst()
                        .get();
        attributeEntity.setDisplayName(attribute.displayName().toMapWithLanguageTagKeys());
        return attributeEntity;
    }

    private CredentialSchemeAttributeEntity createNewAttribute(
            final CredentialSchemeEntity credentialSchemeEntity,
            final CredentialSchemeAttribute attribute) {
        final var attributeEntity = new CredentialSchemeAttributeEntity();
        attributeEntity.setCredentialSchemeEntity(credentialSchemeEntity);
        attributeEntity.setFieldName(attribute.name());
        attributeEntity.setFieldType(attribute.type());
        attributeEntity.setSensitive(attribute.isSensitive());
        attributeEntity.setArray(attribute.isArray());
        attributeEntity.setDisclosable(attribute.isDisclosable());
        attributeEntity.setDisplayName(attribute.displayName().toMapWithLanguageTagKeys());
        attributeEntity.setFormatSpecificAttributeName(attribute.attributeNameOverrides());
        return attributeEntity;
    }

    private MetadataAttributeEntity createNewAttribute(
            final MetadataEntity metadataEntity, final MetaAttribute attribute) {
        final var attributeEntity = new MetadataAttributeEntity();
        attributeEntity.setMetadataEntity(metadataEntity);
        attributeEntity.setAttributeKey(attribute.attributeKey());
        attributeEntity.setDisplayName(attribute.displayName().toMapWithLanguageTagKeys());
        return attributeEntity;
    }

    private CredentialSchemeDetailResponse toCredentialSchemeDetailResponse(
            final CredentialSchemeEntity credentialSchemeEntity) {
        final var attributes =
                credentialSchemeDataService
                        .findAttributesByCredentialScheme(credentialSchemeEntity)
                        .stream()
                        .map(CredentialSchemeUtils::toCredentialSchemeAttribute)
                        .toList();
        final var styles =
                credentialSchemeDataService
                        .findAllStylesByCredentialSchemeEntity(credentialSchemeEntity)
                        .stream()
                        .map(CredentialSchemeUtils::toCredentialSchemeStyleDetail)
                        .toList();
        final var metadata =
                metadataDataService
                        .findByCredentialScheme(credentialSchemeEntity)
                        .map(this::toCredentialSchemeMetadata)
                        .orElse(null);
        final var issuerDefinition = credentialSchemeEntity.getIssuerDefinition();

        return new CredentialSchemeDetailResponse(
                credentialSchemeEntity.getUuid(),
                credentialSchemeEntity.getCredentialIdentifier(),
                credentialSchemeEntity.getVersion(),
                credentialSchemeEntity.getDisplayName(),
                credentialSchemeEntity.getState(),
                attributes,
                styles,
                metadata,
                new IssuerSettings(
                        issuerDefinition.getId(),
                        credentialSchemeEntity.getKeyType(),
                        credentialSchemeEntity.getDoctype(),
                        credentialSchemeEntity.getNamespace(),
                        credentialSchemeEntity.getVct(),
                        credentialSchemeEntity.getSupportedCredentialTypes(),
                        credentialSchemeEntity.getIssClaimOverride(),
                credentialSchemeEntity.getKidOverride(),
                credentialSchemeEntity.getBbsCredentialType(),
                credentialSchemeEntity.getDefaultTrustSystem(),
                credentialSchemeEntity.getSigningKeyIds(),
                credentialSchemeEntity.getStatusListId(),
                credentialSchemeEntity.getCredentialOfferType(),
                credentialSchemeEntity.getIssuanceProfileId()),
                credentialSchemeEntity.getTenantId(),
                credentialSchemeEntity.getMaxBatchSize(),
                credentialSchemeEntity.getTemplateId());
    }

    private CredentialSchemeMetadata toCredentialSchemeMetadata(
            final MetadataEntity metadataEntity) {
        final var attributes =
                metadataDataService.findAllByMetadata(metadataEntity).stream()
                        .map(
                                metadataAttributeEntity ->
                                        new MetaAttribute(
                                                metadataAttributeEntity.getAttributeKey(),
                                                CredentialSchemeUtils.getLocalizedDisplayName(
                                                        metadataAttributeEntity.getDisplayName())))
                        .toList();
        return new CredentialSchemeMetadata(attributes);
    }

    private CredentialSchemeEntity insertCredentialScheme(
            final CredentialSchemeDetail credentialScheme,
            final int issuerId,
            final String tenantId)
            throws InvalidIssuerException {
        final var credentialSchemeEntity = new CredentialSchemeEntity();
        credentialSchemeEntity.setCredentialIdentifier(credentialScheme.credentialIdentifier());
        credentialSchemeEntity.setVersion(credentialScheme.version());
        credentialSchemeEntity.setDisplayName(credentialScheme.displayName());
        credentialSchemeEntity.setCreatedAt(Instant.now());
        credentialSchemeEntity.setState(CredentialSchemeState.CREATED);
        credentialSchemeEntity.setUuid(UUID.randomUUID());
        credentialSchemeEntity.setKeyType(credentialScheme.issuerSettings().issuerKeyType());
        credentialSchemeEntity.setDoctype(credentialScheme.issuerSettings().doctype());
        credentialSchemeEntity.setNamespace(credentialScheme.issuerSettings().namespace());
        credentialSchemeEntity.setSupportedCredentialTypes(
                credentialScheme.issuerSettings().supportedCredentialTypes());
        credentialSchemeEntity.setVct(credentialScheme.issuerSettings().vct());
        credentialSchemeEntity.setTenantId(tenantId);
        credentialSchemeEntity.setTemplateId(credentialScheme.templateId());
        credentialSchemeEntity.setMaxBatchSize(
                normalizeMaxBatchSize(credentialScheme.maxBatchSize()));
        credentialSchemeEntity.setIssClaimOverride(
                credentialScheme.issuerSettings().issClaimOverride());
        credentialSchemeEntity.setKidOverride(credentialScheme.issuerSettings().kidOverride());
        credentialSchemeEntity.setSigningKeyIds(credentialScheme.issuerSettings().signingKeyIds());
        credentialSchemeEntity.setStatusListId(credentialScheme.issuerSettings().statusListId());
        credentialSchemeEntity.setDefaultTrustSystem(
                effectiveTrustSystem(credentialScheme.issuerSettings()));
        credentialSchemeEntity.setCredentialOfferType(
                credentialScheme.issuerSettings().credentialOfferType());
        credentialSchemeEntity.setIssuanceProfileId(
                issuanceProfileId(credentialScheme.issuerSettings()));

        final var issuer =
                issuerDataService
                        .findById(issuerId)
                        .orElseThrow(() -> new InvalidIssuerException(issuerId));
        validateIdentityScope(issuer, tenantId);
        validateIssuanceProfileCompatibility(
                credentialSchemeEntity.getIssuanceProfileId(), issuer);
        validateSigningKeyAssignments(credentialScheme.issuerSettings(), issuer);
        credentialSchemeEntity.setIssuerDefinition(issuer);
        return credentialSchemeDataService.insertScheme(credentialSchemeEntity);
    }

    private void validateIdentityScope(IssuerDefinitionEntity identity, String tenantId) {
        if (identity.getTenantId() != null && !identity.getTenantId().equals(tenantId)) {
            throw new UnauthorizedAccessException(
                    "An organisation cannot use another organisation's identity.");
        }
    }

    private void validatePublishedBinding(
            CredentialSchemeEntity existing, CredentialSchemeDetail requested) {
        if (existing.getState() != CredentialSchemeState.PUBLISHED) return;
        var settings = requested.issuerSettings();
        var requestedTrust = effectiveTrustSystem(settings);
        if (!Objects.equals(existing.getIssuanceProfileId(), settings.issuanceProfileId())
                || existing.getDefaultTrustSystem() != requestedTrust
                || existing.getIssuerDefinition() == null
                || !Objects.equals(existing.getIssuerDefinition().getId(), settings.id())
                || !Objects.equals(existing.getSupportedCredentialTypes(),
                        settings.supportedCredentialTypes())) {
            throw new IllegalArgumentException(
                    "Published credential schema trust, profile, identity, and format are immutable");
        }
    }

    private void validateIssuanceProfile(IssuerSettings settings) {
        EcosystemProfile profile = EcosystemProfileCatalog.require(
                issuanceProfileId(settings), EcosystemProfileRole.ISSUANCE);
        requireSwissFormat(profile, settings);
        var legacyTrustSystem = settings.defaultTrustSystem();
        var profileTrustSystem = profile.policy().trustSystem();
        if (legacyTrustSystem != null
                && legacyTrustSystem != IssuerTrustSystem.Default
                && legacyTrustSystem != profileTrustSystem) {
            throw new IllegalArgumentException(
                    "Trust system is incompatible with issuance profile " + profile.id());
        }
    }

    private void requireSwissFormat(EcosystemProfile profile, IssuerSettings settings) {
        if (profile.family() != EcosystemProfileFamily.SWISS_SWIYU) return;
        var formats = settings.supportedCredentialTypes();
        if (formats == null || !formats.equals(Set.of(CredentialType.SD_JWT))) {
            throw new IllegalArgumentException(
                    "Swiss Profile 1.0 issuance supports SD-JWT VC only");
        }
    }

    private IssuerTrustSystem effectiveTrustSystem(IssuerSettings settings) {
        var profile = EcosystemProfileCatalog.require(
                settings.issuanceProfileId(), EcosystemProfileRole.ISSUANCE);
        return profile.policy().trustSystem() == null
                ? IssuerTrustSystem.Default : profile.policy().trustSystem();
    }

    private String issuanceProfileId(IssuerSettings settings) {
        return EcosystemProfileCatalog.require(
                settings.issuanceProfileId(), EcosystemProfileRole.ISSUANCE).id();
    }

    private void validateIssuanceProfileCompatibility(
            String profileId, IssuerDefinitionEntity identity) {
        if (identityKeySlotService == null) return;
        var profile = EcosystemProfileCatalog.require(profileId, EcosystemProfileRole.ISSUANCE);
        var trustSystem = profile.policy().trustSystem() == null
                ? IssuerTrustSystem.Default : profile.policy().trustSystem();
        var hasSlot = identityKeySlotService.slots(identity.getId()).stream()
                .anyMatch(slot -> slot.getType()
                        == org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.CREDENTIAL_SIGNING
                        && (slot.getTrustSystem() == trustSystem
                            || (trustSystem == IssuerTrustSystem.Custom
                                && slot.getTrustSystem() == IssuerTrustSystem.Default)));
        if (!hasSlot) {
            throw new IllegalArgumentException(
                    "Issuer identity has no credential signing slot for profile " + profile.id());
        }
        if (profile.family() == EcosystemProfileFamily.SWISS_SWIYU
                && identityKeySlotService.slots(identity.getId()).stream()
                .filter(slot -> slot.getType() == IdentityKeySlotType.IDENTITY_STATEMENT
                        && slot.getTrustSystem() == IssuerTrustSystem.Switzerland)
                .map(IdentityKeySlotEntity::getConfiguration)
                .noneMatch(configuration -> configuration != null
                        && !configuration.path("swissDid").asText("").isBlank())) {
            throw new IllegalArgumentException(
                    "Swiss issuance requires a published identity DID");
        }
    }

    private void validateIssuanceReadiness(
            CredentialSchemeEntity entity, String version) {
        if (issuerService == null) return;
        var profile = EcosystemProfileCatalog.require(
                effectiveIssuanceProfileId(entity), EcosystemProfileRole.ISSUANCE);
        var trustSystem = profile.policy().trustSystem() == null
                ? IssuerTrustSystem.Default : profile.policy().trustSystem();
        var issuer = entity.getIssuerDefinition();
        if (identityKeySlotService != null) identityKeySlotService.requireClient(
                issuer.getId(),
                org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotConsumer.ISSUER,
                Set.of(IdentityKeySlotType.CREDENTIAL_SIGNING));
        issuerService.getSigningConfiguration(
                        issuer.getSlug(), trustSystem, entity.getCredentialIdentifier(),
                        version, null, profile.id())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Issuer identity has no signing configuration for profile "
                                + profile.id()));
    }

    private String effectiveIssuanceProfileId(CredentialSchemeEntity entity) {
        return EcosystemProfileCatalog.require(
                entity.getIssuanceProfileId(), EcosystemProfileRole.ISSUANCE).id();
    }

    private int normalizeMaxBatchSize(final Integer maxBatchSize) {
        return maxBatchSize == null ? 1 : maxBatchSize;
    }

    private void validateMaxBatchSize(final Integer maxBatchSize)
            throws InvalidCredentialSchemeException {
        if (normalizeMaxBatchSize(maxBatchSize) < 1) {
            throw new InvalidCredentialSchemeException(
                    "Maximum batch size must be greater than or equal to 1.");
        }
    }

    private List<CredentialSchemeAttributeEntity> insertCredentialSchemeAttributes(
            final CredentialSchemeEntity credentialSchemeEntity,
            final List<CredentialSchemeAttribute> attributes) {
        final var attributeEntities =
                attributes.stream()
                        .map(attribute -> createNewAttribute(credentialSchemeEntity, attribute))
                        .toList();
        return credentialSchemeDataService.insertCredentialSchemeAttributes(attributeEntities);
    }

    private void insertStyles(
            final CredentialSchemeEntity credentialSchemeEntity,
            final List<CredentialSchemeStylePayload> stylePayloads,
            final List<CredentialSchemeAttributeEntity> attributes) {
        final var styles =
                stylePayloads.stream()
                        .map(
                                style -> {
                                    try {
                                        return createNewStyleEntity(
                                                credentialSchemeEntity, style, attributes);
                                    } catch (NoSuchAlgorithmException | JacksonException e) {
                                        logger.warn("Error generating oca bundle.");
                                    }
                                    return null;
                                })
                        .toList();
        credentialSchemeDataService.insertStyles(styles);
    }

    private CredentialSchemeStyleEntity createNewStyleEntity(
            final CredentialSchemeEntity credentialSchemeEntity,
            final CredentialSchemeStylePayload style,
            final List<CredentialSchemeAttributeEntity> attributes)
            throws NoSuchAlgorithmException, JacksonException {
        final var styleEntity = new CredentialSchemeStyleEntity();
        styleEntity.setCredentialSchemeEntity(credentialSchemeEntity);
        styleEntity.setStyle(objectMapper.writeValueAsString(style.style()));
        styleEntity.setOcaVersion(style.ocaVersion());
        final var ocaBundle = generateOcaBundle(credentialSchemeEntity, style, attributes, "");
        final var ocaBundleStr = OcaUtilsKt.toJson(ocaBundle);
        final var ocaBundleFileName = getOcaBundleHash(ocaBundle);
        styleEntity.setOcaBundle(ocaBundleStr);
        styleEntity.setOcaBundleFileName(ocaBundleFileName);
        ensureSwiyuBundle(styleEntity, attributes);
        return styleEntity;
    }

    private void ensureSwiyuBundle(
            CredentialSchemeStyleEntity style, List<CredentialSchemeAttributeEntity> attributes) {
        // Rebuild managed bundles so newly supported locale fallbacks reach existing styles.
        var scheme = style.getCredentialSchemeEntity();
        var name = Objects.requireNonNullElse(scheme.getDisplayName(), scheme.getCredentialIdentifier());
        var bundle = SwiyuBundleCreator.create(name, attributes, style.getStyle());
        var fileName = SwiyuBundleCreator.hash(bundle);
        if (!Objects.equals(bundle, style.getSwiyuOcaBundle())
                || !Objects.equals(fileName, style.getSwiyuOcaFileName())) {
            style.setSwiyuOcaBundle(bundle);
            style.setSwiyuOcaFileName(fileName);
        }
    }

    public OcaBundleJson generateOcaBundle(
            final CredentialSchemeEntity credentialSchemeEntity,
            final CredentialSchemeStylePayload style,
            final List<CredentialSchemeAttributeEntity> credentialSchemeAttributeEntities,
            final String ocaBundleFilename) {
        final var schemeMetadata =
                this.toCredentialSchemeMetadata(
                        metadataDataService
                                .findByCredentialScheme(credentialSchemeEntity)
                                .orElse(null));

        final var map =
                new HashMap<String, SchemaCreationRequestMultipleLanguages.AttributeDetails>();
        credentialSchemeAttributeEntities.forEach(
                attribute ->
                        map.put(
                                attribute.getFieldName(),
                                new SchemaCreationRequestMultipleLanguages.AttributeDetails(
                                        CredentialSchemeUtils.getLocalizedDisplayName(
                                                attribute.getDisplayName()),
                                        attribute.getFieldType(),
                                        attribute.isArray(),
                                        attribute.isSensitive(),
                                        attribute.isDisclosable())));
        final var schemaInformation =
                new SchemaInformationMultipleLanguages(
                        new SchemaIdentifiers(
                                credentialSchemeEntity.getCredentialIdentifier(),
                                credentialSchemeEntity.getVersion(),
                                credentialSchemeEntity.getState()
                                        == CredentialSchemeState.PUBLISHED,
                                style.textColor(),
                                style.cardColor(),
                                ocaBundleFilename,
                                credentialSchemeEntity.getCreatedAt(),
                                credentialSchemeEntity.getUpdatedAt()),
                        map,
                        style.style());
        return OcaUtilsKt.generateOcaBundle(schemaInformation, schemeMetadata);
    }
}
