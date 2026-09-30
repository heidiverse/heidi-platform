// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.heidiverse.heidi.entity.data.repository.IdentityKeySlotRepository;
import org.heidiverse.heidi.entity.data.repository.SigningCertificateRepository;
import org.heidiverse.heidi.entity.data.repository.SigningKeyRepository;
import org.heidiverse.heidi.entity.data.repository.SigningKeyVersionRepository;
import org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity;
import org.heidiverse.heidi.entity.model.entity.SigningCertificateEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateSource;
import org.junit.jupiter.api.Test;

class IssuingPkiChainPublicationTest {
    @Test
    void omitsSelfSignedSubcaAnchor() throws Exception {
        var subcaKey = pair();
        var subca = ca("CN=Issuing SubCA", "CN=Issuing SubCA", subcaKey, subcaKey);
        assertEquals(List.of("leaf"), published(List.of("leaf", subca),
                SigningCertificateSource.CA_ISSUED));
    }

    @Test
    void omitsOfflineRootAnchor() throws Exception {
        var rootKey = pair();
        var subcaKey = pair();
        var root = ca("CN=Offline Root", "CN=Offline Root", rootKey, rootKey);
        var subca = ca("CN=Offline Root", "CN=Issuing SubCA", subcaKey, rootKey);
        assertEquals(List.of("leaf", subca),
                published(List.of("leaf", subca, root),
                        SigningCertificateSource.CA_ISSUED));
    }

    @Test
    void omitsImportedSelfSignedAnchor() throws Exception {
        var rootKey = pair();
        var root = ca("CN=Offline Root", "CN=Offline Root", rootKey, rootKey);
        assertEquals(List.of("leaf"), published(List.of("leaf", root),
                SigningCertificateSource.IMPORTED));
    }

    @Test
    void retainsImportedNonSelfSignedIntermediate() throws Exception {
        var rootKey = pair();
        var subcaKey = pair();
        var subca = ca("CN=Offline Root", "CN=Issuing SubCA", subcaKey, rootKey);
        assertEquals(List.of("leaf", subca), published(List.of("leaf", subca),
                SigningCertificateSource.IMPORTED));
    }

    private static List<String> published(List<String> chain, SigningCertificateSource source) {
        var slots = mock(IdentityKeySlotRepository.class);
        var keys = mock(SigningKeyRepository.class);
        var versions = mock(SigningKeyVersionRepository.class);
        var certificates = mock(SigningCertificateRepository.class);
        var service = new IdentityKeySlotService(slots, null, keys, versions, certificates,
                null, null, null, null, null, null);

        var key = new SigningKeyEntity();
        key.setId(UUID.randomUUID());
        key.setTenantId("tenant-a");
        var version = new SigningKeyVersionEntity();
        version.setId(UUID.randomUUID());
        version.setKeyId(key.getId());
        version.setStatus(SigningKeyService.ACTIVE);
        key.setActiveVersionId(version.getId());

        var slot = new IdentityKeySlotEntity();
        slot.setKeyId(key.getId());
        slot.setType(IdentityKeySlotType.CREDENTIAL_SIGNING);
        slot.setTrustSystem(IssuerTrustSystem.EUDI);
        slot.setCertificateId(UUID.randomUUID());

        var certificate = new SigningCertificateEntity();
        certificate.setId(slot.getCertificateId());
        certificate.setKeyVersionId(version.getId());
        certificate.setProfile(SigningCertificateProfile.CREDENTIAL_SIGNING);
        certificate.setTrustSystem(IssuerTrustSystem.EUDI);
        certificate.setSource(source);
        certificate.setCertificateChain(chain);
        certificate.setNotBefore(Instant.now().minusSeconds(60));
        certificate.setNotAfter(Instant.now().plusSeconds(3600));

        when(keys.findById(key.getId())).thenReturn(Optional.of(key));
        when(slots.findAllByKeyId(key.getId())).thenReturn(List.of(slot));
        when(versions.findById(version.getId())).thenReturn(Optional.of(version));
        when(certificates.findById(certificate.getId())).thenReturn(Optional.of(certificate));

        return service.resolveByKey("tenant-a", key.getId(),
                IdentityKeySlotType.CREDENTIAL_SIGNING).orElseThrow().certificateChain();
    }

    private static KeyPair pair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String ca(String issuer, String subject, KeyPair subjectKey,
            KeyPair issuerKey) throws Exception {
        var now = Instant.now();
        var builder = new JcaX509v3CertificateBuilder(new X500Name(issuer),
                java.math.BigInteger.ONE, Date.from(now.minusSeconds(60)),
                Date.from(now.plusSeconds(3600)), new X500Name(subject), subjectKey.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign));
        var certificate = builder.build(new JcaContentSignerBuilder("SHA256withRSA")
                .build(issuerKey.getPrivate()));
        return Base64.getEncoder().encodeToString(certificate.getEncoded());
    }
}
