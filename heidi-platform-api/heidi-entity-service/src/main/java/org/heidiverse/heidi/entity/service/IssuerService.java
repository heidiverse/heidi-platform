// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService;
import org.heidiverse.heidi.entity.data.service.IssuerDataService;
import org.heidiverse.heidi.entity.data.service.ProofSchemeDataService;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeEntity;
import org.heidiverse.heidi.entity.model.issuer.*;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfile;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileCatalog;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileRole;
import org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity;
import org.heidiverse.heidi.entity.model.signing.SigningKeyId;
import org.heidiverse.heidi.shared.localized.LocalizedValue;
import org.heidiverse.heidi.shared.signing.SigningKeyCapabilities;
import org.heidiverse.heidi.shared.signing.SigningKeyCreator;
import org.heidiverse.heidi.shared.signing.SigningKeyDeleter;
import org.heidiverse.heidi.shared.signing.SigningKeyImporter;
import org.heidiverse.heidi.shared.signing.SigningKeyRef;
import org.heidiverse.heidi.shared.signing.SigningKeyScope;
import org.heidiverse.heidi.shared.signing.SigningPurpose;
import org.heidiverse.heidi.shared.signing.SigningKeyProvider;

import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class IssuerService {
    private static final String LOCAL_DEVELOPMENT_KEY_ALGORITHM = "ES256";
    private static final String BBS_PRESENTATION_SETUP_OPERATION =
            "w3c.bbs-data-integrity-presentation-setup";
    private static final String SWISS_DID_REQUIRED = "Swiss identity requires its published DID";
    private static final Logger LOGGER = LoggerFactory.getLogger(IssuerService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IssuerDataService issuerDataService;
    private final CredentialSchemeDataService credentialSchemeDataService;
    private final SwissTrustStatementRefreshService swissTrustStatementRefreshService;
    private final SigningProviderService signingProviderService;
    private final SigningKeyService signingKeyService;
    private final SigningGrantService signingGrantService;
    private final IdentityKeySlotService identityKeySlotService;
    private final IdentityKeyIntegrityService identityKeyIntegrityService;
    private final ProofSchemeDataService proofSchemeDataService;

    private final org.heidiverse.heidi.entity.data.repository.SigningFlowRepository signingFlows;

    @org.springframework.beans.factory.annotation.Value("${heidi.platform.signing-flows.max-issuer-lifetime:P32D}")
    private java.time.Duration issuerFlowLifetime = java.time.Duration.ofDays(32);
    @org.springframework.beans.factory.annotation.Value("${heidi.platform.signing-flows.max-verifier-lifetime:PT1H}")
    private java.time.Duration verifierFlowLifetime = java.time.Duration.ofHours(1);
    @org.springframework.beans.factory.annotation.Value("${heidi.platform.localization.default-language:en}")
    private String defaultLanguage = "en";

    // Only a development stack registers this bean; production signs with certificates an
    // external CA on a Trusted List issued.
    @Autowired(required = false)
    private LocalDevelopmentTrustSeed developmentTrustSeed;

    @Autowired
    public IssuerService(
            IssuerDataService issuerDataService,
            CredentialSchemeDataService credentialSchemeDataService,
            SwissTrustStatementRefreshService swissTrustStatementRefreshService,
            SigningProviderService signingProviderService,
            SigningKeyService signingKeyService,
            SigningGrantService signingGrantService,
            IdentityKeySlotService identityKeySlotService,
            IdentityKeyIntegrityService identityKeyIntegrityService,
            ProofSchemeDataService proofSchemeDataService,
            org.heidiverse.heidi.entity.data.repository.SigningFlowRepository signingFlows) {
        this.issuerDataService = issuerDataService;
        this.credentialSchemeDataService = credentialSchemeDataService;
        this.swissTrustStatementRefreshService = swissTrustStatementRefreshService;
        this.signingProviderService = signingProviderService;
        this.signingKeyService = signingKeyService;
        this.signingGrantService = signingGrantService;
        this.identityKeySlotService = identityKeySlotService;
        this.identityKeyIntegrityService = identityKeyIntegrityService;
        this.proofSchemeDataService = proofSchemeDataService;
        this.signingFlows = signingFlows;
    }

    public IssuerOverview findAllIssuers() {
        return new IssuerOverview(
                issuerDataService.findAll().stream()
                        .map(this::toIssuerDefinitionResponse)
                        .toList());
    }

    public IssuerOverview findGlobalIdentities() {
        return new IssuerOverview(
                issuerDataService.findGlobal().stream()
                        .map(this::toIssuerDefinitionResponse)
                        .toList());
    }

    public IssuerOverview findIdentitiesAvailableToTenant(String tenantId) {
        return new IssuerOverview(
                issuerDataService.findAvailableToTenant(tenantId).stream()
                        .map(this::toIssuerDefinitionResponse)
                        .toList());
    }

    public Optional<IssuerDefinitionResponse> findIssuerById(int id) {
        return issuerDataService
                .findById(id)
                .map(this::toIssuerDefinitionResponse);
    }

    /** Build the public identity shape from slots once the slot model is available. */
    private IssuerDefinitionResponse toIssuerDefinitionResponse(IssuerDefinitionEntity identity) {
        var trustSystems = identityKeySlotService.slots(identity.getId()).stream()
                .filter(slot -> slot.getTrustSystem() != null)
                .map(slot -> slot.getTrustSystem() == IssuerTrustSystem.Default
                        && slot.getType() != IdentityKeySlotType.DECRYPTION
                        ? IssuerTrustSystem.Custom : slot.getTrustSystem())
                .filter(trustSystem -> trustSystem != IssuerTrustSystem.Default)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (identity.getFederation().enabled()) {
            trustSystems.add(IssuerTrustSystem.OIDF);
        }
        return identity.toSlotDefinitionResponse(trustSystems);
    }

    private String publicKeyToPem(
            String publicJwk,
            List<String> certificateChain,
            String algorithm) {
        try {
            byte[] publicKeyBytes;
            if (certificateChain != null && !certificateChain.isEmpty()) {
                var certificate = CertificateFactory.getInstance("X.509").generateCertificate(
                        new ByteArrayInputStream(Base64.getDecoder().decode(certificateChain.getFirst())));
                publicKeyBytes = certificate.getPublicKey().getEncoded();
            } else {
                var jwk = com.nimbusds.jose.jwk.JWK.parse(publicJwk).toPublicJWK();
                PublicKey publicKey = switch (jwk.getKeyType().getValue()) {
                    case "EC" -> jwk.toECKey().toPublicKey();
                    case "RSA" -> jwk.toRSAKey().toPublicKey();
                    case "OKP" -> jwk.toOctetKeyPair().toPublicKey();
                    default -> throw new IllegalArgumentException(
                            "PEM export is not supported for JWK key type " + jwk.getKeyType());
                };
                publicKeyBytes = publicKey.getEncoded();
            }
            var encoded = Base64.getMimeEncoder(64, "\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                    .encodeToString(publicKeyBytes);
            return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n";
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not export identity public key as PEM", exception);
        }
    }

    private ImportedSigningKey readPkcs12(
            byte[] pkcs12, String rawPassword, String algorithm, String keyId) {
        var password = rawPassword == null ? new char[0] : rawPassword.toCharArray();
        try {
            var keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(pkcs12), password);
            var aliases = keyStore.aliases();
            var privateKeyAliases = new ArrayList<String>();
            while (aliases.hasMoreElements()) {
                var alias = aliases.nextElement();
                if (keyStore.isKeyEntry(alias)) privateKeyAliases.add(alias);
            }
            if (privateKeyAliases.isEmpty()) {
                throw new IllegalArgumentException("PKCS#12 file does not contain a private key");
            }
            if (privateKeyAliases.size() > 1) {
                throw new IllegalArgumentException(
                        "PKCS#12 file contains multiple private keys; import a file with exactly one key");
            }

            var alias = privateKeyAliases.getFirst();
            Key key = keyStore.getKey(alias, password);
            if (!(key instanceof PrivateKey privateKey) || privateKey.getEncoded() == null) {
                throw new IllegalArgumentException(
                        "PKCS#12 private key cannot be exported for provider import");
            }
            var certificates = keyStore.getCertificateChain(alias);
            java.security.cert.X509Certificate leaf = null;
            List<String> chain = List.of();
            if (certificates != null && certificates.length > 0) {
                if (!(certificates[0] instanceof java.security.cert.X509Certificate x509Leaf)) {
                    throw new IllegalArgumentException("PKCS#12 leaf certificate must be X.509");
                }
                leaf = x509Leaf;
                chain = Arrays.stream(certificates).map(certificate -> {
                    try {
                        return Base64.getEncoder().encodeToString(certificate.getEncoded());
                    } catch (Exception exception) {
                        throw new IllegalArgumentException(
                                "Could not read PKCS#12 certificate chain", exception);
                    }
                }).toList();
                validateCertificateChain(chain);
            }

            try (var joseKeyPair = new uniffi.heidi_signing.JoseKeyPair(
                    privateKey.getEncoded(), algorithm)) {
                var publicJwk = withPublicJwkMetadata(
                        joseKeyPair.toPublicJwk(), keyId, algorithm);
                var publicKey = leaf == null
                        ? publicKeyFromJwk(publicJwk)
                        : leaf.getPublicKey();
                if (!chain.isEmpty()) {
                    validateCertificateMatchesKey(chain, algorithm, keyId, publicJwk);
                }
                return new ImportedSigningKey(
                        privateJwk(privateKey, publicKey, algorithm, keyId),
                        publicJwk,
                        chain);
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Could not import PKCS#12 file. Check the password and key algorithm", exception);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private String privateJwk(
            PrivateKey privateKey, PublicKey publicKey, String algorithm, String keyId) {
        try {
            JWK jwk;
            if (privateKey instanceof ECPrivateKey ecPrivate
                    && publicKey instanceof ECPublicKey ecPublic) {
                jwk = new ECKey.Builder(
                                Curve.forECParameterSpec(ecPublic.getParams()), ecPublic)
                        .privateKey(ecPrivate)
                        .keyID(keyId)
                        .algorithm(JWSAlgorithm.parse(algorithm))
                        .build();
            } else if (privateKey instanceof RSAPrivateKey rsaPrivate
                    && publicKey instanceof RSAPublicKey rsaPublic) {
                jwk = new RSAKey.Builder(rsaPublic)
                        .privateKey(rsaPrivate)
                        .keyID(keyId)
                        .algorithm(JWSAlgorithm.parse(algorithm))
                        .build();
            } else if (privateKey instanceof EdECPrivateKey edPrivate
                    && publicKey instanceof EdECPublicKey edPublic) {
                var curve = Curve.forStdName(edPublic.getParams().getName());
                var publicBytes = edPublicBytes(edPublic);
                var privateBytes = edPrivate.getBytes().orElseThrow(
                        () -> new IllegalArgumentException("EdEC private key has no raw key bytes"));
                if (!Curve.Ed25519.equals(curve)
                        || publicBytes.length != 32
                        || privateBytes.length != 32) {
                    throw new IllegalArgumentException(
                            "Only Ed25519 PKCS#12 keys are supported for EdDSA");
                }
                jwk = new OctetKeyPair.Builder(curve, Base64URL.encode(publicBytes))
                        .d(Base64URL.encode(privateBytes))
                        .keyID(keyId)
                        .algorithm(JWSAlgorithm.parse(algorithm))
                        .build();
            } else {
                throw new IllegalArgumentException(
                        "Unsupported PKCS#12 private key type: " + privateKey.getAlgorithm());
            }
            return jwk.toJSONString();
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Could not convert PKCS#12 private key to a private JWK", exception);
        }
    }

    private PublicKey publicKeyFromJwk(String publicJwk) throws Exception {
        var jwk = JWK.parse(publicJwk).toPublicJWK();
        return switch (jwk.getKeyType().getValue()) {
            case "EC" -> jwk.toECKey().toPublicKey();
            case "RSA" -> jwk.toRSAKey().toPublicKey();
            case "OKP" -> jwk.toOctetKeyPair().toPublicKey();
            default -> throw new IllegalArgumentException(
                    "Unsupported PKCS#12 public key type: " + jwk.getKeyType());
        };
    }

    private byte[] edPublicBytes(EdECPublicKey publicKey) {
        var point = publicKey.getPoint();
        var y = point.getY().toByteArray();
        var encoded = new byte[32];
        for (int index = 0; index < y.length; index++) {
            var source = y.length - 1 - index;
            if (index >= encoded.length) {
                if (y[source] != 0) {
                    throw new IllegalArgumentException("EdEC public key coordinate is too large");
                }
                break;
            }
            encoded[index] = y[source];
        }
        if (point.isXOdd()) encoded[encoded.length - 1] |= (byte) 0x80;
        return encoded;
    }

    private record ImportedSigningKey(
            String privateJwk, String publicJwk, List<String> certificateChain) {}

    private void validateCertificateChain(List<String> chain) {
        if (chain.isEmpty()) {
            throw new IllegalArgumentException("Certificate chain must contain a leaf certificate");
        }
        try {
            var factory = CertificateFactory.getInstance("X.509");
            var certificates = chain.stream().map(encoded -> {
                try {
                    return (java.security.cert.X509Certificate) factory.generateCertificate(
                            new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
                } catch (Exception exception) {
                    throw new IllegalArgumentException("Invalid X.509 certificate in trust chain", exception);
                }
            }).toList();
            for (var certificate : certificates) certificate.checkValidity();
            for (int index = 0; index < certificates.size() - 1; index++) {
                certificates.get(index).verify(certificates.get(index + 1).getPublicKey());
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Certificates do not form a valid leaf-to-root trust chain", exception);
        }
    }

    public IssuerTrustConfigurationResponse getIssuerTrustConfiguration(
            String tenantId, int issuerId, IssuerTrustSystem trustSystem, Set<Integer> allowedIssuerIds) {
        validateAssignments(Set.of(issuerId), allowedIssuerIds);
        return slotTrustResponse(issuerId, trustSystem);
    }

    public List<SwissVerificationQuery> listSwissVerificationQueries(
            int issuerId, Set<Integer> allowedIssuerIds) {
        validateAssignments(Set.of(issuerId), allowedIssuerIds);
        var issuer = issuerDataService.findById(issuerId)
                .orElseThrow(() -> new IllegalArgumentException("Issuer not found: " + issuerId));
        var slot = findTrustSlot(issuerId, IssuerTrustSystem.Switzerland);
        return swissTrustStatementRefreshService.listVerificationQueryStatements(
                issuer, slotTrustRequest(slot.getConfiguration()));
    }

    @Transactional
    public IssuerTrustConfigurationResponse updateIssuerTrustConfiguration(
            String tenantId,
            int issuerId,
            IssuerTrustSystem trustSystem,
            IssuerTrustConfigurationRequest request,
            Set<Integer> allowedIssuerIds) {
        validateAssignments(Set.of(issuerId), allowedIssuerIds);
        return updateSlotTrustConfiguration(issuerId, trustSystem, request);
    }

    public Optional<IssuerTrustConfigurationResponse> getIssuerTrustConfiguration(
            String issuerSlug, IssuerTrustSystem trustSystem) {
        return issuerDataService.findBySlug(issuerSlug)
                .filter(issuer -> identityKeySlotService.slots(issuer.getId()).stream()
                        .anyMatch(slot -> slot.getType() == IdentityKeySlotType.IDENTITY_STATEMENT
                                && slot.getTrustSystem() == trustSystem))
                .map(issuer -> slotTrustResponse(issuer.getId(), trustSystem));
    }

    @Transactional
    public IssuerTrustConfigurationResponse refreshSwissTrustConfiguration(
            int issuerId, Set<Integer> allowedIssuerIds) {
        validateAssignments(Set.of(issuerId), allowedIssuerIds);
        return refreshSlotSwissTrustConfiguration(issuerId);
    }

    /** Stores the EUDI access certificate for an issuer identity-statement slot. */
    @Transactional
    public IssuerTrustConfigurationResponse setEudiTrustCertificateChain(
            int issuerId, String keyId, CertificateChainRequest request,
            Set<Integer> allowedIssuerIds) {
        validateAssignments(Set.of(issuerId), allowedIssuerIds);
        var issuer = issuerDataService.findById(issuerId)
                .orElseThrow(() -> new IllegalArgumentException("Issuer not found: " + issuerId));
        var resolved = identityKeySlotService.resolve(
                        issuer.getTenantId(), issuerId, IdentityKeySlotType.IDENTITY_STATEMENT,
                        IssuerTrustSystem.EUDI, null, keyId, SigningCertificateProfile.ACCESS)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Identity has no EUDI identity-statement key " + keyId));
        if (resolved.version() == null) {
            throw new IllegalArgumentException("Identity key has no active version: " + keyId);
        }
        var chain = normalizeCertificateChain(request.certificateChain());
        validateCertificateChain(chain);
        validateCertificateMatchesKey(
                chain, resolved.version().getAlgorithm(), keyId, resolved.version().getPublicJwk());
        var certificateId = signingKeyService.setCertificateChain(
                issuer.getTenantId(), resolved.key().getId(), resolved.version().getId(), chain,
                SigningCertificateProfile.ACCESS, SigningCertificateSource.IMPORTED,
                IssuerTrustSystem.EUDI);
        var slot = resolved.slot();
        identityKeySlotService.save(issuer.getTenantId(), issuerId, new IdentityKeySlotRequest(
                slot.getId(), slot.getType(), slot.getTrustSystem(), slot.getOperation(),
                slot.getKeyId(), slot.getProviderId(), certificateId, slot.getOrder(),
                slot.getConfiguration(), slot.getConsumer()));
        return slotTrustResponse(issuerId, IssuerTrustSystem.EUDI);
    }

    /** Exports the public half of the identity-statement key selected by its slot. */
    public String exportIdentityPublicKey(
            int issuerId, IssuerTrustSystem trustSystem, String keyId, PublicKeyFormat format,
            Set<Integer> allowedIssuerIds) {
        validateAssignments(Set.of(issuerId), allowedIssuerIds);
        var issuer = issuerDataService.findById(issuerId)
                .orElseThrow(() -> new IllegalArgumentException("Issuer not found: " + issuerId));
        var resolved = identityKeySlotService.resolve(
                        issuer.getTenantId(), issuerId, IdentityKeySlotType.IDENTITY_STATEMENT,
                        trustSystem, null, keyId, trustCertificateProfile(trustSystem))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Identity has no key for trust system " + trustSystem));
        var version = resolved.version();
        if (version == null || !hasText(version.getPublicJwk())) {
            throw new IllegalArgumentException("Identity key has no public key to export: " + keyId);
        }
        return format == PublicKeyFormat.PEM
                ? publicKeyToPem(version.getPublicJwk(), resolved.certificateChain(), version.getAlgorithm())
                : version.getPublicJwk();
    }

    private void validateAssignments(Set<Integer> issuerIds, Set<Integer> allowedIssuerIds) {
        if (allowedIssuerIds == null || !allowedIssuerIds.containsAll(issuerIds)) {
            throw new SecurityException(
                    "An identity can only be used by issuers allowed for the tenant");
        }
    }

    private IssuerTrustConfigurationResponse updateSlotTrustConfiguration(
            int issuerId, IssuerTrustSystem trustSystem, IssuerTrustConfigurationRequest request) {
        var issuer = issuerDataService.findById(issuerId)
                .orElseThrow(() -> new IllegalArgumentException("Issuer not found: " + issuerId));
        var slot = findTrustSlot(issuerId, trustSystem);
        var current = slotTrustRequest(slot.getConfiguration());
        var merged = mergeTrustRequest(current, request);
        validateSlotTrustRequest(trustSystem, merged);
        identityKeySlotService.updateConfiguration(
                issuer.getTenantId(), issuerId, IdentityKeySlotType.IDENTITY_STATEMENT,
                trustSystem, null, OBJECT_MAPPER.valueToTree(merged));
        if (merged.eudiVerificationTrustAnchors() != null) {
            merged.eudiVerificationTrustAnchors().forEach(this::validateTrustAnchorCertificate);
            issuer.setEudiVerificationTrustAnchors(merged.eudiVerificationTrustAnchors());
        }
        if (merged.eudiTrustOwnPki() != null) {
            issuer.setEudiTrustOwnPki(merged.eudiTrustOwnPki());
        }
        if (merged.swissVerificationTrustAnchor() != null) {
            var anchor = merged.swissVerificationTrustAnchor().trim();
            if (!anchor.isEmpty() && !anchor.startsWith("did:webvh:") && !anchor.startsWith("did:tdw:")) {
                throw new IllegalArgumentException(
                        "Swiss verification trust anchor must use did:webvh or did:tdw");
            }
            issuer.setSwissVerificationTrustAnchor(anchor.isEmpty() ? null : anchor);
        }
        issuerDataService.save(issuer);
        return slotTrustResponse(issuerId, trustSystem);
    }

    private IssuerTrustConfigurationResponse refreshSlotSwissTrustConfiguration(int issuerId) {
        var issuer = issuerDataService.findById(issuerId)
                .orElseThrow(() -> new IllegalArgumentException("Issuer not found: " + issuerId));
        var slot = findTrustSlot(issuerId, IssuerTrustSystem.Switzerland);
        var current = slotTrustRequest(slot.getConfiguration());
        if (!hasText(current.swissDid()) || !hasText(current.swissRegistryBaseUrl())) {
            throw new IllegalArgumentException(
                    "Swiss trust configuration requires a DID and registry URL");
        }
        var result = swissTrustStatementRefreshService.refresh(
                current.swissDid(), current.swissRegistryBaseUrl());
        var refreshed = new IssuerTrustConfigurationRequest(
                current.issuerClaim(), current.swissDid(), current.swissRegistryBaseUrl(),
                current.swissStatusRegistryApiUrl(), current.swissStatusRegistryPartnerId(),
                current.swissTrustRegistryAuthoringUrl(), current.swissTrustRegistryTokenUrl(),
                current.swissTrustRegistryClientId(), current.swissTrustRegistryClientSecret(),
                current.swissTrustRegistryRefreshToken(),
                result.identityStatement(), result.issuanceStatements(), current.eudiVerificationTrustAnchors(),
                current.swissVerificationTrustAnchor());
        identityKeySlotService.updateConfiguration(
                issuer.getTenantId(), issuerId, IdentityKeySlotType.IDENTITY_STATEMENT,
                IssuerTrustSystem.Switzerland, null, OBJECT_MAPPER.valueToTree(refreshed));
        issuerDataService.save(issuer);
        return slotTrustResponse(issuerId, IssuerTrustSystem.Switzerland);
    }

    private IssuerTrustConfigurationResponse slotTrustResponse(
            int issuerId, IssuerTrustSystem trustSystem) {
        var issuer = issuerDataService.findById(issuerId)
                .orElseThrow(() -> new IllegalArgumentException("Issuer not found: " + issuerId));
        var slot = findTrustSlot(issuerId, trustSystem);
        var request = slotTrustRequest(slot.getConfiguration());
        var resolved = identityKeySlotService.resolve(
                issuer.getTenantId(), issuerId, IdentityKeySlotType.IDENTITY_STATEMENT,
                trustSystem, null, null, trustCertificateProfile(trustSystem));
        var chains = resolved.map(key -> List.of(new IssuerTrustConfigurationResponse.IssuerCertificateChain(
                        key.key() == null ? null : key.key().getId(),
                        key.key() == null ? null : key.key().getLogicalKeyId(),
                        key.version() == null ? null : key.version().getAlgorithm(),
                        key.certificateChain())))
                .orElse(List.of());
        var chainAvailable = chains.stream().anyMatch(chain -> !chain.certificateChain().isEmpty());
        return new IssuerTrustConfigurationResponse(
                issuerId, trustSystem, chainAvailable, chains,
                request.issuerClaim(), request.swissDid(), request.swissRegistryBaseUrl(),
                request.swissStatusRegistryApiUrl(), request.swissStatusRegistryPartnerId(),
                request.swissTrustRegistryAuthoringUrl(), request.swissTrustRegistryTokenUrl(),
                request.swissTrustRegistryClientId(),
                hasText(request.swissTrustRegistryTokenUrl())
                        && hasText(request.swissTrustRegistryClientId())
                        && hasText(request.swissTrustRegistryClientSecret())
                        && hasText(request.swissTrustRegistryRefreshToken()),
                validStatement(request.swissIdentityStatement()), validStatementExpiry(request.swissIdentityStatement()),
                validStatements(request.swissIssuanceStatements()), earliestExpiry(request.swissIssuanceStatements()),
                issuer.getEudiVerificationTrustAnchors(), issuer.getSwissVerificationTrustAnchor(),
                issuer.isEudiTrustOwnPki());
    }

    private IdentityKeySlotEntity findTrustSlot(int issuerId, IssuerTrustSystem trustSystem) {
        return identityKeySlotService.slots(issuerId).stream()
                .filter(slot -> slot.getType() == IdentityKeySlotType.IDENTITY_STATEMENT)
                .filter(slot -> slot.getTrustSystem() == trustSystem)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Identity has no identity-statement slot for trust system " + trustSystem));
    }

    private IssuerTrustConfigurationRequest slotTrustRequest(JsonNode node) {
        if (node == null || node.isNull()) return new IssuerTrustConfigurationRequest(
                null, null, null, null, null, null, null);
        try {
            return OBJECT_MAPPER.treeToValue(node, IssuerTrustConfigurationRequest.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid identity trust configuration", exception);
        }
    }

    private IssuerTrustConfigurationRequest mergeTrustRequest(
            IssuerTrustConfigurationRequest current, IssuerTrustConfigurationRequest update) {
        if (update == null) return current;
        return new IssuerTrustConfigurationRequest(
                update.issuerClaim() == null ? current.issuerClaim() : update.issuerClaim(),
                update.swissDid() == null ? current.swissDid() : update.swissDid(),
                update.swissRegistryBaseUrl() == null ? current.swissRegistryBaseUrl() : update.swissRegistryBaseUrl(),
                update.swissStatusRegistryApiUrl() == null
                        ? current.swissStatusRegistryApiUrl() : update.swissStatusRegistryApiUrl(),
                update.swissStatusRegistryPartnerId() == null
                        ? current.swissStatusRegistryPartnerId() : update.swissStatusRegistryPartnerId(),
                update.swissTrustRegistryAuthoringUrl() == null
                        ? current.swissTrustRegistryAuthoringUrl()
                        : update.swissTrustRegistryAuthoringUrl(),
                update.swissTrustRegistryTokenUrl() == null
                        ? current.swissTrustRegistryTokenUrl()
                        : update.swissTrustRegistryTokenUrl(),
                update.swissTrustRegistryClientId() == null
                        ? current.swissTrustRegistryClientId()
                        : update.swissTrustRegistryClientId(),
                update.swissTrustRegistryClientSecret() == null
                        ? current.swissTrustRegistryClientSecret()
                        : update.swissTrustRegistryClientSecret(),
                update.swissTrustRegistryRefreshToken() == null
                        ? current.swissTrustRegistryRefreshToken()
                        : update.swissTrustRegistryRefreshToken(),
                update.swissIdentityStatement() == null ? current.swissIdentityStatement() : update.swissIdentityStatement(),
                update.swissIssuanceStatements() == null ? current.swissIssuanceStatements() : update.swissIssuanceStatements(),
                update.eudiVerificationTrustAnchors() == null ? current.eudiVerificationTrustAnchors() : update.eudiVerificationTrustAnchors(),
                update.swissVerificationTrustAnchor() == null ? current.swissVerificationTrustAnchor() : update.swissVerificationTrustAnchor(),
                update.eudiTrustOwnPki() == null ? current.eudiTrustOwnPki() : update.eudiTrustOwnPki());
    }

    private void validateSlotTrustRequest(
            IssuerTrustSystem trustSystem, IssuerTrustConfigurationRequest request) {
        if (trustSystem == IssuerTrustSystem.Switzerland
                && hasText(request.swissDid()) && !request.swissDid().startsWith("did:webvh:")) {
            throw new IllegalArgumentException("Swiss issuer DID must use did:webvh");
        }
        if (trustSystem == IssuerTrustSystem.Switzerland
                && hasText(request.issuerClaim()) && !request.issuerClaim().startsWith("did:webvh:")) {
            throw new IllegalArgumentException("Swiss issuer claim must use did:webvh");
        }
        if (hasText(request.swissIdentityStatement())) jwtExpiry(request.swissIdentityStatement());
        if (request.swissIssuanceStatements() != null) {
            request.swissIssuanceStatements().values().forEach(this::jwtExpiry);
        }
    }

    private String validStatement(String statement) {
        return hasText(statement) && statementExpiry(statement).isAfter(Instant.now()) ? statement : null;
    }

    private Map<String, String> validStatements(Map<String, String> statements) {
        if (statements == null) return Map.of();
        return statements.entrySet().stream()
                .filter(entry -> hasText(entry.getValue()) && statementExpiry(entry.getValue()).isAfter(Instant.now()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Instant statementExpiry(String statement) {
        return hasText(statement) ? jwtExpiry(statement) : Instant.MIN;
    }

    private Instant validStatementExpiry(String statement) {
        return hasText(statement) && statementExpiry(statement).isAfter(Instant.now())
                ? statementExpiry(statement) : null;
    }

    private Instant earliestExpiry(Map<String, String> statements) {
        return validStatements(statements).values().stream()
                .map(this::statementExpiry).min(Comparator.naturalOrder()).orElse(null);
    }

    private void validateTrustAnchorCertificate(String encoded) {
        if (!hasText(encoded)) {
            throw new IllegalArgumentException("EUDI verification trust anchors must not be empty");
        }
        try {
            var normalized = encoded
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");
            var certificate = (java.security.cert.X509Certificate)
                    CertificateFactory.getInstance("X.509").generateCertificate(
                            new ByteArrayInputStream(Base64.getDecoder().decode(normalized)));
            certificate.checkValidity();
            if (certificate.getBasicConstraints() < 0) {
                throw new IllegalArgumentException("EUDI verification trust anchor must be a CA certificate");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid EUDI verification trust anchor", exception);
        }
    }

    private void validateCertificateMatchesKey(
            List<String> chain, String algorithm, String keyId, String expectedJwk) {
        if (chain == null || chain.isEmpty()) throw new IllegalArgumentException("Certificate chain is required");
        try {
            var encoded = chain.getFirst()
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");
            var certificate = CertificateFactory.getInstance("X.509").generateCertificate(
                    new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
            var certificateJwk = OBJECT_MAPPER.readTree(
                    uniffi.heidi_signing.Heidi_signing_jvmKt.publicJwkFromDer(
                            certificate.getPublicKey().getEncoded(), algorithm, keyId));
            var configuredJwk = OBJECT_MAPPER.readTree(expectedJwk);
            for (var field : List.of("kty", "crv", "x", "y", "n", "e")) {
                if (configuredJwk.has(field)
                        && !Objects.equals(configuredJwk.get(field), certificateJwk.get(field))) {
                    throw new IllegalArgumentException("Certificate does not match identity key " + keyId);
                }
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid X.509 signing certificate", exception);
        }
    }

    private boolean hasPublicKeyMaterial(String issuerJwk) {
        if (!hasText(issuerJwk)) return false;
        try {
            var json = OBJECT_MAPPER.readTree(issuerJwk);
            return json != null && (json.has("x") || json.has("n"));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Issuer JWK must be a valid JSON object", exception);
        }
    }

    private List<String> normalizeCertificateChain(List<String> chain) {
        if (chain == null) return List.of();
        return chain.stream().map(certificate -> certificate
                        .replace("-----BEGIN CERTIFICATE-----", "")
                        .replace("-----END CERTIFICATE-----", "")
                        .replaceAll("\\s", ""))
                .filter(IssuerService::hasText)
                .toList();
    }

    private Instant jwtExpiry(String compactJwt) {
        try {
            var parts = compactJwt.split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException("Trust statement must be a compact JWT");
            var payload = OBJECT_MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!payload.has("exp")) throw new IllegalArgumentException("Trust statement JWT must contain exp");
            return Instant.ofEpochSecond(payload.get("exp").asLong());
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid trust statement JWT", exception);
        }
    }

    /** Creates an identity; keys and roles are configured through identity slots. */
    @Transactional
    public IssuerDefinitionResponse createIssuer(IssuerDefinitionRequest issuerDefinition) {
        return createIssuer(issuerDefinition, null);
    }

    @Transactional
    public IssuerDefinitionResponse createIssuer(
            IssuerDefinitionRequest issuerDefinition, String tenantId) {
        validateSlotTrustSettings(issuerDefinition);
        var entity = IssuerDefinitionEntity.fromIssuerDefinition(issuerDefinition);
        entity.setTenantId(tenantId);
        var saved = issuerDataService.save(entity);
        writeGrantsForAllIdentities();
        return toIssuerDefinitionResponse(saved);
    }

    public IssuerDefinitionResponse updateIssuer(int id, IssuerDefinitionRequest issuer) {
        validateSlotTrustSettings(issuer);
        var existing = issuerDataService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Issuer not found for ID: " + id));
        existing.setSlug(issuer.slug());
        existing.setLogo(issuer.logo());
        existing.setDisplayName(issuer.displayName().toMapWithLanguageTagKeys());
        existing.setDefaultTrustSystem(issuer.defaultTrustSystem());
        existing.setCustomProfileName(issuer.customProfileName());
        var saved = issuerDataService.save(existing);
        writeGrantsForAllIdentities();
        return toIssuerDefinitionResponse(saved);
    }

    public boolean deleteIssuerById(int id) {
        var deleted = issuerDataService.softDeleteById(id);
        if (deleted) writeGrantsForAllIdentities();
        return deleted;
    }

    public IssuerDefinitionResponse updateTenantIdentity(
            String tenantId, int id, IssuerDefinitionRequest identity) {
        requireTenantIdentity(tenantId, id);
        return updateIssuer(id, identity);
    }

    /** The encryption policy the issuer backend applies to this issuer's OID4VCI messages. */
    public Optional<IssuerCredentialEncryption> getCredentialEncryption(
            String issuerSlug, String issuanceProfileId) {
        var profile = EcosystemProfileCatalog.require(issuanceProfileId, EcosystemProfileRole.ISSUANCE);
        return issuerDataService.findBySlug(issuerSlug)
                .map(issuer -> credentialEncryption(
                        issuer,
                        applyIssuanceProfile(issuer.getCredentialEncryption(), profile),
                        profile.policy().trustSystem() == null
                                ? IssuerTrustSystem.Default : profile.policy().trustSystem()));
    }

    private IssuerCredentialEncryption applyIssuanceProfile(
            IssuerCredentialEncryption policy, EcosystemProfile profile) {
        var encryption = profile.policy().encryptionAlgorithms();
        var keyAlgorithms = encryption.alg().supported();
        var contentAlgorithms = encryption.enc().supported();
        var compression = encryption.zip().supported();
        if (keyAlgorithms.isEmpty() && contentAlgorithms.isEmpty() && compression.isEmpty()) {
            return policy;
        }
        return new IssuerCredentialEncryption(
                constrain(policy.requestAlgValues(), keyAlgorithms),
                constrain(policy.requestEncValues(), contentAlgorithms),
                constrain(policy.requestZipValues(), compression),
                constrain(policy.responseAlgValues(), keyAlgorithms),
                constrain(policy.responseEncValues(), contentAlgorithms),
                constrain(policy.responseZipValues(), compression),
                encryption.required() ? Boolean.TRUE : policy.requestEncryptionRequired(),
                encryption.required() ? Boolean.TRUE : policy.responseEncryptionRequired(),
                policy.requestKeys());
    }

    private static List<String> constrain(List<String> configured, List<String> allowed) {
        if (allowed.isEmpty()) return configured;
        if (configured.isEmpty()) return allowed;
        return configured.stream().filter(allowed::contains).toList();
    }

    public IssuerCredentialEncryption getTenantCredentialEncryption(String tenantId, int id) {
        requireAvailableIdentity(tenantId, id);
        return getCredentialEncryption(id);
    }

    public IssuerCredentialEncryption getGlobalCredentialEncryption(int id) {
        requireGlobalIdentity(id);
        return getCredentialEncryption(id);
    }

    private IssuerCredentialEncryption getCredentialEncryption(int id) {
        var issuer = issuerDataService.findById(id).orElseThrow();
        return credentialEncryption(issuer, issuer.getCredentialEncryption());
    }

    @Transactional
    public IssuerCredentialEncryption updateCredentialEncryption(
            String tenantId, int id, IssuerCredentialEncryption request) {
        requireTenantIdentity(tenantId, id);
        return updateCredentialEncryption(id, request);
    }

    @Transactional
    public IssuerCredentialEncryption updateGlobalCredentialEncryption(
            int id, IssuerCredentialEncryption request) {
        requireGlobalIdentity(id);
        return updateCredentialEncryption(id, request);
    }

    private IssuerCredentialEncryption updateCredentialEncryption(
            int id, IssuerCredentialEncryption request) {
        var issuer = issuerDataService.findById(id).orElseThrow();
        var policy = (request == null ? IssuerCredentialEncryption.unrestricted() : request).validated();
        issuer.setCredentialEncryption(policy);
        issuerDataService.save(issuer);
        return policy;
    }

    /** Adds only public routing metadata for the issuer's durable request decryption slots. */
    private IssuerCredentialEncryption credentialEncryption(
            IssuerDefinitionEntity issuer, IssuerCredentialEncryption policy) {
        return credentialEncryption(issuer, policy, null);
    }

    /**
     * Adds public routing metadata, optionally limited to the trust framework of an issuance
     * profile. A null requested trust system is used by management responses and includes all
     * configured scopes so the UI can show their assignment.
     */
    private IssuerCredentialEncryption credentialEncryption(
            IssuerDefinitionEntity issuer,
            IssuerCredentialEncryption policy,
            IssuerTrustSystem requestedTrustSystem) {
        if (identityKeySlotService == null || signingProviderService == null) return policy;
        var keys = new LinkedHashMap<String, CredentialEncryptionKey>();
        var now = Instant.now();
        identityKeySlotService.slots(issuer.getId()).stream()
                .filter(slot -> slot.getType() == IdentityKeySlotType.DECRYPTION)
                .filter(slot -> requestedTrustSystem == null
                        || requestDecryptionSlotApplies(slot.getTrustSystem(), requestedTrustSystem))
                .filter(slot -> slot.getKeyId() != null)
                .forEach(slot -> {
                    var slotTrustSystem = slot.getTrustSystem() == null
                            ? IssuerTrustSystem.Default : slot.getTrustSystem();
                    var key = signingKeyService.owned(
                            issuer.getTenantId(), slot.getKeyId());
                    var provider = signingProviderService.runtimeConfiguration(
                            issuer.getTenantId(), key.getProviderId());
                    keyVersions(key.getId()).stream()
                            .filter(version -> SigningKeyService.ACTIVE.equals(version.getStatus())
                                    || (SigningKeyService.PREVIOUS.equals(version.getStatus())
                                        && version.getPreviousUntil() != null
                                        && now.isBefore(version.getPreviousUntil())))
                            .map(version -> requestEncryptionKey(
                                    policy, provider, key, version, slotTrustSystem))
                            .filter(Objects::nonNull)
                            .forEach(requestKey -> keys.putIfAbsent(
                                    requestKeyMapKey(requestKey, requestedTrustSystem), requestKey));
                });
        return policy.withRequestKeys(List.copyOf(keys.values()));
    }

    static boolean requestDecryptionSlotApplies(
            IssuerTrustSystem slotTrustSystem, IssuerTrustSystem requestedTrustSystem) {
        if (requestedTrustSystem == null) return true;
        var configured = slotTrustSystem == null ? IssuerTrustSystem.Default : slotTrustSystem;
        return configured == IssuerTrustSystem.Default || configured == requestedTrustSystem;
    }

    private static String requestKeyMapKey(
            CredentialEncryptionKey requestKey, IssuerTrustSystem requestedTrustSystem) {
        return requestedTrustSystem == null
                ? requestKey.keyId() + "\u0000" + requestKey.trustSystem()
                : requestKey.keyId();
    }

    private List<SigningKeyVersionEntity> keyVersions(UUID keyId) {
        return signingKeyService.allVersions(keyId);
    }

    private CredentialEncryptionKey requestEncryptionKey(
            IssuerCredentialEncryption policy,
            org.heidiverse.heidi.entity.model.signing.SigningProviderConfiguration provider,
            org.heidiverse.heidi.entity.model.entity.SigningKeyEntity key,
            SigningKeyVersionEntity version,
            IssuerTrustSystem trustSystem) {
        var keyManagementAlgorithm = provider.contentKeyAlgorithms().stream()
                .filter(candidate -> compatibleContentKeyAlgorithm(version.getPublicJwk(), candidate))
                .filter(candidate -> policy.requestAlgValues().isEmpty()
                        || policy.requestAlgValues().contains(candidate))
                .findFirst()
                .orElse(null);
        if (keyManagementAlgorithm == null) return null;
        var publicJwk = withJwkAlgorithm(version.getPublicJwk(), keyManagementAlgorithm);
        var keyId = key.getLogicalKeyId() + "-v" + version.getVersion();
        try {
            var parsed = OBJECT_MAPPER.readTree(publicJwk);
            if (parsed != null && parsed.has("kid")) keyId = parsed.get("kid").asText(keyId);
        } catch (RuntimeException ignored) {
            // The persisted JWK was validated when the key version was created.
        }
        return new CredentialEncryptionKey(
                keyId, version.getKeyUri(), keyManagementAlgorithm, publicJwk,
                provider.endpoint(), provider.authenticationMode(), version.getAlgorithm(),
                trustSystem == IssuerTrustSystem.Default ? null : trustSystem);
    }

    /** Filters provider capabilities by the actual public key type before publishing metadata. */
    static boolean compatibleContentKeyAlgorithm(String publicJwk, String algorithm) {
        try {
            var key = JWK.parse(publicJwk);
            if (algorithm.startsWith("ECDH-ES")) {
                return key instanceof ECKey ec
                        && Curve.P_256.equals(ec.getCurve());
            }
            if ("RSA-OAEP-256".equals(algorithm)) {
                return key instanceof RSAKey rsa
                        && new java.math.BigInteger(1, rsa.getModulus().decode()).bitLength() >= 2048;
            }
        } catch (Exception ignored) {
            // Key versions are validated when saved; an invalid legacy row is simply not offered.
        }
        return false;
    }

    public boolean deleteTenantIdentity(String tenantId, int id) {
        requireTenantIdentity(tenantId, id);
        return deleteIssuerById(id);
    }

    private void requireTenantIdentity(String tenantId, int id) {
        var identity = issuerDataService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Identity not found: " + id));
        if (!tenantId.equals(identity.getTenantId())) {
            throw new SecurityException("Identity does not belong to tenant " + tenantId);
        }
    }

    private void requireGlobalIdentity(int id) {
        var identity = issuerDataService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Identity not found: " + id));
        if (identity.getTenantId() != null) {
            throw new SecurityException("Identity is not platform-owned");
        }
    }

    private void requireAvailableIdentity(String tenantId, int id) {
        var identity = issuerDataService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Identity not found: " + id));
        if (identity.getTenantId() != null && !tenantId.equals(identity.getTenantId())) {
            throw new SecurityException("Identity does not belong to tenant " + tenantId);
        }
    }

    public List<String> publicKeys(String issuerSlug) {
        var identity = issuerDataService.findBySlug(issuerSlug)
                .orElseThrow(() -> new IllegalArgumentException("Identity not found"));
        return identityKeySlotService.publicKeys(identity.getId(), IdentityKeySlotType.CREDENTIAL_SIGNING);
    }

    public Optional<IssuerSigningConfigurationInternal> getSigningConfiguration(
            String issuerSlug, IssuerTrustSystem trustSystem, String credentialIdentifier, String version,
            String signingKeyId) {
        return getSigningConfiguration(
                issuerSlug, trustSystem, credentialIdentifier, version, signingKeyId, null);
    }

    public Optional<IssuerSigningConfigurationInternal> getSigningConfiguration(
            String issuerSlug, IssuerTrustSystem trustSystem, String credentialIdentifier, String version,
            String signingKeyId, String issuanceProfileId) {
        var resolvedProfileId = resolveIssuanceProfileId(
                issuanceProfileId, trustSystem, credentialIdentifier, version);
        return issuerDataService.findBySlug(issuerSlug)
                .flatMap(issuer -> slotSigningConfiguration(
                        issuer, trustSystem, credentialIdentifier, version, signingKeyId,
                        resolvedProfileId));
    }

    private void writeGrantsForAllIdentities() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            if (TransactionSynchronizationManager.hasResource(this)) return;
            TransactionSynchronizationManager.bindResource(this, Boolean.TRUE);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    writeGrantPoliciesNow();
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(IssuerService.this);
                }
            });
            return;
        }
        writeGrantPoliciesNow();
    }

    /** Recomputes provider grants after a schema or non-issuer slot changes. */
    public void reconcileSigningGrants() {
        writeGrantsForAllIdentities();
    }

    @Scheduled(fixedDelayString = "${heidi.platform.signing-grants.rotation-reconcile-interval-ms:60000}")
    void reconcileExpiredRotationGrants() {
        // Reconcile operation scopes as well as expired versions. Operation scopes have no
        // platform row, so a provider outage during unbinding otherwise has no retry trigger.
        signingFlows.expire();
        writeGrantPoliciesNow();
    }

    @Transactional
    public void retainSigningFlow(String slug, SigningFlowRequest request) {
        var now = Instant.now();
        var limit = request.client() == IdentityKeySlotConsumer.ISSUER ? issuerFlowLifetime : verifierFlowLifetime;
        if (!request.expiresAt().isAfter(now) || request.expiresAt().isAfter(now.plus(limit))) {
            throw new IllegalArgumentException("Signing flow expiry exceeds the configured lifetime");
        }
        var identity = issuerDataService.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Identity not found"));
        var policies = new LinkedHashMap<GrantTarget, Map<String, Set<SigningPurpose>>>();
        collectSlotGrants(identity, policies,
                credentialSchemeDataService.findPublishedIssuerIdentityIds().contains(identity.getId()),
                proofSchemeDataService.findActiveVerifierIdentityIds().contains(identity.getId()));
        var client = flowClient(request.client());
        var allowed = policies.entrySet().stream()
                .filter(entry -> entry.getKey().key().equals(request.keyUri()))
                .anyMatch(entry -> entry.getValue().getOrDefault(client, Set.of()).contains(request.purpose()));
        if (!allowed || request.purpose() == SigningPurpose.KEY_MANAGEMENT || request.purpose() == SigningPurpose.READ) {
            throw new SecurityException("The identity does not grant this client the requested key use");
        }
        signingFlows.retain(request.flowId(), client, request.keyUri(), request.purpose(), request.expiresAt());
        writeGrantsForAllIdentities();
    }

    @Transactional
    public void releaseSigningFlow(UUID flowId, IdentityKeySlotConsumer client) {
        signingFlows.release(flowId, flowClient(client));
        writeGrantsForAllIdentities();
    }

    private static String flowClient(IdentityKeySlotConsumer client) {
        return switch (client) {
            case ISSUER -> SigningGrantService.CLIENT_ISSUER;
            case VERIFIER -> SigningGrantService.CLIENT_VERIFIER;
        };
    }

    private synchronized void writeGrantPoliciesNow() {
        grantPolicies().forEach(this::publishGrantPolicy);
    }

    /** Effective policy for one identity, including authority shared through other identities. */
    public List<IdentityGrantStatus> identityGrants(String tenantId, int identityId) {
        identityKeySlotService.list(tenantId, identityId);
        var identity = issuerDataService.findById(identityId)
                .orElseThrow(() -> new IllegalArgumentException("Identity not found"));
        var issuerIds = credentialSchemeDataService.findPublishedIssuerIdentityIds();
        var verifierIds = proofSchemeDataService == null
                ? Set.<Integer>of() : proofSchemeDataService.findActiveVerifierIdentityIds();
        var identityPolicies = new LinkedHashMap<GrantTarget, Map<String, Set<SigningPurpose>>>();
        collectSlotGrants(identity, identityPolicies, issuerIds.contains(identityId),
                verifierIds.contains(identityId));
        var effective = grantPolicies();

        return identityPolicies.keySet().stream()
                .map(target -> {
                    var desired = immutablePolicy(effective.getOrDefault(target, Map.of()));
                    var status = signingGrantService.status(
                            target.tenantId(), target.providerId(), target.key());
                    return new IdentityGrantStatus(target.key(), target.providerId(), desired,
                            status.confirmed(), status.pending() || !Objects.equals(desired, status.confirmed()));
                })
                .sorted(Comparator.comparing(IdentityGrantStatus::scope))
                .toList();
    }

    private Map<GrantTarget, Map<String, Set<SigningPurpose>>> grantPolicies() {
        var policies = new LinkedHashMap<GrantTarget, Map<String, Set<SigningPurpose>>>();
        var management = Map.of(SigningGrantService.CLIENT_PLATFORM,
                Set.of(SigningPurpose.KEY_MANAGEMENT));
        {
            // Keep management authority even when a prepared, unused or revoked key cannot sign.
            signingKeyService.keys().forEach(key ->
                    signingKeyService.keyScope(key.getId()).ifPresent(scope ->
                            collectPolicy(policies, key.getTenantId(), key.getProviderId(),
                                    scope, management)));
            // SubCA keys sign certificates on the platform, e.g. self-signing and leaf issuance.
            signingKeyService.keys().forEach(key -> {
                var grants = signingKeyService.isSubca(key.getId())
                        ? SigningGrantService.platformSigningKeyGrants() : management;
                signingKeyService.allVersions(key.getId()).forEach(version ->
                        collectPolicy(policies, key.getTenantId(), key.getProviderId(),
                                version.getKeyUri(), grants));
            });
            // Operation scopes have no platform-side key row. Enumerate the provider's
            // policies so an operation removed from an identity cannot retain stale access.
            signingProviderService.providers().forEach(provider ->
                    signingGrantService.scopes(provider.getTenantId(), provider.getId()).stream()
                            .filter(scope -> scope.startsWith("op/"))
                            .forEach(scope -> collectPolicy(policies, provider.getTenantId(),
                                    provider.getId(), scope, management)));
        }
        var identities = issuerDataService.findAll();
        var publishedIssuerIds = credentialSchemeDataService.findPublishedIssuerIdentityIds();
        var activeVerifierIds = proofSchemeDataService == null
                ? Set.<Integer>of() : proofSchemeDataService.findActiveVerifierIdentityIds();
        if (identityKeyIntegrityService != null) identityKeyIntegrityService.scan();
        identities.forEach(identity -> collectSlotGrants(identity, policies,
                publishedIssuerIds.contains(identity.getId()),
                activeVerifierIds.contains(identity.getId())));
        // A saved request remains authorized even after its schema or slot is removed.
        signingFlows.active().forEach(reference -> collectPolicy(
                policies, reference.tenantId(), reference.providerId(), reference.keyUri(),
                Map.of(reference.client(), Set.of(reference.purpose()))));
        return policies;
    }

    private void collectSlotGrants(
            IssuerDefinitionEntity identity,
            Map<GrantTarget, Map<String, Set<SigningPurpose>>> policies,
            boolean issuerUsesIdentity,
            boolean verifierUsesIdentity) {
        for (var slot : identityKeySlotService.slots(identity.getId())) {
            var grants = switch (slot.getType()) {
                case CREDENTIAL_SIGNING -> credentialSlotGrants(issuerUsesIdentity);
                case IDENTITY_STATEMENT -> slot.getTrustSystem() == IssuerTrustSystem.OIDF
                        ? SigningGrantService.platformSigningKeyGrants()
                        : trustSlotGrants(issuerUsesIdentity, verifierUsesIdentity);
                case PRESENTATION_SIGNING -> presentationSlotGrants(verifierUsesIdentity);
                case DECRYPTION -> decryptionSlotGrants(issuerUsesIdentity);
                case OPERATION -> {
                    var consumer = slot.getConsumer();
                    if (consumer == null) {
                        consumer = BBS_PRESENTATION_SETUP_OPERATION.equals(slot.getOperation())
                                ? IdentityKeySlotConsumer.VERIFIER : IdentityKeySlotConsumer.ISSUER;
                    }
                    // An unused operation retains management, not backend execution authority.
                    if ((consumer == IdentityKeySlotConsumer.ISSUER && !issuerUsesIdentity)
                            || (consumer == IdentityKeySlotConsumer.VERIFIER && !verifierUsesIdentity)) {
                        yield Map.of(SigningGrantService.CLIENT_PLATFORM, Set.of(SigningPurpose.KEY_MANAGEMENT));
                    }
                    yield consumer == IdentityKeySlotConsumer.VERIFIER
                            ? SigningGrantService.verifierOperationGrants()
                            : SigningGrantService.operationKeyGrants();
                }
                case FEDERATION, STATUS_LIST -> SigningGrantService.platformSigningKeyGrants();
            };
            if (slot.getKeyId() != null) {
                collectSlotKey(identity, slot.getKeyId(), grants, policies);
            }
            if (slot.getType() == IdentityKeySlotType.OPERATION
                    && slot.getKeyId() == null
                    && slot.getProviderId() != null && hasText(slot.getOperation())) {
                collectPolicy(policies, identity.getTenantId(), slot.getProviderId(),
                        "op/" + slot.getOperation(), grants);
            }
        }
    }

    private static Map<String, Set<SigningPurpose>> credentialSlotGrants(boolean issuerUsesIdentity) {
        var grants = new LinkedHashMap<String, Set<SigningPurpose>>();
        grants.put(SigningGrantService.CLIENT_PLATFORM, Set.of(SigningPurpose.KEY_MANAGEMENT));
        if (issuerUsesIdentity) {
            grants.put(SigningGrantService.CLIENT_ISSUER, Set.of(SigningPurpose.SIGNING));
        }
        return grants;
    }

    private static Map<String, Set<SigningPurpose>> trustSlotGrants(
            boolean issuerUsesIdentity, boolean verifierUsesIdentity) {
        var grants = new LinkedHashMap<String, Set<SigningPurpose>>();
        grants.put(SigningGrantService.CLIENT_PLATFORM, Set.of(SigningPurpose.KEY_MANAGEMENT));
        if (issuerUsesIdentity) {
            grants.put(SigningGrantService.CLIENT_ISSUER, Set.of(SigningPurpose.SIGNING));
        }
        if (verifierUsesIdentity) {
            grants.put(SigningGrantService.CLIENT_VERIFIER, Set.of(SigningPurpose.SIGNING));
        }
        return grants;
    }

    private static Map<String, Set<SigningPurpose>> presentationSlotGrants(
            boolean verifierUsesIdentity) {
        var grants = new LinkedHashMap<String, Set<SigningPurpose>>();
        grants.put(SigningGrantService.CLIENT_PLATFORM, Set.of(SigningPurpose.KEY_MANAGEMENT));
        if (verifierUsesIdentity) {
            grants.put(SigningGrantService.CLIENT_VERIFIER, Set.of(SigningPurpose.SIGNING));
        }
        return grants;
    }

    private static Map<String, Set<SigningPurpose>> decryptionSlotGrants(boolean issuerUsesIdentity) {
        var grants = new LinkedHashMap<String, Set<SigningPurpose>>();
        grants.put(SigningGrantService.CLIENT_PLATFORM, Set.of(SigningPurpose.KEY_MANAGEMENT));
        if (issuerUsesIdentity) {
            grants.put(SigningGrantService.CLIENT_ISSUER, Set.of(SigningPurpose.DECRYPT));
        }
        return grants;
    }

    private void collectSlotKey(
            IssuerDefinitionEntity identity,
            UUID keyId,
            Map<String, Set<SigningPurpose>> grants,
            Map<GrantTarget, Map<String, Set<SigningPurpose>>> policies) {
        try {
            var key = signingKeyService.owned(identity.getTenantId(), keyId);
            signingKeyService.allVersions(keyId).stream()
                    .filter(version -> SigningKeyService.ACTIVE.equals(version.getStatus())
                            || (SigningKeyService.PREVIOUS.equals(version.getStatus())
                                && (version.getPreviousUntil() == null
                                    || Instant.now().isBefore(version.getPreviousUntil()))))
                    .forEach(version -> collectPolicy(
                            policies, identity.getTenantId(), key.getProviderId(), version.getKeyUri(), grants));
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not resolve slot key {} for identity {}", keyId, identity.getSlug(), exception);
        }
    }

    private void collectPolicy(
            Map<GrantTarget, Map<String, Set<SigningPurpose>>> policies,
            String tenantId,
            Integer providerId,
            String keyUri,
            Map<String, Set<SigningPurpose>> grants) {
        if (providerId == null || keyUri == null || keyUri.isBlank()) return;
        // Keep the caller's scope: version policies must not collapse into the key policy.
        // A global provider has one operation policy shared by all consuming organisations.
        var provider = signingProviderService.resolve(tenantId, providerId);
        var target = new GrantTarget(provider.getTenantId(), providerId, keyUri);
        var policy = policies.computeIfAbsent(target, ignored -> new LinkedHashMap<>());
        grants.forEach((client, purposes) -> policy
                .computeIfAbsent(client, ignored -> new LinkedHashSet<>())
                .addAll(purposes));
    }

    private void publishGrantPolicy(GrantTarget target, Map<String, Set<SigningPurpose>> grants) {
        try {
            signingGrantService.publishScope(
                    target.tenantId(), target.providerId(), target.key(),
                    immutablePolicy(grants));
        } catch (RuntimeException exception) {
            // A failed reconciliation must not leave a transaction half-written; retrying the
            // identity change writes the same complete policy again.
            LOGGER.warn("Could not reconcile signing grants for {}", target.key(), exception);
        }
    }

    private static Map<String, Set<SigningPurpose>> immutablePolicy(
            Map<String, Set<SigningPurpose>> grants) {
        return grants.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }

    private record GrantTarget(String tenantId, Integer providerId, String key) {}

    public Optional<IssuerSigningConfigurationInternal> getSigningConfiguration(
            String issuerSlug, IssuerTrustSystem trustSystem, String credentialIdentifier,
            String version) {
        return getSigningConfiguration(
                issuerSlug, trustSystem, credentialIdentifier, version, null);
    }

    private IssuerTrustSystem effectiveTrustSystem(
            IssuerDefinitionEntity issuer,
            IssuerTrustSystem requested,
            String credentialIdentifier,
            String version) {
        if (requested != IssuerTrustSystem.Default) return requested;
        var credentialOverride = hasText(credentialIdentifier) && hasText(version)
                ? credentialSchemeDataService
                        .findByCredentialIdentifierAndVersion(credentialIdentifier, version)
                        .map(CredentialSchemeEntity::getDefaultTrustSystem)
                        .orElse(null)
                : null;
        var configuredDefault = credentialOverride == null
                ? issuer.getDefaultTrustSystem() : credentialOverride;
        return configuredDefault == null ? IssuerTrustSystem.Default : configuredDefault;
    }

    private IssuerTrustSystem effectiveTrustSystem(
            IssuerDefinitionEntity issuer,
            IssuerTrustSystem requested,
            String credentialIdentifier,
            String version,
            org.heidiverse.heidi.entity.model.profile.EcosystemProfile profile) {
        if (profile == null) {
            return effectiveTrustSystem(issuer, requested, credentialIdentifier, version);
        }
        var profileTrust = profile.policy().trustSystem();
        var effectiveProfileTrust = profileTrust == null ? IssuerTrustSystem.Default : profileTrust;
        if (requested != IssuerTrustSystem.Default && requested != effectiveProfileTrust) {
            throw new IllegalArgumentException(
                    "Trust system does not match ecosystem profile " + profile.id());
        }
        return effectiveProfileTrust;
    }

    private static org.heidiverse.heidi.entity.model.profile.EcosystemProfile profile(
            String id,
            org.heidiverse.heidi.entity.model.profile.EcosystemProfileRole role) {
        return id == null || id.isBlank() ? null
                : org.heidiverse.heidi.entity.model.profile.EcosystemProfileCatalog.require(id, role);
    }

    private String resolveIssuanceProfileId(
            String requestedProfileId, IssuerTrustSystem trustSystem,
            String credentialIdentifier, String version) {
        if (requestedProfileId != null && !requestedProfileId.isBlank()) {
            return requestedProfileId;
        }
        if (hasText(credentialIdentifier) && hasText(version)) {
            var persistedProfile = credentialSchemeDataService
                .findByCredentialIdentifierAndVersion(credentialIdentifier, version)
                .map(CredentialSchemeEntity::getIssuanceProfileId)
                .orElse(null);
            if (persistedProfile != null && !persistedProfile.isBlank()) return persistedProfile;
        }
        if (trustSystem != IssuerTrustSystem.Default) return null;
        return null;
    }

    public Optional<IssuerSigningConfigurationInternal> getSigningConfiguration(
            String issuerSlug, IssuerTrustSystem trustSystem) {
        return getSigningConfiguration(issuerSlug, trustSystem, null, null, null);
    }

    /** Resolves identity-level signing material for metadata and trust-framework artifacts. */
    public Optional<IssuerSigningConfigurationInternal> getTrustSigningConfiguration(
            String issuerSlug, IssuerTrustSystem trustSystem, String credentialIdentifier,
            String version) {
        return getTrustSigningConfiguration(
                issuerSlug, trustSystem, credentialIdentifier, version, null);
    }

    /** A caller may name the key directly, as a proof scheme does when it overrides the identity. */
    public Optional<IssuerSigningConfigurationInternal> getTrustSigningConfiguration(
            String issuerSlug, IssuerTrustSystem trustSystem, String credentialIdentifier,
            String version, String signingKeyId) {
        return getTrustSigningConfiguration(
                issuerSlug, trustSystem, credentialIdentifier, version, signingKeyId, null);
    }

    public Optional<IssuerSigningConfigurationInternal> getTrustSigningConfiguration(
            String issuerSlug, IssuerTrustSystem trustSystem, String credentialIdentifier,
            String version, String signingKeyId, String issuanceProfileId) {
        var resolvedProfileId = resolveIssuanceProfileId(
                issuanceProfileId, trustSystem, credentialIdentifier, version);
        return issuerDataService.findBySlug(issuerSlug)
                .flatMap(issuer -> slotTrustConfiguration(
                        issuer, trustSystem, credentialIdentifier, version, signingKeyId,
                        resolvedProfileId));
    }

    /** Resolves the verifier's request signer; EUDI binds it to a wallet access certificate. */
    public Optional<IssuerSigningConfigurationInternal> getPresentationSigningConfiguration(
            String issuerSlug, IssuerTrustSystem trustSystem, String signingKeyId) {
        return getPresentationSigningConfiguration(issuerSlug, trustSystem, signingKeyId, null);
    }

    public Optional<IssuerSigningConfigurationInternal> getPresentationSigningConfiguration(
            String issuerSlug, IssuerTrustSystem trustSystem, String signingKeyId,
            String presentationProfileId) {
        return issuerDataService.findBySlug(issuerSlug)
                .flatMap(issuer -> slotPresentationConfiguration(
                        issuer, trustSystem, signingKeyId, presentationProfileId));
    }

    public Optional<IssuerSigningConfigurationInternal> getOperationSigningConfiguration(
            String issuerSlug, IssuerTrustSystem trustSystem, String operation) {
        return getOperationSigningConfiguration(issuerSlug, trustSystem, operation, null, null);
    }

    public Optional<IssuerSigningConfigurationInternal> getOperationSigningConfiguration(
            String issuerSlug,
            IssuerTrustSystem trustSystem,
            String operation,
            String credentialIdentifier,
            String version) {
        return getOperationSigningConfiguration(
                issuerSlug, trustSystem, operation, credentialIdentifier, version, null);
    }

    public Optional<IssuerSigningConfigurationInternal> getOperationSigningConfiguration(
            String issuerSlug,
            IssuerTrustSystem trustSystem,
            String operation,
            String credentialIdentifier,
            String version,
            String issuanceProfileId) {
        requireOperation(operation);
        var resolvedProfileId = resolveIssuanceProfileId(
                issuanceProfileId, trustSystem, credentialIdentifier, version);
        return issuerDataService.findBySlug(issuerSlug)
                .flatMap(issuer -> slotOperationConfiguration(
                        issuer, trustSystem, operation, credentialIdentifier, version,
                        resolvedProfileId));
    }

    private Optional<IssuerSigningConfigurationInternal> slotSigningConfiguration(
            IssuerDefinitionEntity issuer,
            IssuerTrustSystem requestedTrustSystem,
            String credentialIdentifier,
            String version,
            String signingKeyId,
            String issuanceProfileId) {
        var profile = profile(issuanceProfileId, EcosystemProfileRole.ISSUANCE);
        var trustSystem = effectiveTrustSystem(
                issuer, requestedTrustSystem, credentialIdentifier, version,
                profile);
        var selectedKeyId = hasText(signingKeyId)
                ? signingKeyId : selectedSigningKeyId(credentialIdentifier, version, trustSystem);
        var resolved = identityKeySlotService.resolve(
                issuer.getTenantId(), issuer.getId(), IdentityKeySlotType.CREDENTIAL_SIGNING,
                trustSystem, null, selectedKeyId, SigningCertificateProfile.CREDENTIAL_SIGNING);
        if (resolved.isEmpty()) return Optional.empty();
        // Credential overrides select material; identifiers still belong to the identity.
        var trust = slotTrustRequest(identityKeySlotService.slots(issuer.getId()).stream()
                .filter(slot -> slot.getType() == IdentityKeySlotType.IDENTITY_STATEMENT
                        && slot.getTrustSystem() == trustSystem)
                .map(IdentityKeySlotEntity::getConfiguration)
                .filter(Objects::nonNull).findFirst().orElse(null));
        var did = trustSystem == IssuerTrustSystem.Switzerland ? trust.swissDid() : null;
        if (trustSystem == IssuerTrustSystem.Switzerland && !hasText(did)) {
            throw new IllegalArgumentException(SWISS_DID_REQUIRED);
        }
        var issuerClaim = hasText(trust.issuerClaim()) ? trust.issuerClaim() : did;
        return Optional.of(toSlotConfiguration(
                issuer, resolved.get(), trustSystem, issuerClaim, did, issuanceProfileId, profile));
    }

    private Optional<IssuerSigningConfigurationInternal> slotTrustConfiguration(
            IssuerDefinitionEntity issuer,
            IssuerTrustSystem requestedTrustSystem,
            String credentialIdentifier,
            String version,
            String signingKeyId,
            String issuanceProfileId) {
        var profile = profile(issuanceProfileId, EcosystemProfileRole.ISSUANCE);
        var trustSystem = effectiveTrustSystem(
                issuer, requestedTrustSystem, credentialIdentifier, version,
                profile);
        var certificateProfile = trustCertificateProfile(trustSystem);
        var resolved = identityKeySlotService.resolve(
                issuer.getTenantId(), issuer.getId(), IdentityKeySlotType.IDENTITY_STATEMENT,
                trustSystem, null, null, certificateProfile);
        if (resolved.isEmpty()) return Optional.empty();
        var trust = slotTrustRequest(resolved.get().slot().getConfiguration());
        return Optional.of(toSlotConfiguration(
                issuer, resolved.get(), trustSystem, trust.issuerClaim(), trust.swissDid(),
                issuanceProfileId, profile));
    }

    private Optional<IssuerSigningConfigurationInternal> slotPresentationConfiguration(
            IssuerDefinitionEntity issuer,
            IssuerTrustSystem requestedTrustSystem,
            String signingKeyId,
            String presentationProfileId) {
        var profile = profile(presentationProfileId, EcosystemProfileRole.PRESENTATION);
        var trustSystem = effectiveTrustSystem(
                issuer, requestedTrustSystem, null, null,
                profile);
        var certificateProfile = IdentityKeySlotType.PRESENTATION_SIGNING.certificateProfile(trustSystem);
        var resolved = identityKeySlotService.resolve(
                issuer.getTenantId(), issuer.getId(), IdentityKeySlotType.PRESENTATION_SIGNING,
                trustSystem, null, signingKeyId, certificateProfile);
        if (resolved.isEmpty()) return Optional.empty();

        String issuerClaim = null;
        String swissDid = null;
        if (trustSystem == IssuerTrustSystem.Switzerland) {
            var trust = identityKeySlotService.slots(issuer.getId()).stream()
                    .filter(slot -> slot.getType() == IdentityKeySlotType.IDENTITY_STATEMENT
                            && slot.getTrustSystem() == trustSystem)
                    .map(slot -> slotTrustRequest(slot.getConfiguration()))
                    .findFirst()
                    .orElseGet(() -> slotTrustRequest(null));
            swissDid = trust.swissDid();
            if (!hasText(swissDid)) {
                throw new IllegalArgumentException(SWISS_DID_REQUIRED);
            }
            issuerClaim = swissDid;
        }
        return Optional.of(toSlotConfiguration(
                issuer, resolved.get(), trustSystem, issuerClaim, swissDid,
                presentationProfileId, profile));
    }

    private Optional<IssuerSigningConfigurationInternal> slotOperationConfiguration(
            IssuerDefinitionEntity issuer,
            IssuerTrustSystem requestedTrustSystem,
            String operation,
            String credentialIdentifier,
            String version,
            String issuanceProfileId) {
        var profile = profile(issuanceProfileId, EcosystemProfileRole.ISSUANCE);
        var trustSystem = effectiveTrustSystem(
                issuer, requestedTrustSystem, credentialIdentifier, version,
                profile);
        var resolved = identityKeySlotService.resolve(
                issuer.getTenantId(), issuer.getId(), IdentityKeySlotType.OPERATION,
                trustSystem, operation, null, SigningCertificateProfile.CREDENTIAL_SIGNING);
        if (resolved.isEmpty()) return Optional.empty();
        return Optional.of(toSlotConfiguration(
                issuer, resolved.get(), trustSystem, null, null, issuanceProfileId, profile));
    }

    private IssuerSigningConfigurationInternal toSlotConfiguration(
            IssuerDefinitionEntity issuer,
            IdentityKeySlotService.ResolvedKey resolved,
            IssuerTrustSystem trustSystem,
            String issuerClaim,
            String swissDid,
            String profileId,
            EcosystemProfile profile) {
        var slot = resolved.slot();
        var key = resolved.key();
        var version = resolved.version();
        var providerId = slot.getProviderId() != null
                ? slot.getProviderId() : key == null ? null : key.getProviderId();
        if (providerId == null) {
            throw new IllegalStateException("Identity key slot has no signing provider");
        }
        var provider = signingProviderService.runtimeConfiguration(issuer.getTenantId(), providerId);
        var keyId = version == null ? null : OBJECT_MAPPER.readTree(version.getPublicJwk()).path("kid").asText();
        var publicJwk = version == null ? null
                : withPublicJwkMetadata(version.getPublicJwk(), keyId, version.getAlgorithm());
        if (hasText(swissDid) && hasText(publicJwk)) {
            publicJwk = withOverriddenJwkKeyId(publicJwk, swissDid + "#" + keyId);
        }
        var previous = key == null || signingKeyService == null ? List.<String>of()
                : signingKeyService.previousPublicJwks(
                        issuer.getTenantId(), key.getId(), version.getId());
        var algorithm = version == null ? null : version.getAlgorithm();
        if (profile != null && algorithm != null
                && !profile.policy().signingAlgorithms().supported().contains(algorithm)) {
            throw new IllegalArgumentException(
                    "Signing algorithm " + algorithm + " is not allowed by ecosystem profile "
                            + profile.id());
        }
        var providerAlgorithms = provider.supportedAlgorithms() == null
                ? List.<String>of() : provider.supportedAlgorithms();
        var supportedAlgorithms = profile == null
                ? providerAlgorithms
                : providerAlgorithms.stream()
                        .filter(profile.policy().signingAlgorithms().supported()::contains)
                        .toList();
        if (profile != null && supportedAlgorithms.isEmpty()) {
            throw new IllegalArgumentException(
                    "Signing provider has no algorithm allowed by ecosystem profile " + profile.id());
        }
        return new IssuerSigningConfigurationInternal(
                trustSystem, issuerClaim, keyId,
                version == null ? null : version.getKeyUri(), algorithm,
                provider.endpoint(), provider.authenticationMode(),
                publicJwk, resolved.certificateChain(), previous,
                supportedAlgorithms, provider.supportedOperations(), profileId);
    }

    private static SigningCertificateProfile trustCertificateProfile(IssuerTrustSystem trustSystem) {
        return trustSystem == IssuerTrustSystem.EUDI
                ? SigningCertificateProfile.ACCESS : SigningCertificateProfile.CREDENTIAL_SIGNING;
    }

    private String selectedSigningKeyId(
            String credentialIdentifier, String version, IssuerTrustSystem trustSystem) {
        if (!hasText(credentialIdentifier) || !hasText(version)) return null;
        return credentialSchemeDataService
                .findByCredentialIdentifierAndVersion(credentialIdentifier, version)
                .map(scheme -> scheme.getSigningKeyIds().getOrDefault(
                        trustSystem, scheme.getSigningKeyIds().get(IssuerTrustSystem.Default)))
                .orElse(null);
    }

    private static void requireOperation(String operation) {
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("Signing operation must not be blank");
        }
    }

    /** Ensures the local profile has explicit custom issuer and verifier signing slots. */
    public IssuerDefinitionEntity ensureLocalDevelopmentIssuer(
            String tenantId, String issuerSlug, String displayName) {
        var issuer = issuerDataService.findBySlug(issuerSlug).orElseGet(() -> {
            createIssuer(new IssuerDefinitionRequest(
                    issuerSlug,
                    "",
                    LocalizedValue.fromStringMap(
                            Map.of(TenantLanguages.tag(defaultLanguage), displayName))),
                    tenantId);
            return issuerDataService.findBySlug(issuerSlug)
                    .orElseThrow(() -> new IllegalStateException(
                            "Local issuer was not persisted: " + issuerSlug));
        });
        issuer.setTenantId(tenantId);
        ensureLocalKeySlots(issuer);
        var saved = issuerDataService.save(issuer);
        writeGrantsForAllIdentities();
        return saved;
    }

    private void validateSlotTrustSettings(IssuerDefinitionRequest request) {
        var trustSystems = request.trustSystems();
        if (trustSystems != null && trustSystems.contains(IssuerTrustSystem.Default)) {
            throw new IllegalArgumentException(
                    "Default is routing configuration, not an assignable trust system");
        }
        if (request.defaultTrustSystem() != null
                && trustSystems != null
                && !trustSystems.isEmpty()
                && !trustSystems.contains(request.defaultTrustSystem())) {
            throw new IllegalArgumentException(
                    "The default trust system must be active on the identity");
        }
    }

    /** Gives the local profile usable issuer and verifier signing assignments. */
    private void ensureLocalKeySlots(IssuerDefinitionEntity issuer) {
        final var localTrustSystem = IssuerTrustSystem.Custom;
        var roles = List.of(
                IdentityKeySlotType.CREDENTIAL_SIGNING,
                IdentityKeySlotType.IDENTITY_STATEMENT,
                IdentityKeySlotType.PRESENTATION_SIGNING);
        var slots = identityKeySlotService.slots(issuer.getId());
        var missing = roles.stream().filter(type -> slots.stream().noneMatch(slot ->
                slot.getType() == type && slot.getTrustSystem() == localTrustSystem)).toList();
        if (missing.isEmpty()) return;

        // Bootstrap missing roles only; an operator's existing bindings are authoritative.
        var keyId = slots.stream()
                .filter(slot -> slot.getTrustSystem() == localTrustSystem)
                .filter(slot -> slot.getType() == IdentityKeySlotType.CREDENTIAL_SIGNING
                        || slot.getType() == IdentityKeySlotType.IDENTITY_STATEMENT)
                .map(IdentityKeySlotEntity::getKeyId)
                .filter(Objects::nonNull)
                .findFirst()
                .or(() -> signingKeyService.findOwnedByLogicalKeyId(
                        issuer.getTenantId(), issuer.getSlug()).map(key -> key.getId()))
                .orElseGet(() -> signingKeyService
                        .create(issuer.getTenantId(), issuer.getSlug(), "ES256", null, null)
                        .keyId());
        var key = signingKeyService.owned(issuer.getTenantId(), keyId);
        for (var type : missing) {
            identityKeySlotService.ensureKeySlot(issuer.getTenantId(), issuer.getId(), type,
                    localTrustSystem, null, keyId, key.getProviderId());
        }
    }

    /** Attaches the local development CA chain to keys reachable from the identity. */
    @Transactional
    public IssuerDefinitionEntity seedLocalDevelopmentTrust(
            String issuerSlug, String issuerIdentifier, LocalDevelopmentTrustSeed seed) {
        return seedLocalDevelopmentTrust(issuerSlug, issuerIdentifier, seed, List.of());
    }

    /** Provisions configured trust slots before the schema seed runner starts. */
    @Transactional
    public IssuerDefinitionEntity seedLocalDevelopmentTrust(
            String issuerSlug,
            String issuerIdentifier,
            LocalDevelopmentTrustSeed seed,
            Collection<IssuerTrustSystem> configuredTrustSystems) {
        var issuer = issuerDataService.findBySlug(issuerSlug)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Issuer not found for slug: " + issuerSlug));
        var rootCertificate = seed.rootCertificate();
        validateTrustAnchorCertificate(rootCertificate);
        ensureLocalTrustSlots(
                issuer, issuerIdentifier, seed, rootCertificate, configuredTrustSystems);
        // Startup supplies a default; configured verification trust remains the operator's choice.
        if (issuer.getEudiVerificationTrustAnchors().isEmpty()) {
            issuer.setEudiVerificationTrustAnchors(List.of(rootCertificate));
        }
        for (var slot : identityKeySlotService.slots(issuer.getId())) {
            if (slot.getType() != IdentityKeySlotType.CREDENTIAL_SIGNING
                    && slot.getType() != IdentityKeySlotType.IDENTITY_STATEMENT
                    && slot.getType() != IdentityKeySlotType.PRESENTATION_SIGNING) continue;
            var keyId = slot.getKeyId();
            if (keyId == null) continue;
            var version = signingKeyService.activeVersion(issuer.getTenantId(), keyId);
            if (IssuerSigningAlgorithms.BBS.equals(version.getAlgorithm())
                    || !hasText(version.getPublicJwk())) continue;
            var profile = slot.getType().certificateProfile(slot.getTrustSystem());
            var trust = slot.getTrustSystem() == IssuerTrustSystem.Default ? null : slot.getTrustSystem();
            var certificates = signingKeyService.certificates(issuer.getTenantId(), keyId, version.getId());

            // Seeding fills gaps; a valid operator-selected certificate remains authoritative.
            var selected = certificates.stream()
                    .filter(certificate -> certificate.getId().equals(slot.getCertificateId()))
                    .filter(certificate -> certificate.getKeyVersionId().equals(version.getId()))
                    .filter(certificate -> certificate.getProfile() == profile
                            && Objects.equals(SigningCertificateRules.framework(certificate.getTrustSystem()), trust))
                    .anyMatch(certificate -> SigningCertificateRules.validAt(certificate, Instant.now()));
            if (selected) continue;

            // Reuse a valid development certificate; restarting must not change x509 client IDs.
            var certificateId = certificates.stream()
                    .filter(certificate -> certificate.getProfile() == profile
                            && certificate.getTrustSystem() == trust)
                    .filter(certificate -> reusableDevCertificate(certificate, rootCertificate, issuerIdentifier))
                    .sorted(Comparator.comparing(certificate -> !certificate.getId().equals(slot.getCertificateId())))
                    .map(org.heidiverse.heidi.entity.model.entity.SigningCertificateEntity::getId)
                    .findFirst()
                    .orElseGet(() -> signingKeyService.setCertificateChain(
                            issuer.getTenantId(), keyId, version.getId(),
                            developmentChain(seed, issuerSlug, issuerIdentifier, version.getPublicJwk()),
                            profile, SigningCertificateSource.DEVELOPMENT, trust));
            if (certificateId.equals(slot.getCertificateId())) continue;
            identityKeySlotService.save(issuer.getTenantId(), issuer.getId(), new IdentityKeySlotRequest(
                    slot.getId(), slot.getType(), slot.getTrustSystem(), slot.getOperation(), keyId,
                    slot.getProviderId(), certificateId, slot.getOrder(), slot.getConfiguration(), slot.getConsumer()));
        }
        return issuerDataService.save(issuer);
    }

    private void ensureLocalTrustSlots(
            IssuerDefinitionEntity issuer,
            String issuerIdentifier,
            LocalDevelopmentTrustSeed seed,
            String rootCertificate,
            Collection<IssuerTrustSystem> configuredTrustSystems) {
        if (configuredTrustSystems == null || configuredTrustSystems.isEmpty()) return;
        if (configuredTrustSystems.contains(IssuerTrustSystem.Default)) {
            throw new IllegalArgumentException(
                    "Default is routing configuration, not an assignable trust system");
        }

        var roles = List.of(
                IdentityKeySlotType.CREDENTIAL_SIGNING,
                IdentityKeySlotType.IDENTITY_STATEMENT,
                IdentityKeySlotType.PRESENTATION_SIGNING);
        for (var trustSystem : new LinkedHashSet<>(configuredTrustSystems)) {
            if (trustSystem == null) {
                throw new IllegalArgumentException("Local trust system must not be null");
            }
            var slots = identityKeySlotService.slots(issuer.getId());
            var missing = roles.stream()
                    .filter(type -> slots.stream().noneMatch(slot ->
                            slot.getType() == type && slot.getTrustSystem() == trustSystem))
                    .toList();
            if (missing.isEmpty()) continue;

            var localKeyId = usableLocalKey(issuer, slots, roles, trustSystem);
            if (localKeyId == null) {
                localKeyId = signingKeyService
                        .create(issuer.getTenantId(),
                                issuer.getSlug() + "-" + trustSystem.name().toLowerCase(),
                                LOCAL_DEVELOPMENT_KEY_ALGORITHM, null, null)
                        .keyId();
            }
            var keyId = localKeyId;
            var key = signingKeyService.owned(issuer.getTenantId(), keyId);
            var version = signingKeyService.activeVersion(issuer.getTenantId(), keyId);
            if (IssuerSigningAlgorithms.BBS.equals(version.getAlgorithm())
                    || !hasText(version.getPublicJwk())) {
                throw new IllegalArgumentException(
                        "Local trust bootstrap requires an ES256 signing key");
            }
            var certificates = signingKeyService.certificates(
                    issuer.getTenantId(), keyId, version.getId());
            for (var type : missing) {
                var profile = type.certificateProfile(trustSystem);
                var framework = SigningCertificateRules.framework(trustSystem);
                var certificateId = reusableCertificate(
                        certificates, profile, framework, rootCertificate, issuerIdentifier);
                if (certificateId == null) {
                    certificateId = signingKeyService.setCertificateChain(
                            issuer.getTenantId(),
                            keyId,
                            version.getId(),
                            developmentChain(seed, issuer.getSlug(), issuerIdentifier,
                                    version.getPublicJwk()),
                            profile,
                            SigningCertificateSource.DEVELOPMENT,
                            framework);
                    certificates = signingKeyService.certificates(
                            issuer.getTenantId(), keyId, version.getId());
                }
                identityKeySlotService.save(
                        issuer.getTenantId(),
                        issuer.getId(),
                        new IdentityKeySlotRequest(
                                null,
                                type,
                                trustSystem,
                                null,
                                keyId,
                                key.getProviderId(),
                                certificateId,
                                0,
                                null));
            }
        }
    }

    private UUID reusableCertificate(
            List<org.heidiverse.heidi.entity.model.entity.SigningCertificateEntity> certificates,
            SigningCertificateProfile profile,
            IssuerTrustSystem framework,
            String rootCertificate,
            String issuerIdentifier) {
        for (var certificate : certificates) {
            if (certificate.getProfile() != profile
                    || SigningCertificateRules.framework(certificate.getTrustSystem()) != framework
                    || !reusableDevCertificate(certificate, rootCertificate, issuerIdentifier)) {
                continue;
            }
            return certificate.getId();
        }
        return null;
    }

    private UUID usableLocalKey(
            IssuerDefinitionEntity issuer,
            List<IdentityKeySlotEntity> slots,
            List<IdentityKeySlotType> roles,
            IssuerTrustSystem trustSystem) {
        for (var slot : slots) {
            if (slot.getTrustSystem() != trustSystem
                    || !roles.contains(slot.getType())
                    || slot.getKeyId() == null) continue;
            var version = signingKeyService.activeVersion(issuer.getTenantId(), slot.getKeyId());
            if (!IssuerSigningAlgorithms.BBS.equals(version.getAlgorithm())
                    && hasText(version.getPublicJwk())) return slot.getKeyId();
        }
        return null;
    }

    private boolean reusableDevCertificate(
            org.heidiverse.heidi.entity.model.entity.SigningCertificateEntity record,
            String root, String issuer) {
        var chain = record.getCertificateChain();
        if (record.getSource() != SigningCertificateSource.DEVELOPMENT
                || chain.isEmpty() || !root.equals(chain.getLast())) return false;
        try {
            var leaf = (java.security.cert.X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(chain.getFirst())));
            leaf.checkValidity();
            var host = java.net.URI.create(issuer).getHost();
            var type = host == null ? org.bouncycastle.asn1.x509.GeneralName.uniformResourceIdentifier
                    : org.bouncycastle.asn1.x509.GeneralName.dNSName;
            var name = host == null ? issuer : host;
            var names = leaf.getSubjectAlternativeNames();
            return names != null && names.stream().anyMatch(san ->
                    Integer.valueOf(type).equals(san.getFirst()) && name.equals(san.get(1)));
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * Certifies a key provisioned after start-up with the development CA, so a key added through
     * the Cockpit is usable without hand-uploading a chain. Returns nothing outside a development
     * stack, and never runs when the caller supplied a chain of their own - the Cockpit stays the
     * way to replace this certificate.
     */
    private List<String> developmentCertificate(
            String keyId, String algorithm, String publicJwk) {
        if (developmentTrustSeed == null) return List.of();
        if (IssuerSigningAlgorithms.BBS.equals(algorithm)) return List.of();
        if (!hasText(publicJwk)) return List.of();

        return developmentChain(
                developmentTrustSeed, keyId, developmentTrustSeed.issuerIdentifier(), publicJwk);
    }

    private List<String> developmentChain(
            LocalDevelopmentTrustSeed seed,
            String subjectName,
            String issuer,
            String publicJwk) {
        try {
            var jwk = com.nimbusds.jose.jwk.JWK.parse(publicJwk).toPublicJWK();
            var publicKey = switch (jwk.getKeyType().getValue()) {
                case "EC" -> jwk.toECKey().toPublicKey().getEncoded();
                case "RSA" -> jwk.toRSAKey().toPublicKey().getEncoded();
                case "OKP" -> jwk.toOctetKeyPair().toPublicKey().getEncoded();
                default -> throw new IllegalArgumentException(
                        "Unsupported provider key type for local certificate: " + jwk.getKeyType());
            };
            var chain = seed.certificateChain(subjectName, issuer, publicKey);
            validateCertificateChain(chain);
            return chain;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not issue a development certificate for " + subjectName, exception);
        }
    }

    private String withPublicJwkMetadata(String issuerJwk, String keyId, String algorithm) {
        try {
            var parsed = OBJECT_MAPPER.readTree(issuerJwk);
            if (!(parsed instanceof ObjectNode json)) {
                throw new IllegalArgumentException("Issuer JWK must be a JSON object");
            }
            // Always the platform key ID: the provider names its key with the prefix from
            // providerKeyName and returns that name as the JWK's kid, which must not reach a
            // credential.
            json.put("kid", keyId);
            if (!json.has("alg")) json.put("alg", algorithm);
            return OBJECT_MAPPER.writeValueAsString(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not add metadata to issuer JWK", exception);
        }
    }

    private String withOverriddenJwkKeyId(String issuerJwk, String publishedKeyId) {
        try {
            var parsed = OBJECT_MAPPER.readTree(issuerJwk);
            if (!(parsed instanceof ObjectNode json)) {
                throw new IllegalArgumentException("Issuer JWK must be a JSON object");
            }
            json.put("kid", publishedKeyId);
            return OBJECT_MAPPER.writeValueAsString(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not set Swiss DID key-id on issuer JWK", exception);
        }
    }

    private String withJwkAlgorithm(String publicJwk, String algorithm) {
        try {
            var parsed = OBJECT_MAPPER.readTree(publicJwk);
            if (!(parsed instanceof ObjectNode json)) {
                throw new IllegalArgumentException("Credential encryption JWK must be a JSON object");
            }
            json.put("alg", algorithm);
            json.put("use", "enc");
            return OBJECT_MAPPER.writeValueAsString(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not add metadata to credential encryption JWK", exception);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
