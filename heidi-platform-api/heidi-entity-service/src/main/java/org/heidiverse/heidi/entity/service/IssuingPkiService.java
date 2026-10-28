// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import com.nimbusds.jose.jwk.AsymmetricJWK;
import com.nimbusds.jose.jwk.JWK;
import java.math.BigInteger;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.heidiverse.heidi.signing.adapters.ManagedCertificates;
import uniffi.heidi_pki_x509.*;
import org.heidiverse.heidi.entity.data.repository.IssuingSubcaRepository;
import org.heidiverse.heidi.entity.model.entity.IssuingSubcaEntity;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateSource;
import org.heidiverse.heidi.shared.signing.SigningCsrRequest;
import org.heidiverse.heidi.shared.signing.SigningKeyRef;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues certificates with an organisation key held by the signing service. */
@Service
public class IssuingPkiService {
    public enum PathLengthMode { LIMITED, UNLIMITED }
    private static final Duration CA_VALIDITY = Duration.ofDays(365);
    private static final long CLOCK_SKEW_SECONDS = 60;
    private static final Duration LEAF_VALIDITY = Duration.ofDays(90);
    private static final String PID_TYPE = "0.4.0.194126.1.1";
    private static final String ORGANISATION_ID = "2.5.4.97";
    private static final String COUNTRY = "2.5.4.6";
    private static final String ORGANISATION = "2.5.4.10";
    private static final String COMMON_NAME = "2.5.4.3";
    private static final String SAN_OID = "2.5.29.17";
    private static final int DIGITAL_SIGNATURE = 1;
    private static final int CONTENT_COMMITMENT = 1 << 1;
    private static final int CA_USAGE = (1 << 5) | (1 << 6);
    private static final int SERIAL_BITS = 159;

    private final IssuingSubcaRepository repository;
    private final SigningKeyService keys;
    private final SigningProviderService providers;
    private final IssuerService issuers;
    private final String publicBaseUrl;

    public IssuingPkiService(IssuingSubcaRepository repository, SigningKeyService keys,
            SigningProviderService providers, IssuerService issuers,
            @Value("${heidi.platform.public-base-url}") String publicBaseUrl) {
        this.repository = repository;
        this.keys = keys;
        this.providers = providers;
        this.issuers = issuers;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public List<IssuingSubcaEntity> list(String tenantId) {
        var cas = repository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        var active = cas.stream().filter(ca -> ca.getDeletedAt() == null).toList();
        active.forEach(ca -> {
            ca.setAnchor(CertificateAnchors.resolve(ca.getCertificateChain(), cas));
            ca.setChainDetails(CertificateAnchors.details(ca.getCertificateChain()));
        });
        return active;
    }

    /** Hide the branch while retaining leaf references, provenance and publication URLs. */
    @Transactional
    public void deleteChain(String tenantId, UUID id) {
        owned(tenantId, id);
        var cas = repository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        var branch = new HashSet<UUID>();
        branch.add(id);
        boolean changed;
        do {
            changed = false;
            for (var ca : cas) {
                if (branch.contains(ca.getParentCaId())) changed |= branch.add(ca.getId());
            }
        } while (changed);

        var deletedAt = Instant.now();
        var removed = cas.stream().filter(ca -> branch.contains(ca.getId()) && ca.getDeletedAt() == null).toList();
        removed.forEach(ca -> ca.setDeletedAt(deletedAt));
        repository.saveAll(removed);
    }

    @Transactional
    public IssuingSubcaEntity create(String tenantId, String subjectDn, String algorithm,
            Integer providerId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("Organisation required");
        return create(tenantId, subjectDn, algorithm, providerId, null);
    }

    @Transactional
    public IssuingSubcaEntity create(String tenantId, String subjectDn, String algorithm,
            Integer providerId, UUID parentCaId) {
        if (parentCaId != null) checkParent(owned(tenantId, parentCaId));
        legalIssuerName(subjectDn);
        var id = UUID.randomUUID();
        var key = keys.create(tenantId, "subca-" + id, java.util.Set.of(SigningKeyUsage.SIGN),
                algorithm, providerId, null);
        var subca = new IssuingSubcaEntity();
        subca.setId(id);
        subca.setParentCaId(parentCaId);
        subca.setTenantId(tenantId);
        subca.setKeyId(key.keyId());
        subca.setKeyVersionId(key.keyVersionId());
        subca.setSubjectDn(subjectDn);
        subca.setCreatedAt(Instant.now());
        var saved = repository.save(subca);

        // Grant the platform SIGNING on the new key after commit, before self-sign or leaves.
        issuers.reconcileSigningGrants();
        return saved;
    }

    /** Include deleted CAs so existing certificates retain their published trust anchors. */
    public List<String> trustAnchors(String tenantId) {
        var anchors = new java.util.LinkedHashSet<String>();
        for (var ca : repository.findAllByTenantIdOrderByCreatedAtDesc(tenantId)) {
            if (ca.getCertificateChain().isEmpty()) continue;

            // Verifiers anchor on the chain's last certificate: self-signed SubCA or offline root.
            var root = ca.getCertificateChain().getLast();
            try {
                ManagedCertificates.valid(root);
            } catch (Exception expired) {
                continue;
            }
            anchors.add(root);
        }
        return List.copyOf(anchors);
    }

    public IssuingSubcaEntity owned(String tenantId, UUID id) {
        var subca = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("SubCA not found"));
        if (!subca.getTenantId().equals(tenantId)) throw new SecurityException("SubCA belongs to another organisation");
        if (subca.getDeletedAt() != null) throw new IllegalArgumentException("CA chain has been deleted");
        return subca;
    }

