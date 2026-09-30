// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import com.nimbusds.jose.jwk.AsymmetricJWK;
import com.nimbusds.jose.jwk.JWK;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.net.URI;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.heidiverse.heidi.entity.data.repository.IssuingSubcaRepository;
import org.heidiverse.heidi.entity.model.entity.IssuingSubcaEntity;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateSource;
import org.heidiverse.heidi.shared.signing.SigningCsrRequest;
import org.heidiverse.heidi.shared.signing.SigningKeyRef;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;
import org.heidiverse.heidi.signing.adapters.ProviderContentSigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues certificates with an organisation key held by the signing service. */
@Service
public class IssuingPkiService {
    private static final Duration CA_VALIDITY = Duration.ofDays(365);
    private static final Duration LEAF_VALIDITY = Duration.ofDays(90);
    private static final ASN1ObjectIdentifier QC_STATEMENTS = new ASN1ObjectIdentifier("1.3.6.1.5.5.7.1.3");
    private static final ASN1ObjectIdentifier QC_TYPE = new ASN1ObjectIdentifier("0.4.0.1862.1.6");
    private static final ASN1ObjectIdentifier PID_TYPE = new ASN1ObjectIdentifier("0.4.0.194126.1.1");
    private static final ASN1ObjectIdentifier ORGANISATION_ID = new ASN1ObjectIdentifier("2.5.4.97");

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
        return repository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public IssuingSubcaEntity create(String tenantId, String subjectDn, String algorithm,
            Integer providerId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("Organisation required");
        legalIssuerName(subjectDn);
        var id = UUID.randomUUID();
        var key = keys.create(tenantId, "subca-" + id, java.util.Set.of(SigningKeyUsage.SIGN),
                algorithm, providerId, null);
        var subca = new IssuingSubcaEntity();
        subca.setId(id);
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

    /** Roots anchoring the organisation's certified, unexpired SubCAs. */
    public List<String> trustAnchors(String tenantId) {
        var anchors = new java.util.LinkedHashSet<String>();
        for (var ca : repository.findAllByTenantIdOrderByCreatedAtDesc(tenantId)) {
            if (ca.getCertificateChain().isEmpty()) continue;

            // Verifiers anchor on the chain's last certificate: self-signed SubCA or offline root.
            var root = ca.getCertificateChain().getLast();
            try {
                parse(List.of(root)).getFirst().checkValidity();
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
        return subca;
    }

    public String csr(String tenantId, UUID id) {
        var ca = owned(tenantId, id);
        return keys.createCsr(tenantId, ca.getKeyId(), ca.getKeyVersionId(),
                new SigningCsrRequest(ca.getSubjectDn(), List.of(), List.of()));
    }

    @Transactional
    public IssuingSubcaEntity selfSign(String tenantId, UUID id) {
        var ca = owned(tenantId, id);
        var name = legalIssuerName(ca.getSubjectDn());
        if (!ca.getCertificateChain().isEmpty()) throw new IllegalArgumentException("SubCA already certified");
        try {
            var version = keys.version(tenantId, ca.getKeyId(), ca.getKeyVersionId());
            var publicKey = ((AsymmetricJWK) JWK.parse(version.getPublicJwk())).toPublicKey();
            var now = Instant.now();
            var builder = new X509v3CertificateBuilder(name, serial(), Date.from(now.minusSeconds(60)),
                    Date.from(now.plus(CA_VALIDITY)), name,
                    SubjectPublicKeyInfo.getInstance(publicKey.getEncoded()));
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
            builder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            builder.addExtension(Extension.subjectKeyIdentifier, false,
                    new JcaX509ExtensionUtils().createSubjectKeyIdentifier(publicKey));
            var cert = new JcaX509CertificateConverter().getCertificate(
                    builder.build(signer(tenantId, ca)));
            cert.verify(publicKey);
            ca.setCertificateChain(List.of(encode(cert)));
            ca.setCertificateHistory(List.of(encode(cert)));
            ca.setCertificateSource("SELF_SIGNED");
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
        var certs = parse(chain);
        validateChain(certs);
        var version = keys.version(tenantId, ca.getKeyId(), ca.getKeyVersionId());
        try {
            var publicKey = ((AsymmetricJWK) JWK.parse(version.getPublicJwk())).toPublicKey();
            if (!java.util.Arrays.equals(publicKey.getEncoded(), certs.getFirst().getPublicKey().getEncoded())) {
                throw new IllegalArgumentException("SubCA certificate does not match its key");
            }
            if (!new X500Name(ca.getSubjectDn()).equals(
                    X500Name.getInstance(certs.getFirst().getSubjectX500Principal().getEncoded()))) {
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
        if (profile == null) throw new IllegalArgumentException("PID or EAA profile required");
        var subject = legalPersonSubject(subjectDn);
        var ca = owned(tenantId, caId);
        if (ca.getCertificateChain().isEmpty()) throw new IllegalArgumentException("SubCA needs a certificate");
        if (ca.getKeyId().equals(keyId)) throw new IllegalArgumentException("SubCA key cannot be a credential key");
        var issuer = parse(ca.getCertificateChain()).getFirst();
        legalIssuerName(X500Name.getInstance(issuer.getSubjectX500Principal().getEncoded()));
        validateCa(issuer);
        var version = keys.version(tenantId, keyId, versionId);
        if (!version.getUsages().contains(SigningKeyUsage.SIGN)) throw new IllegalArgumentException("Credential key must sign");
        if (!SigningKeyService.ACTIVE.equals(version.getStatus())
                && !SigningKeyService.PREPARED.equals(version.getStatus())) {
            throw new IllegalArgumentException("Credential key version must be active or prepared");
        }
        try {
            var publicKey = ((AsymmetricJWK) JWK.parse(version.getPublicJwk())).toPublicKey();
            var now = Instant.now();
            var expiry = now.plus(LEAF_VALIDITY).isBefore(issuer.getNotAfter().toInstant())
                    ? now.plus(LEAF_VALIDITY) : issuer.getNotAfter().toInstant();
            if (!expiry.isAfter(now)) throw new IllegalArgumentException("SubCA certificate expires too soon");
            var builder = new X509v3CertificateBuilder(
                    X500Name.getInstance(issuer.getSubjectX500Principal().getEncoded()), serial(),
                    Date.from(now.minusSeconds(60)), Date.from(expiry), subject,
                    SubjectPublicKeyInfo.getInstance(publicKey.getEncoded()));
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            var keyUsage = profile == LeafProfile.EAA
                    ? KeyUsage.digitalSignature | KeyUsage.nonRepudiation
                    : KeyUsage.digitalSignature;
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(keyUsage));
            builder.addExtension(Extension.subjectKeyIdentifier, false,
                    new JcaX509ExtensionUtils().createSubjectKeyIdentifier(publicKey));
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(issuer));
            if (profile == LeafProfile.PID) {
                var caUrl = URI.create(publicBaseUrl + "/public/v1/issuing-pki/" + caId
                        + "/certificates/" + fingerprint(ca.getCertificateChain().getFirst()));
                if (!"https".equals(caUrl.getScheme()) && !"http".equals(caUrl.getScheme())) {
                    throw new IllegalArgumentException("Public CA URL must use HTTP(S)");
                }
                builder.addExtension(Extension.authorityInfoAccess, false,
                        new AuthorityInformationAccess(AccessDescription.id_ad_caIssuers,
                                new GeneralName(GeneralName.uniformResourceIdentifier, caUrl.toString())));
                builder.addExtension(QC_STATEMENTS, false,
                        new DERSequence(new DERSequence(new org.bouncycastle.asn1.ASN1Encodable[] {
                                QC_TYPE, new DERSequence(PID_TYPE) })));
            }
            var leaf = new JcaX509CertificateConverter().getCertificate(
                    builder.build(signer(tenantId, ca)));
            leaf.verify(issuer.getPublicKey());
            var chain = new ArrayList<String>();
            chain.add(encode(leaf));
            chain.addAll(ca.getCertificateChain());
            return keys.setCertificateChain(tenantId, keyId, versionId, chain,
                    SigningCertificateProfile.CREDENTIAL_SIGNING, SigningCertificateSource.CA_ISSUED,
                    IssuerTrustSystem.EUDI, profile.name());
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Could not issue leaf certificate", error);
        }
    }

    public String publicCertificate(UUID id, String fingerprint) {
        var ca = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("SubCA not found"));
        return ca.getCertificateHistory().stream().filter(cert -> fingerprint(cert).equals(fingerprint))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("SubCA certificate not found"));
    }

    private static String fingerprint(String certificate) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(certificate));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception error) { throw new IllegalArgumentException("Invalid CA certificate", error); }
    }

    private ProviderContentSigner signer(String tenantId, IssuingSubcaEntity ca) {
        var key = keys.owned(tenantId, ca.getKeyId());
        var version = keys.version(tenantId, ca.getKeyId(), ca.getKeyVersionId());
        var signature = switch (version.getAlgorithm()) {
            case "ES256" -> "SHA256withECDSA";
            case "ES384" -> "SHA384withECDSA";
            case "ES512" -> "SHA512withECDSA";
            case "RS256" -> "SHA256withRSA";
            default -> throw new IllegalArgumentException("Unsupported CA signing algorithm");
        };
        return new ProviderContentSigner(providers.provider(tenantId, key.getProviderId()),
                new SigningKeyRef(version.getKeyUri(), version.getPublicJwk(), version.getAlgorithm()),
                signature, version.getAlgorithm());
    }

    private static BigInteger serial() { return new BigInteger(159, new SecureRandom()).setBit(158); }

    private static X500Name legalPersonSubject(String subjectDn) {
        if (subjectDn == null || subjectDn.isBlank()) {
            throw new IllegalArgumentException("Leaf subject required");
        }
        try {
            var subject = new X500Name(subjectDn);
            for (var field : new ASN1ObjectIdentifier[] {
                    BCStyle.C, BCStyle.O, ORGANISATION_ID, BCStyle.CN }) {
                if (subject.getRDNs(field).length != 1
                        || subject.getRDNs(field)[0].getFirst() == null
                        || subject.getRDNs(field)[0].getFirst().getValue().toString().isBlank()) {
                    throw new IllegalArgumentException("Leaf subject needs one C, O, organizationIdentifier and CN");
                }
            }
            var country = subject.getRDNs(BCStyle.C)[0].getFirst().getValue().toString();
            if (!country.matches("[A-Z]{2}")) {
                throw new IllegalArgumentException("Leaf subject C must be a two-letter country code");
            }
            return subject;
        } catch (IllegalArgumentException error) { throw error; }
        catch (Exception error) { throw new IllegalArgumentException("Invalid leaf subject", error); }
    }

    private static X500Name legalIssuerName(String subjectDn) {
        if (subjectDn == null || subjectDn.isBlank()) {
            throw new IllegalArgumentException("SubCA subject required");
        }
        return legalIssuerName(new X500Name(subjectDn));
    }

    private static X500Name legalIssuerName(X500Name subject) {
        for (var field : new ASN1ObjectIdentifier[] {BCStyle.C, BCStyle.O, BCStyle.CN}) {
            if (subject.getRDNs(field).length != 1
                    || subject.getRDNs(field)[0].getFirst() == null
                    || subject.getRDNs(field)[0].getFirst().getValue().toString().isBlank()) {
                throw new IllegalArgumentException("SubCA subject needs one C, O and CN");
            }
        }
        if (!subject.getRDNs(BCStyle.C)[0].getFirst().getValue().toString().matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("SubCA subject C must be a two-letter country code");
        }
        var organisationIds = subject.getRDNs(ORGANISATION_ID);
        if (organisationIds.length > 1) {
            throw new IllegalArgumentException("SubCA subject has multiple organizationIdentifier values");
        }
        if (organisationIds.length == 1) {
            var identifier = organisationIds[0].getFirst().getValue().toString();
            var organisation = subject.getRDNs(BCStyle.O)[0].getFirst().getValue().toString();
            if (identifier.isBlank() || identifier.equals(organisation)) {
                throw new IllegalArgumentException("SubCA organizationIdentifier must differ from O");
            }
        }
        return subject;
    }

    private static String encode(X509Certificate cert) throws Exception {
        return Base64.getEncoder().encodeToString(cert.getEncoded());
    }

    private static List<X509Certificate> parse(List<String> chain) {
        try {
            var factory = CertificateFactory.getInstance("X.509");
            var parsed = new ArrayList<X509Certificate>();
            for (var item : chain) parsed.add((X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(Base64.getDecoder().decode(item))));
            return parsed;
        } catch (Exception error) { throw new IllegalArgumentException("Invalid certificate encoding", error); }
    }

    private static void validateCa(X509Certificate cert) {
        try { cert.checkValidity(); } catch (Exception error) { throw new IllegalArgumentException("Expired CA certificate", error); }
        var usage = cert.getKeyUsage();
        if (cert.getBasicConstraints() < 0 || usage == null || usage.length <= 5 || !usage[5]) {
            throw new IllegalArgumentException("CA certificate needs CA=true and keyCertSign");
        }
    }

    private static void validateChain(List<X509Certificate> chain) {
        try {
            for (var index = 0; index < chain.size(); index++) {
                var cert = chain.get(index);
                validateCa(cert);
                if (cert.getBasicConstraints() < index) {
                    throw new IllegalArgumentException("CA path length does not permit this chain");
                }
                if (index + 1 == chain.size()) {
                    if (!cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal()))
                        throw new IllegalArgumentException("Last certificate must be the offline root");
                    cert.verify(cert.getPublicKey());
                    continue;
                }
                var issuer = chain.get(index + 1);
                if (!cert.getIssuerX500Principal().equals(issuer.getSubjectX500Principal()))
                    throw new IllegalArgumentException("CA chain issuer mismatch");
                cert.verify(issuer.getPublicKey());
            }
        } catch (IllegalArgumentException error) { throw error; }
        catch (Exception error) { throw new IllegalArgumentException("Invalid CA chain", error); }
    }

    public enum LeafProfile { PID, EAA }
}
