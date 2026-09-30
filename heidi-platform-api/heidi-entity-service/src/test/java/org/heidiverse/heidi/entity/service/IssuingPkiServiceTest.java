// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.RSAKey;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.heidiverse.heidi.entity.data.repository.IssuingSubcaRepository;
import org.heidiverse.heidi.entity.model.entity.IssuingSubcaEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateSource;
import org.heidiverse.heidi.shared.signing.ProviderHealth;
import org.heidiverse.heidi.shared.signing.SigningKeyProvider;
import org.heidiverse.heidi.shared.signing.SigningKeyRef;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IssuingPkiServiceTest {
    private static final String TENANT = "tenant-a";
    private static final String PUBLIC_URL = "https://issuer.example";

    private final IssuingSubcaRepository repository = mock(IssuingSubcaRepository.class);
    private final SigningKeyService keys = mock(SigningKeyService.class);
    private final SigningProviderService providers = mock(SigningProviderService.class);
    private final IssuingPkiService service =
            new IssuingPkiService(repository, keys, providers, mock(IssuerService.class), PUBLIC_URL);

    private IssuingSubcaEntity ca;
    private KeyPair caPair;
    private SigningKeyVersionEntity caVersion;
    private SigningKeyVersionEntity leafVersion;

    @BeforeEach
    void setUp() throws Exception {
        caPair = pair();
        var leafPair = pair();
        ca = new IssuingSubcaEntity();
        ca.setId(UUID.randomUUID());
        ca.setTenantId(TENANT);
        ca.setKeyId(UUID.randomUUID());
        ca.setKeyVersionId(UUID.randomUUID());
        ca.setSubjectDn("CN=Test Issuing CA,O=Example,C=CH");

        caVersion = version(ca.getKeyId(), ca.getKeyVersionId(), caPair);
        leafVersion = version(UUID.randomUUID(), UUID.randomUUID(), leafPair);
        var caKey = new SigningKeyEntity();
        caKey.setId(ca.getKeyId());
        caKey.setTenantId(TENANT);
        caKey.setProviderId(7);

        when(repository.findById(ca.getId())).thenReturn(Optional.of(ca));
        when(repository.save(any(IssuingSubcaEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(keys.owned(TENANT, ca.getKeyId())).thenReturn(caKey);
        when(keys.version(TENANT, ca.getKeyId(), ca.getKeyVersionId())).thenReturn(caVersion);
        when(keys.version(TENANT, leafVersion.getKeyId(), leafVersion.getId()))
                .thenReturn(leafVersion);
        when(providers.provider(TENANT, 7)).thenReturn(new RsaProvider(caPair));
    }

    @Test
    void selfSignedCaIssuesPidAndEaaLeaves() throws Exception {
        service.selfSign(TENANT, ca.getId());
        var issuer = certificate(ca.getCertificateChain().getFirst());
        issuer.verify(issuer.getPublicKey());
        assertEquals(0, issuer.getBasicConstraints());
        assertTrue(issuer.getKeyUsage()[5]);

        var issued = new ArrayList<List<String>>();
        when(keys.setCertificateChain(eq(TENANT), eq(leafVersion.getKeyId()),
                eq(leafVersion.getId()), any(), eq(SigningCertificateProfile.CREDENTIAL_SIGNING),
                eq(SigningCertificateSource.CA_ISSUED), eq(IssuerTrustSystem.EUDI), any()))
                .thenAnswer(call -> {
                    issued.add(call.getArgument(3));
                    return UUID.randomUUID();
                });

        for (var profile : IssuingPkiService.LeafProfile.values()) {
            service.issueLeaf(TENANT, ca.getId(), leafVersion.getKeyId(), leafVersion.getId(),
                    "CN=Test Issuer,O=Example AG,C=CH,2.5.4.97=VATCH-123456789", profile);
            var chain = issued.getLast();
            assertEquals(2, chain.size());
            assertEquals(ca.getCertificateChain().getFirst(), chain.getLast());
            var leaf = certificate(chain.getFirst());
            leaf.verify(issuer.getPublicKey());
            assertEquals(issuer.getSubjectX500Principal(), leaf.getIssuerX500Principal());
            assertEquals(-1, leaf.getBasicConstraints());
            assertTrue(leaf.getKeyUsage()[0]);
            assertEquals(profile == IssuingPkiService.LeafProfile.EAA,
                    leaf.getKeyUsage()[1]);
            assertFalse(leaf.getKeyUsage()[5]);
            assertTrue(leaf.getExtensionValue(Extension.subjectKeyIdentifier.getId()) != null);
            assertEquals(profile == IssuingPkiService.LeafProfile.PID,
                    leaf.getExtensionValue(Extension.authorityInfoAccess.getId()) != null);
            assertEquals(profile == IssuingPkiService.LeafProfile.PID,
                    leaf.getExtensionValue("1.3.6.1.5.5.7.1.3") != null);
        }
    }

    @Test
    void rejectsAnotherOrganisationsCa() {
        assertThrows(SecurityException.class, () -> service.csr("tenant-b", ca.getId()));
    }

    @Test
    void rejectsCaWithoutIssuerCountry() {
        ca.setSubjectDn("CN=Test Issuing CA,O=Example");
        assertThrows(IllegalArgumentException.class, () -> service.selfSign(TENANT, ca.getId()));
    }

    @Test
    void rejectsIncompleteLegalPersonSubject() {
        service.selfSign(TENANT, ca.getId());
        assertThrows(IllegalArgumentException.class, () -> service.issueLeaf(TENANT,
                ca.getId(), leafVersion.getKeyId(), leafVersion.getId(),
                "CN=Test Issuer,O=Example AG", IssuingPkiService.LeafProfile.PID));
    }

    @Test
    void importsOfflineRootChainForTheSameSubcaKey() throws Exception {
        service.selfSign(TENANT, ca.getId());
        var original = ca.getCertificateChain().getFirst();
        var rootPair = pair();
        var rootName = new X500Name("CN=Offline Root,O=Example");
        var subcaName = new X500Name(ca.getSubjectDn());
        var root = signedCa(rootName, rootName, rootPair, rootPair, 1);
        var subca = signedCa(rootName, subcaName, caPair, rootPair, 0);

        service.importChain(TENANT, ca.getId(), List.of(encode(subca), encode(root)));

        assertEquals("OFFLINE_ROOT", ca.getCertificateSource());
        assertEquals(2, ca.getCertificateChain().size());
        assertFalse(original.equals(ca.getCertificateChain().getFirst()));
        assertThrows(IllegalArgumentException.class, () -> service.importChain(TENANT,
                ca.getId(), List.of(encode(root), encode(subca))));
    }

    @Test
    void trustAnchorsAreRootsOfCertifiedSubcas() throws Exception {
        service.selfSign(TENANT, ca.getId());

        // Offline-root SubCA: the root, not the SubCA, anchors its chain.
        var rootPair = pair();
        var rootName = new X500Name("CN=Offline Root,O=Example");
        var root = signedCa(rootName, rootName, rootPair, rootPair, 1);
        var imported = subca(List.of(encode(signedCa(rootName,
                new X500Name("CN=Imported CA,O=Example,C=CH"), pair(), rootPair, 0)), encode(root)));

        var pending = subca(List.of());
        var selfName = new X500Name("CN=Expired CA,O=Example,C=CH");
        var expiredPair = pair();
        var expired = subca(List.of(encode(signedCa(selfName, selfName, expiredPair, expiredPair, 0,
                Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600)))));
        when(repository.findAllByTenantIdOrderByCreatedAtDesc(TENANT))
                .thenReturn(List.of(ca, imported, pending, expired, ca));

        assertEquals(List.of(ca.getCertificateChain().getFirst(), encode(root)),
                service.trustAnchors(TENANT));
    }

    private static IssuingSubcaEntity subca(List<String> chain) {
        var subca = new IssuingSubcaEntity();
        subca.setId(UUID.randomUUID());
        subca.setTenantId(TENANT);
        subca.setCertificateChain(chain);
        return subca;
    }

    private static X509Certificate signedCa(X500Name issuer, X500Name subject,
            KeyPair subjectPair, KeyPair issuerPair, int pathLength) throws Exception {
        var now = Instant.now();
        return signedCa(issuer, subject, subjectPair, issuerPair, pathLength,
                now.minusSeconds(60), now.plusSeconds(3600));
    }

    private static X509Certificate signedCa(X500Name issuer, X500Name subject,
            KeyPair subjectPair, KeyPair issuerPair, int pathLength,
            Instant notBefore, Instant notAfter) throws Exception {
        var builder = new JcaX509v3CertificateBuilder(issuer, BigInteger.valueOf(pathLength + 1),
                Date.from(notBefore), Date.from(notAfter), subject,
                subjectPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(pathLength));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        return new JcaX509CertificateConverter().getCertificate(builder.build(
                new JcaContentSignerBuilder("SHA256withRSA").build(issuerPair.getPrivate())));
    }

    private static String encode(X509Certificate certificate) throws Exception {
        return Base64.getEncoder().encodeToString(certificate.getEncoded());
    }

    private static SigningKeyVersionEntity version(UUID keyId, UUID id, KeyPair pair) {
        var version = new SigningKeyVersionEntity();
        version.setId(id);
        version.setKeyId(keyId);
        version.setKeyUri("software://kc/" + keyId + "/" + id);
        version.setAlgorithm("RS256");
        version.setPublicJwk(new RSAKey.Builder((RSAPublicKey) pair.getPublic()).build().toJSONString());
        version.setStatus(SigningKeyService.ACTIVE);
        version.setUsages(Set.of(SigningKeyUsage.SIGN));
        return version;
    }

    private static KeyPair pair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509Certificate certificate(String encoded) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(
                new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
    }

    private record RsaProvider(KeyPair pair) implements SigningKeyProvider {
        @Override public String scheme() { return "software"; }
        @Override public List<String> supportedAlgorithms() { return List.of("RS256"); }
        @Override public SigningKeyRef resolve(String uri) { throw new UnsupportedOperationException(); }
        @Override public ProviderHealth health() { throw new UnsupportedOperationException(); }
        @Override public byte[] sign(SigningKeyRef ref, byte[] message) {
            try {
                var signature = Signature.getInstance("SHA256withRSA");
                signature.initSign(pair.getPrivate());
                signature.update(message);
                return signature.sign();
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }
    }
}