    public String csr(String tenantId, UUID id) {
        var ca = owned(tenantId, id);
        return keys.createCsr(tenantId, ca.getKeyId(), ca.getKeyVersionId(),
                new SigningCsrRequest(ca.getSubjectDn(), List.of(), List.of()));
    }

    @Transactional
    public IssuingSubcaEntity selfSign(String tenantId, UUID id,
            PathLengthMode mode, Integer pathLength) {
        var ca = owned(tenantId, id);
        if (mode == null) throw new IllegalArgumentException("Choose a root CA path length policy");
        if (mode == PathLengthMode.LIMITED && (pathLength == null || pathLength < 0)) {
            throw new IllegalArgumentException("Path length must be a non-negative integer");
        }
        if (mode == PathLengthMode.UNLIMITED && pathLength != null) {
            throw new IllegalArgumentException("Unlimited path length must not include a limit");
        }
        if (ca.getParentCaId() != null) throw new IllegalArgumentException("Intermediate must be signed by its parent CA");
        var name = legalIssuerName(ca.getSubjectDn());
        if (!ca.getCertificateChain().isEmpty()) throw new IllegalArgumentException("SubCA already certified");
        try {
            var version = keys.version(tenantId, ca.getKeyId(), ca.getKeyVersionId());
            var spki = ((AsymmetricJWK) JWK.parse(version.getPublicJwk())).toPublicKey().getEncoded();
            var now = Instant.now();
            var spec = new CertificateSpec(name, null, spki, serial(), now.minusSeconds(CLOCK_SKEW_SECONDS).getEpochSecond(),
                    now.plus(CA_VALIDITY).getEpochSecond(), ManagedCertificates.algorithm(version.getAlgorithm()),
                    CertificateRole.AUTHORITY, pathLength == null ? null : pathLength.longValue(),
                    CA_USAGE, List.of(), List.of(), null, List.of(), List.of());
            var certificate = sign(tenantId, ca, spec);
            ca.setCertificateChain(List.of(certificate));
            ca.setCertificateHistory(List.of(certificate));
            ca.setCertificateSource("SELF_SIGNED");
            ca.setGeneratedRootCertificate(certificate);
            return repository.save(ca);
        } catch (Exception error) {
            throw new IllegalArgumentException("Could not self-sign SubCA", error);
        }
    }

