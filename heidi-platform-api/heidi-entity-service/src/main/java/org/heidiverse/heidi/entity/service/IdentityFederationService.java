// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService;
import org.heidiverse.heidi.entity.data.service.IssuerDataService;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeState;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeEntity;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity;
import org.heidiverse.heidi.entity.model.issuer.FederationAuthority;
import org.heidiverse.heidi.entity.model.issuer.FederationCredentialIssuer;
import org.heidiverse.heidi.entity.model.issuer.FederationSubordinateCandidate;
import org.heidiverse.heidi.entity.model.issuer.IssuerFederation;
import org.heidiverse.heidi.entity.model.issuer.IssuerFederationResponse;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.shared.localized.LocalizedValue;
import org.heidiverse.heidi.shared.signing.SigningKeyRef;
import org.heidiverse.heidi.signing.adapters.ProviderJwsSigner;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes identities as OpenID Federation entities.
 *
 * <pre>
 * identity (OIDF identity-statement key assignment)
 *   ├─ {issuer}/{slug}/c/{credential}/{version}   leaf, one per credential schema version
 *   ├─ {verifier}/{slug}                          leaf
 *   └─ {platform}/federation/{slug}               authority, when the identity is one
 *        fetch?sub=…  subordinate statements for accepted identities that hint at it
 * </pre>
 *
 * <p>Every statement is signed through the signing service with the active version of the
 * identity's OIDF identity-statement key; the platform holds no separate federation key.
 */
@Service
public class IdentityFederationService {
    public static final String STATEMENT_MEDIA_TYPE = "application/entity-statement+jwt";

    private static final JOSEObjectType STATEMENT_TYPE = new JOSEObjectType("entity-statement+jwt");
    private static final Duration STATEMENT_LIFETIME = Duration.ofDays(1);
    private static final String ISSUANCE_VARIANT = "c";
    private static final String LOCAL_FEDERATION_KEY_PREFIX = "federation-";
    private static final String LOCAL_FEDERATION_ALGORITHM = "ES256";
    private static final List<CredentialSchemeState> ISSUED_STATES =
            List.of(CredentialSchemeState.PUBLISHED, CredentialSchemeState.ARCHIVED);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final IssuerDataService issuerDataService;
    private final CredentialSchemeDataService credentialSchemeDataService;
    private final SigningKeyService keyService;
    private final SigningProviderService providerService;
    private final IdentityKeySlotService identityKeySlotService;
    private final IssuerService issuerService;
    private final String platformBaseUrl;
    private final String issuerBaseUrl;
    private final String verifierBaseUrl;

    @Autowired
    public IdentityFederationService(
            IssuerDataService issuerDataService,
            CredentialSchemeDataService credentialSchemeDataService,
            SigningKeyService keyService,
            SigningProviderService providerService,
            IdentityKeySlotService identityKeySlotService,
            IssuerService issuerService,
            @Value("${heidi.platform.public-base-url}") String platformBaseUrl,
            @Value("${heidi.issuer.public-base-url}") String issuerBaseUrl,
            @Value("${heidi.verifier.public-base-url}") String verifierBaseUrl) {
        this.issuerDataService = issuerDataService;
        this.credentialSchemeDataService = credentialSchemeDataService;
        this.keyService = keyService;
        this.providerService = providerService;
        this.identityKeySlotService = identityKeySlotService;
        this.issuerService = issuerService;
        this.platformBaseUrl = trimSlash(platformBaseUrl);
        this.issuerBaseUrl = trimSlash(issuerBaseUrl);
        this.verifierBaseUrl = trimSlash(verifierBaseUrl);
    }

    public IdentityFederationService(
            IssuerDataService issuerDataService,
            CredentialSchemeDataService credentialSchemeDataService,
            SigningKeyService keyService,
            SigningProviderService providerService,
            String platformBaseUrl,
            String issuerBaseUrl,
            String verifierBaseUrl) {
        this(issuerDataService, credentialSchemeDataService, keyService, providerService, null,
                null,
                platformBaseUrl, issuerBaseUrl, verifierBaseUrl);
    }

    // ------------------------------------------------------------------ settings