    @Transactional
    public IssuingSubcaEntity importChain(String tenantId, UUID id, List<String> chain) {
        var ca = owned(tenantId, id);
        legalIssuerName(ca.getSubjectDn());
        if (chain == null || chain.size() < 2) throw new IllegalArgumentException("SubCA and offline root certificates required");
        var certs = validateChain(chain);
        if (ca.getParentCaId() != null && !chain.subList(1, chain.size()).equals(
                owned(tenantId, ca.getParentCaId()).getCertificateChain())) {
            throw new IllegalArgumentException("Imported chain must match the selected parent CA");
        }
        var version = keys.version(tenantId, ca.getKeyId(), ca.getKeyVersionId());
        try {
            var publicKey = ((AsymmetricJWK) JWK.parse(version.getPublicJwk())).toPublicKey();
            if (!ManagedCertificates.use(x509 -> x509.matchesPublicKey(certs.getFirst().getDer(), publicKey.getEncoded()))) {
                throw new IllegalArgumentException("SubCA certificate does not match its key");
            }
            if (!ManagedCertificates.use(x509 -> x509.namesEqual(
                    ManagedCertificates.subject(ca.getSubjectDn()), certs.getFirst().getSubject().getDer()))) {
                throw new IllegalArgumentException("SubCA certificate subject differs from CSR subject");
            }
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Could not inspect SubCA key", error);
        }
        ca.setCertificateChain(chain);
        var history = new ArrayList<>(ca.getCertificateHistory());
        if (!history.contains(chain.getFirst())) history.add(chain.getFirst());
        ca.setCertificateHistory(history);
        ca.setCertificateSource("OFFLINE_ROOT");
        return repository.save(ca);
    }

    @Transactional
    public UUID issueLeaf(String tenantId, UUID caId, UUID keyId, UUID versionId,
            String subjectDn, LeafProfile profile) {
        return issueLeaf(tenantId, caId, keyId, versionId, subjectDn, profile,
                SigningCertificateProfile.CREDENTIAL_SIGNING, null);
    }

    @Transactional
    public UUID issueLeaf(String tenantId, UUID caId, UUID keyId, UUID versionId,
            String subjectDn, LeafProfile profile, SigningCertificateProfile purpose, String csrPem) {
        if (profile == null) profile = LeafProfile.GENERIC;
        if (purpose == null) purpose = SigningCertificateProfile.CREDENTIAL_SIGNING;
        var csr = csrPem == null || csrPem.isBlank() ? null : parseCsr(csrPem);
        var subject = csr == null ? genericSubject(subjectDn) : csr.getSubject().getDer();
        if (csr != null && csr.getSubject().getAttributes().isEmpty()) {
            throw new IllegalArgumentException("Leaf subject required");
        }
        if (profile != LeafProfile.GENERIC) legalPersonSubject(subject);
        var ca = owned(tenantId, caId);
        if (repository.existsByParentCaId(caId)) {
            throw new IllegalArgumentException("Choose a CA at the end of a branch to issue certificates or sign CSRs");
        }
        if (ca.getCertificateChain().isEmpty()) throw new IllegalArgumentException("SubCA needs a certificate");
        if (repository.existsByKeyId(keyId)) throw new IllegalArgumentException("CA key cannot be a leaf signing key");
        var issuer = validateChain(ca.getCertificateChain()).getFirst();
        legalIssuerName(issuer.getSubject().getDer());
        var version = keys.version(tenantId, keyId, versionId);
        if (!version.getUsages().contains(SigningKeyUsage.SIGN)) throw new IllegalArgumentException("Credential key must sign");
        if (!SigningKeyService.ACTIVE.equals(version.getStatus())
                && !SigningKeyService.PREPARED.equals(version.getStatus())) {
            throw new IllegalArgumentException("Credential key version must be active or prepared");
        }
        try {
            var spki = ((AsymmetricJWK) JWK.parse(version.getPublicJwk())).toPublicKey().getEncoded();
            if (csr != null && !java.util.Arrays.equals(spki, csr.getSpkiDer())) {
                throw new IllegalArgumentException("CSR does not match the registered key");
            }
            var now = Instant.now();
            var issuerExpiry = Instant.ofEpochSecond(issuer.getNotAfter());
            var expiry = now.plus(LEAF_VALIDITY).isBefore(issuerExpiry) ? now.plus(LEAF_VALIDITY) : issuerExpiry;
            if (!expiry.isAfter(now)) throw new IllegalArgumentException("SubCA certificate expires too soon");
            // Copy only SAN after SDK proof-of-possession validation; the CA controls usages.
            var requested = csr == null ? List.<ExtensionInfo>of() : csr.getExtensions().stream()
                    .filter(extension -> SAN_OID.equals(extension.getOid()))
                    .map(extension -> new ExtensionInfo(extension.getOid(), false, extension.getValueDer())).toList();
            String caUrl = null;
            if (profile == LeafProfile.PID) {
                var url = URI.create(publicBaseUrl + "/public/v1/issuing-pki/" + caId
                        + "/certificates/" + fingerprint(ca.getCertificateChain().getFirst()));
                if (!"https".equals(url.getScheme()) && !"http".equals(url.getScheme())) {
                    throw new IllegalArgumentException("Public CA URL must use HTTP(S)");
                }
                caUrl = url.toString();
            }
            var usage = profile == LeafProfile.EAA ? DIGITAL_SIGNATURE | CONTENT_COMMITMENT : DIGITAL_SIGNATURE;
            var spec = new CertificateSpec(subject, issuer.getDer(), spki, serial(),
                    now.minusSeconds(CLOCK_SKEW_SECONDS).getEpochSecond(), expiry.getEpochSecond(),
                    ManagedCertificates.algorithm(caAlgorithm(tenantId, ca)), CertificateRole.LEAF, null, usage,
                    List.of(), List.of(), caUrl, profile == LeafProfile.PID ? List.of(PID_TYPE) : List.of(), requested);
            var leaf = sign(tenantId, ca, spec);
            var chain = new ArrayList<String>();
            chain.add(leaf);
            chain.addAll(ca.getCertificateChain());
            return keys.setCertificateChain(tenantId, keyId, versionId, chain,
                    purpose, SigningCertificateSource.CA_ISSUED,
                    null, profile == LeafProfile.GENERIC ? null : profile.name(), caId);
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Could not issue leaf certificate", error);
        }
    }

    /** Certify a prepared intermediate after its key's signing grant has committed. */
    @Transactional
    public IssuingSubcaEntity certify(String tenantId, UUID id) {
        var ca = owned(tenantId, id);
        if (ca.getParentCaId() == null) throw new IllegalArgumentException("Parent CA required");
        if (!ca.getCertificateChain().isEmpty()) throw new IllegalArgumentException("CA already certified");
        var parent = owned(tenantId, ca.getParentCaId());
        var ancestors = checkParent(parent);
        try {
            var issuer = ancestors.getFirst();
            var version = keys.version(tenantId, ca.getKeyId(), ca.getKeyVersionId());
            var spki = ((AsymmetricJWK) JWK.parse(version.getPublicJwk())).toPublicKey().getEncoded();
            var now = Instant.now();
            var issuerExpiry = Instant.ofEpochSecond(issuer.getNotAfter());
            var expiry = now.plus(CA_VALIDITY).isBefore(issuerExpiry) ? now.plus(CA_VALIDITY) : issuerExpiry;
            var spec = new CertificateSpec(legalIssuerName(ca.getSubjectDn()), issuer.getDer(), spki, serial(),
                    now.minusSeconds(CLOCK_SKEW_SECONDS).getEpochSecond(), expiry.getEpochSecond(),
                    ManagedCertificates.algorithm(caAlgorithm(tenantId, parent)), CertificateRole.AUTHORITY,
                    null, CA_USAGE, List.of(), List.of(), null, List.of(), List.of());
            var certificate = sign(tenantId, parent, spec);
            var chain = new ArrayList<String>();
            chain.add(certificate);
            chain.addAll(parent.getCertificateChain());
            validateChain(chain);
            ca.setCertificateChain(chain);
            ca.setCertificateHistory(List.of(chain.getFirst()));
            ca.setCertificateSource("PLATFORM_CA");
            return repository.save(ca);
        } catch (Exception error) {
            throw new IllegalArgumentException("Could not certify intermediate", error);
        }
    }

    private static CsrInfo parseCsr(String pem) {
        return ManagedCertificates.use(x509 -> x509.verifyCsr(x509.parsePemCsr(pem)));
    }

    /** Reject exhausted paths before provisioning a new intermediate's key. */
    private List<CertificateInfo> checkParent(IssuingSubcaEntity parent) {
        if (parent.getCertificateChain().isEmpty()) throw new IllegalArgumentException("Parent CA needs a certificate");
        var ancestors = validateChain(parent.getCertificateChain());
        for (var index = 0; index < ancestors.size(); index++) {
            if (ancestors.get(index).getPathLength() != null && ancestors.get(index).getPathLength() <= index) {
                throw new IllegalArgumentException("Parent CA path length does not permit another intermediate");
            }
        }
        return ancestors;
    }