    @Transactional(readOnly = true)
    public IssuerFederationResponse get(String tenantId, int identityId) {
        return response(visibleIdentity(tenantId, identityId));
    }

    @Transactional
    public IssuerFederationResponse update(
            String tenantId, int identityId, IssuerFederation request) {
        var identity = ownedIdentity(tenantId, identityId);
        var settings = (request == null ? IssuerFederation.disabled() : request).validated();

        if (settings.enabled()) {
            requireJwsKey(identity.getTenantId(), settings.signingKeyId());
        }
        validateCredentialSchemeSelection(identity, settings);
        for (var subordinateId : settings.subordinateIds()) {
            issuerDataService.findById(subordinateId)
                    .filter(candidate -> !candidate.isDeleted())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Subordinate identity not found: " + subordinateId));
        }

        identity.setFederation(settings);
        issuerDataService.save(identity);
        if (identityKeySlotService != null) {
            if (settings.enabled()) {
                identityKeySlotService.ensureKeySlot(
                    identity.getTenantId(), identity.getId(),
                    org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.IDENTITY_STATEMENT,
                    IssuerTrustSystem.OIDF, null, settings.signingKeyId(), null);
            } else {
                identityKeySlotService.remove(
                        identity.getTenantId(), identity.getId(),
                        org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.IDENTITY_STATEMENT,
                        IssuerTrustSystem.OIDF, null);
            }
        }
        if (issuerService != null) issuerService.reconcileSigningGrants();
        return response(identity);
    }

    /**
     * Makes the local identity its own trust anchor, so a local stack serves a complete federation
     * - authority, subordinate statements and leaves - without configuration. Local development
     * only; a deployment configures federation in the Cockpit.
     */
    @Transactional
    public void ensureLocalDevelopmentFederation(String slug) {
        var identity = issuerDataService.findBySlug(slug)
                .orElseThrow(() -> new IllegalStateException("Local identity is missing: " + slug));
        if (identity.getFederation().enabled()) {
            if (!signingKeyAvailable(identity)) rotateLocalKey(identity);
            return;
        }

        var key = keyService.create(
                identity.getTenantId(), LOCAL_FEDERATION_KEY_PREFIX + slug, LOCAL_FEDERATION_ALGORITHM,
                null, null);
        identity.setFederation(new IssuerFederation(
                key.keyId(), List.of(authorityEntityId(identity)), true, List.of(identity.getId())));
        issuerDataService.save(identity);
        if (identityKeySlotService != null) {
            identityKeySlotService.ensureKeySlot(
                    identity.getTenantId(), identity.getId(),
                    org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.IDENTITY_STATEMENT,
                    IssuerTrustSystem.OIDF, null, key.keyId(), null);
        }
        if (issuerService != null) issuerService.reconcileSigningGrants();
    }

    private boolean signingKeyAvailable(IssuerDefinitionEntity identity) {
        var tenantId = identity.getTenantId();
        var keyId = identity.getFederation().signingKeyId();
        try {
            var key = keyService.owned(tenantId, keyId);
            var active = keyService.activeVersion(tenantId, keyId);
            providerService.provider(tenantId, key.getProviderId()).resolve(active.getKeyUri());
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    // Local signing services may forget keys the database still references, e.g. an in-memory
    // provider after a restart. Rotating keeps the key, and with it the identity's settings.
    private void rotateLocalKey(IssuerDefinitionEntity identity) {
        var tenantId = identity.getTenantId();
        var keyId = identity.getFederation().signingKeyId();
        var prepared = keyService.prepareRotation(tenantId, keyId);
        keyService.activate(tenantId, keyId, prepared.keyVersionId());
        if (issuerService != null) issuerService.reconcileSigningGrants();
    }

    /** Authorities on this platform, offered as authority hints. */
    @Transactional(readOnly = true)
    public List<FederationAuthority> authorities() {
        return issuerDataService.findAll().stream()
                .filter(identity -> identity.getFederation().enabled()
                        && identity.getFederation().authority())
                .map(identity -> new FederationAuthority(
                        authorityEntityId(identity), identity.getSlug(), displayName(identity)))
                .toList();
    }

    // ------------------------------------------------------------------ published statements

    /** The authority's own entity configuration. */
    @Transactional(readOnly = true)
    public String authorityConfiguration(String slug) {
        var authority = authority(slug);
        var entityId = authorityEntityId(authority);

        var metadata = Map.<String, Object>of(
                "federation_entity", federationEntityMetadata(authority, Map.of(
                        "federation_fetch_endpoint", entityId + "/fetch",
                        "federation_list_endpoint", entityId + "/list")));
        return sign(authority, statement(authority, entityId, entityId)
                .claim("metadata", metadata)
                .build());
    }

    /** Entity identifiers of every accepted subordinate. */
    @Transactional(readOnly = true)
    public List<String> subordinates(String slug) {
        var authority = authority(slug);
        var entityId = authorityEntityId(authority);
        return acceptedSubordinates(authority).stream()
                .flatMap(subordinate -> subordinateEntityIds(subordinate).stream())
                .filter(subject -> !subject.equals(entityId))
                .toList();
    }

    /** The authority's statement about one subordinate entity. */
    @Transactional(readOnly = true)
    public String subordinateStatement(String slug, String subject) {
        var authority = authority(slug);
        var entityId = authorityEntityId(authority);
        if (entityId.equals(subject)) {
            throw new FederationInvalidRequestException(
                    "An authority issues no subordinate statement about itself");
        }

        var subordinate = acceptedSubordinates(authority).stream()
                .filter(candidate -> subordinateEntityIds(candidate).contains(subject))
                .findFirst()
                .orElseThrow(() -> new FederationEntityNotFoundException(
                        "Unknown subordinate: " + subject));

        return sign(authority, new JWTClaimsSet.Builder(baseClaims(entityId, subject))
                .claim("jwks", jwks(subordinate))
                .claim("source_endpoint", entityId + "/fetch")
                .build());
    }

    /** Signs a leaf entity configuration on behalf of the issuer or verifier backend. */
    @Transactional(readOnly = true)
    public String leafConfiguration(String slug, String entityId, Map<String, Object> metadata) {
        var identity = enabledIdentity(slug);
        if (!leafEntityIds(identity).contains(entityId)) {
            throw new FederationEntityNotFoundException(
                    "Not an entity of identity " + slug + ": " + entityId);
        }

        var combined = new LinkedHashMap<String, Object>(metadata == null ? Map.of() : metadata);
        combined.put("federation_entity", federationEntityMetadata(identity, Map.of()));
        return sign(identity, statement(identity, entityId, entityId)
                .claim("metadata", combined)
                .build());
    }

    // ------------------------------------------------------------------ entity identifiers

    /**
     * Leaf entities of an identity. A credential issuer is one only when credentials carry its URL
     * as {@code iss}: a schema overriding the claim, or signing under a DID, publishes nothing a
     * trust chain could be built from.
     */
    List<String> leafEntityIds(IssuerDefinitionEntity identity) {
        var entityIds = new ArrayList<String>();
        credentialSchemeDataService
                .findByIssuerDefinitionSlugFilteredByState(identity.getSlug(), ISSUED_STATES)
                .stream()
                .filter(schema -> issuesUnderUrl(identity, schema))
                .filter(schema -> credentialSchemeEnabled(identity.getFederation(), schema))
                .map(schema -> String.join("/",
                        issuerBaseUrl, identity.getSlug(), ISSUANCE_VARIANT,
                        schema.getCredentialIdentifier(), schema.getVersion()))
                .forEach(entityIds::add);
        entityIds.add(verifierBaseUrl + "/" + identity.getSlug());
        return entityIds;
    }

    private List<FederationCredentialIssuer> credentialIssuers(IssuerDefinitionEntity identity) {
        var settings = identity.getFederation();
        return credentialSchemeDataService
                .findByIssuerDefinitionSlugFilteredByState(identity.getSlug(), ISSUED_STATES)
                .stream()
                .map(schema -> {
                    var eligible = issuesUnderUrl(identity, schema);
                    var enabled = eligible && credentialSchemeEnabled(settings, schema);
                    var entityId = eligible ? credentialEntityId(identity, schema) : null;
                    return new FederationCredentialIssuer(
                            schema.getId(),
                            schema.getCredentialIdentifier(),
                            schema.getVersion(),
                            schema.getDisplayName(),
                            schema.getState(),
                            entityId,
                            eligible,
                            enabled);
                })
                .toList();
    }

    private String credentialEntityId(
            IssuerDefinitionEntity identity, CredentialSchemeEntity schema) {
        return String.join("/", issuerBaseUrl, identity.getSlug(), ISSUANCE_VARIANT,
                schema.getCredentialIdentifier(), schema.getVersion());
    }

    private boolean credentialSchemeEnabled(
            IssuerFederation settings, CredentialSchemeEntity schema) {
        return settings.credentialSchemeIds() == null
                || settings.credentialSchemeIds().contains(schema.getId());
    }

    private void validateCredentialSchemeSelection(
            IssuerDefinitionEntity identity, IssuerFederation settings) {
        if (settings.credentialSchemeIds() == null) return;

        var schemes = credentialSchemeDataService
                .findByIssuerDefinitionSlugFilteredByState(identity.getSlug(), ISSUED_STATES);
        for (var schemeId : settings.credentialSchemeIds()) {
            var scheme = schemes.stream()
                    .filter(candidate -> Objects.equals(candidate.getId(), schemeId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Credential scheme does not belong to identity: " + schemeId));
            if (!issuesUnderUrl(identity, scheme)) {
                throw new IllegalArgumentException(
                        "Credential scheme cannot be published as an issuer entity: " + schemeId);
            }
        }
    }

    String authorityEntityId(IssuerDefinitionEntity identity) {
        return platformBaseUrl + "/federation/" + identity.getSlug();
    }

    private List<String> subordinateEntityIds(IssuerDefinitionEntity subordinate) {
        var entityIds = new ArrayList<>(leafEntityIds(subordinate));
        if (subordinate.getFederation().authority()) {
            entityIds.add(authorityEntityId(subordinate));
        }
        return entityIds;
    }

    private boolean issuesUnderUrl(IssuerDefinitionEntity identity, CredentialSchemeEntity schema) {
        if (hasText(schema.getIssClaimOverride())) return false;

        var trustSystem = Optional.ofNullable(schema.getDefaultTrustSystem())
                .or(() -> Optional.ofNullable(identity.getDefaultTrustSystem()))
                .orElse(IssuerTrustSystem.Default);
        if (trustSystem == IssuerTrustSystem.Switzerland) return false;

        return trustRequest(identity, trustSystem)
                .or(() -> trustRequest(identity, IssuerTrustSystem.Default))
                .map(request -> !hasText(request.issuerClaim()))
                .orElse(true);
    }

    private Optional<org.heidiverse.heidi.entity.model.issuer.IssuerTrustConfigurationRequest> trustRequest(
            IssuerDefinitionEntity identity, IssuerTrustSystem trustSystem) {
        if (identityKeySlotService == null) return Optional.empty();
        return identityKeySlotService.slots(identity.getId()).stream()
                .filter(slot -> slot.getType()
                        == org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.IDENTITY_STATEMENT)
                .filter(slot -> slot.getTrustSystem() == trustSystem)
                .findFirst()
                .flatMap(slot -> {
                    try {
                        return Optional.ofNullable(JSON.treeToValue(
                                slot.getConfiguration(),
                                org.heidiverse.heidi.entity.model.issuer.IssuerTrustConfigurationRequest.class));
                    } catch (Exception exception) {
                        return Optional.empty();
                    }
                });
    }

    // ------------------------------------------------------------------ helpers

    // Both sides must agree: the authority lists the subordinate, the subordinate hints at it.
    private List<IssuerDefinitionEntity> acceptedSubordinates(IssuerDefinitionEntity authority) {
        var entityId = authorityEntityId(authority);
        return authority.getFederation().subordinateIds().stream()
                .map(issuerDataService::findById)
                .flatMap(Optional::stream)
                .filter(subordinate -> !subordinate.isDeleted()
                        && subordinate.getFederation().enabled()
                        && subordinate.getFederation().authorityHints().contains(entityId))
                .toList();
    }

    private IssuerFederationResponse response(IssuerDefinitionEntity identity) {
        var settings = identity.getFederation();
        var authorityEntityId = settings.authority() ? authorityEntityId(identity) : null;
        var candidates = settings.authority()
                ? issuerDataService.findAll().stream()
                        .map(candidate -> new FederationSubordinateCandidate(
                                candidate.getId(),
                                candidate.getSlug(),
                                displayName(candidate),
                                candidate.getFederation().authorityHints().contains(authorityEntityId)))
                        .filter(candidate -> candidate.hintsAuthority()
                                || settings.subordinateIds().contains(candidate.id())
                                || sameTenant(identity, candidate.id()))
                        .toList()
                : List.<FederationSubordinateCandidate>of();
        return new IssuerFederationResponse(
                settings, leafEntityIds(identity), authorityEntityId, candidates,
                credentialIssuers(identity));
    }

    private boolean sameTenant(IssuerDefinitionEntity identity, int candidateId) {
        return issuerDataService.findById(candidateId)
                .map(candidate -> Objects.equals(candidate.getTenantId(), identity.getTenantId()))
                .orElse(false);
    }

    private IssuerDefinitionEntity ownedIdentity(String tenantId, int identityId) {
        var identity = issuerDataService.findById(identityId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Identity not found: " + identityId));
        if (!Objects.equals(tenantId, identity.getTenantId())) {
            throw new SecurityException("Identity does not belong to tenant " + tenantId);
        }
        return identity;
    }

    private IssuerDefinitionEntity visibleIdentity(String tenantId, int identityId) {
        var identity = issuerDataService.findById(identityId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Identity not found: " + identityId));
        if (identity.getTenantId() != null
                && !Objects.equals(tenantId, identity.getTenantId())) {
            throw new SecurityException("Identity does not belong to tenant " + tenantId);
        }
        return identity;
    }

    private IssuerDefinitionEntity enabledIdentity(String slug) {
        return issuerDataService.findBySlug(slug)
                .filter(identity -> !identity.isDeleted() && identity.getFederation().enabled())
                .orElseThrow(() -> new FederationEntityNotFoundException(
                        "No federation entity for identity " + slug));
    }

    private IssuerDefinitionEntity authority(String slug) {
        var identity = enabledIdentity(slug);
        if (!identity.getFederation().authority()) {
            throw new FederationEntityNotFoundException("Identity is no federation authority: " + slug);
        }
        return identity;
    }

    private JWTClaimsSet.Builder statement(
            IssuerDefinitionEntity identity, String issuer, String subject) {
        var builder = new JWTClaimsSet.Builder(baseClaims(issuer, subject))
                .claim("jwks", jwks(identity));
        // An identity that is its own authority must not hint at itself from that entity.
        var hints = identity.getFederation().authorityHints().stream()
                .filter(hint -> !hint.equals(subject))
                .toList();
        if (!hints.isEmpty()) {
            builder.claim("authority_hints", hints);
        }
        return builder;
    }

    private static JWTClaimsSet baseClaims(String issuer, String subject) {
        var now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(STATEMENT_LIFETIME)))
                .build();
    }

    private Map<String, Object> federationEntityMetadata(
            IssuerDefinitionEntity identity, Map<String, Object> endpoints) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("organization_name", displayName(identity).coalesceOrGetAny("en"));
        if (hasText(identity.getLogo()) && identity.getLogo().startsWith("https://")) {
            metadata.put("logo_uri", identity.getLogo());
        }
        metadata.putAll(endpoints);
        return metadata;
    }

    // Active and previous versions, so statements signed before a rotation keep verifying.
    private Map<String, Object> jwks(IssuerDefinitionEntity identity) {
        var resolved = federationKey(identity);
        var active = resolved.version();

        var keys = new ArrayList<Map<String, Object>>();
        keys.add(publicJwk(active.getPublicJwk()));
        keyService.previousPublicJwks(
                        identity.getTenantId(), resolved.key().getId(), active.getId())
                .forEach(jwk -> keys.add(publicJwk(jwk)));
        identityKeySlotService.publicKeys(identity.getId(),
                        org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.IDENTITY_STATEMENT,
                        IssuerTrustSystem.OIDF)
                .forEach(jwk -> keys.add(publicJwk(jwk)));
        return Map.of("keys", keys.stream().distinct().toList());
    }

    private String sign(IssuerDefinitionEntity identity, JWTClaimsSet claims) {
        var resolved = federationKey(identity);
        var version = resolved.version();
        var providerId = resolved.slot().getProviderId() != null
                ? resolved.slot().getProviderId() : resolved.key().getProviderId();

        try {
            var key = new SigningKeyRef(
                    version.getKeyUri(), version.getPublicJwk(), version.getAlgorithm());
            var header = new JWSHeader.Builder(JWSAlgorithm.parse(version.getAlgorithm()))
                    .type(STATEMENT_TYPE)
                    .keyID(keyId(version.getPublicJwk()))
                    .build();
            var jwt = new SignedJWT(header, claims);
            jwt.sign(new ProviderJwsSigner(
                    providerService.provider(identity.getTenantId(), providerId), key));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(
                    "Could not sign entity statement for " + identity.getSlug(), exception);
        }
    }

    private IdentityKeySlotService.ResolvedKey federationKey(IssuerDefinitionEntity identity) {
        var configuredKey = identity.getFederation().signingKeyId();
        if (identityKeySlotService != null) {
            var resolved = identityKeySlotService.resolve(
                    identity.getTenantId(), identity.getId(),
                    org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.IDENTITY_STATEMENT,
                    IssuerTrustSystem.OIDF, null, null,
                    org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile.CREDENTIAL_SIGNING)
                    .filter(candidate -> candidate.key() != null);
            if (resolved.isPresent()) return requireJwsKey(resolved.get());
        }

        var key = keyService.owned(identity.getTenantId(), configuredKey);
        var version = requireJwsKey(identity.getTenantId(), configuredKey);
        return new IdentityKeySlotService.ResolvedKey(
                federationSlot(identity), key, version, List.of());
    }

    private org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity federationSlot(
            IssuerDefinitionEntity identity) {
        var slot = new org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity();
        slot.setIdentityId(identity.getId());
        slot.setType(org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.IDENTITY_STATEMENT);
        slot.setTrustSystem(IssuerTrustSystem.OIDF);
        slot.setKeyId(identity.getFederation().signingKeyId());
        return slot;
    }

    private SigningKeyVersionEntity requireJwsKey(
            String tenantId, UUID keyId) {
        var version = keyService.activeVersion(tenantId, keyId);
        requireJwsAlgorithm(version.getAlgorithm());
        return version;
    }

    private IdentityKeySlotService.ResolvedKey requireJwsKey(
            IdentityKeySlotService.ResolvedKey resolved) {
        requireJwsAlgorithm(resolved.version().getAlgorithm());
        return resolved;
    }

    private static void requireJwsAlgorithm(String algorithm) {
        if (!JWSAlgorithm.Family.SIGNATURE.contains(JWSAlgorithm.parse(algorithm))) {
            throw new IllegalArgumentException(
                    "OIDF identity-statement keys must sign JWS; unsupported algorithm " + algorithm);
        }
    }

    private static Map<String, Object> publicJwk(String jwk) {
        try {
            var parsed = JWK.parse(jwk).toPublicJWK();
            var json = new LinkedHashMap<>(parsed.toJSONObject());
            json.putIfAbsent("kid", parsed.computeThumbprint().toString());
            return json;
        } catch (ParseException | JOSEException exception) {
            throw new IllegalStateException("Invalid federation public key", exception);
        }
    }

    private static String keyId(String jwk) {
        return (String) publicJwk(jwk).get("kid");
    }

    private static LocalizedValue<String> displayName(IssuerDefinitionEntity identity) {
        var names = identity.getDisplayName();
        return LocalizedValue.fromStringMap(
                names == null || names.isEmpty() ? Map.of("en", identity.getSlug()) : names);
    }

    private static String trimSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Maps to the federation {@code not_found} error. */
    public static class FederationEntityNotFoundException extends RuntimeException {
        public FederationEntityNotFoundException(String message) {
            super(message);
        }
    }

    /** Maps to the federation {@code invalid_request} error. */
    public static class FederationInvalidRequestException extends RuntimeException {
        public FederationInvalidRequestException(String message) {
            super(message);
        }
    }
}