    /** Sign SDK-built bytes without exporting provider keys. */
    private String sign(String tenantId, IssuingSubcaEntity ca, CertificateSpec spec) {
        var key = keys.owned(tenantId, ca.getKeyId());
        var version = keys.version(tenantId, ca.getKeyId(), ca.getKeyVersionId());
        var provider = providers.provider(tenantId, key.getProviderId());
        var reference = new SigningKeyRef(version.getKeyUri(), version.getPublicJwk(), version.getAlgorithm());
        return ManagedCertificates.use(x509 -> {
            var tbs = x509.prepare(spec);
            var issuerSpki = spec.getIssuerCertificate() == null ? spec.getSpkiDer()
                    : x509.inspect(spec.getIssuerCertificate()).getSpkiDer();
            var certificate = x509.complete(tbs, provider.sign(reference, tbs), SignatureEncoding.JOSE_RAW, issuerSpki);
            return Base64.getEncoder().encodeToString(certificate);
        });
    }

    public String publicCertificate(UUID id, String fingerprint) {
        var ca = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("SubCA not found"));
        return ca.getCertificateHistory().stream().filter(cert -> fingerprint(cert).equals(fingerprint))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("SubCA certificate not found"));
    }

    private static String fingerprint(String certificate) {
        return ManagedCertificates.info(certificate).getFingerprint();
    }

    private String caAlgorithm(String tenantId, IssuingSubcaEntity ca) {
        return keys.version(tenantId, ca.getKeyId(), ca.getKeyVersionId()).getAlgorithm();
    }

    private static byte[] serial() {
        return new BigInteger(SERIAL_BITS, new SecureRandom()).setBit(SERIAL_BITS - 1).toByteArray();
    }

    private static byte[] genericSubject(String subjectDn) {
        if (subjectDn == null || subjectDn.isBlank()) throw new IllegalArgumentException("Leaf subject required");
        return ManagedCertificates.subject(subjectDn);
    }

    private static void legalPersonSubject(byte[] subject) {
        var name = ManagedCertificates.use(x509 -> x509.inspectName(subject));
        for (var oid : List.of(COUNTRY, ORGANISATION, ORGANISATION_ID, COMMON_NAME)) requireAttribute(name, oid);
        requireCountry(name);
    }

    private static byte[] legalIssuerName(String subjectDn) {
        if (subjectDn == null || subjectDn.isBlank()) throw new IllegalArgumentException("SubCA subject required");
        return legalIssuerName(ManagedCertificates.subject(subjectDn));
    }

    private static byte[] legalIssuerName(byte[] subject) {
        var name = ManagedCertificates.use(x509 -> x509.inspectName(subject));
        for (var oid : List.of(COUNTRY, ORGANISATION, COMMON_NAME)) requireAttribute(name, oid);
        requireCountry(name);
        var identifiers = name.getAttributes().stream().filter(a -> ORGANISATION_ID.equals(a.getOid())).toList();
        if (identifiers.size() > 1) throw new IllegalArgumentException("Multiple organizationIdentifier values");
        if (!identifiers.isEmpty() && (identifiers.getFirst().getValue().isBlank()
                || identifiers.getFirst().getValue().equals(requireAttribute(name, ORGANISATION)))) {
            throw new IllegalArgumentException("SubCA organizationIdentifier must differ from O");
        }
        return subject;
    }

    private static String requireAttribute(NameInfo name, String oid) {
        var values = name.getAttributes().stream().filter(a -> oid.equals(a.getOid())).toList();
        if (values.size() != 1 || values.getFirst().getValue().isBlank()) {
            throw new IllegalArgumentException("Subject requires one non-empty attribute " + oid);
        }
        return values.getFirst().getValue();
    }

    private static void requireCountry(NameInfo name) {
        if (!requireAttribute(name, COUNTRY).matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("Subject C must be a two-letter country code");
        }
    }

    private static List<CertificateInfo> validateChain(List<String> chain) {
        return ManagedCertificates.validate(chain, ChainKind.AUTHORITIES, ChainEnd.COMPLETE, IssuerUsage.REQUIRED, null);
    }

    public enum LeafProfile { GENERIC, PID, EAA }
}
