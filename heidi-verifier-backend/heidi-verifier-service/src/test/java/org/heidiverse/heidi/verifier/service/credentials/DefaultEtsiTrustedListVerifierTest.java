// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.heidiverse.heidi.shared.testing.TestCertificates;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;

class DefaultEtsiTrustedListVerifierTest {
    private final DefaultEtsiTrustedListVerifier verifier =
            new DefaultEtsiTrustedListVerifier(RestClient.builder());

    @Test
    void readsServiceCertificatesFromASignedLote() throws Exception {
        var signer = TestCertificates.ca("lote-signer");
        var service = TestCertificates.ca("listed-service");

        var parsed = verifier.parse(signedLote(
                signer, service, Instant.now().plus(1, ChronoUnit.DAYS), null));

        assertEquals(List.of(service.certificate()), parsed.certificates());
    }

    @Test
    void excludesLoteServicesWithUnacceptedStatus() throws Exception {
        var signer = TestCertificates.ca("lote-signer");
        var service = TestCertificates.ca("listed-service");
        var jwt = signedLote(
                signer,
                service,
                Instant.now().plus(1, ChronoUnit.DAYS),
                "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/withdrawn");

        assertTrue(verifier.parse(jwt).certificates().isEmpty());
    }

    @Test
    void rejectsExpiredLote() throws Exception {
        var signer = TestCertificates.ca("lote-signer");
        var service = TestCertificates.ca("listed-service");
        var jwt = signedLote(signer, service, Instant.now().minus(1, ChronoUnit.DAYS), null);

        assertThrows(IllegalArgumentException.class, () -> verifier.parse(jwt));
    }

    @Test
    void rejectsTamperedLote() throws Exception {
        var signer = TestCertificates.ca("lote-signer");
        var service = TestCertificates.ca("listed-service");
        var jwt = signedLote(signer, service, Instant.now().plus(1, ChronoUnit.DAYS), null);
        var parts = new String(jwt, StandardCharsets.US_ASCII).split("\\.");
        var tampered = (parts[0] + "." + tamperBase64Url(parts[1]) + "." + parts[2])
                .getBytes(StandardCharsets.US_ASCII);

        assertThrows(IllegalArgumentException.class, () -> verifier.parse(tampered));
    }

    @Test
    void rejectsLoteWithForeignX5c() throws Exception {
        var keySigner = TestCertificates.ca("lote-signer");
        var certSigner = TestCertificates.ca("foreign-signer");
        var service = TestCertificates.ca("listed-service");
        var jwt = signedLote(
                keySigner, certSigner, service, Instant.now().plus(1, ChronoUnit.DAYS), null);

        assertThrows(IllegalArgumentException.class, () -> verifier.parse(jwt));
    }

    @Test
    void rejectsLoteMissingX5c() throws Exception {
        var signer = TestCertificates.ca("lote-signer");
        var header = new JWSHeader.Builder(JWSAlgorithm.ES256).build();
        var jwt = new SignedJWT(
                header, new JWTClaimsSet.Builder().claim("LoTE", Map.of()).build());
        jwt.sign(new ECDSASigner((ECPrivateKey) signer.keyPair().getPrivate()));

        assertThrows(
                IllegalArgumentException.class,
                () -> verifier.parse(jwt.serialize().getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void rejectsUnsignedLote() throws Exception {
        var jwt = new PlainJWT(new JWTClaimsSet.Builder().claim("LoTE", Map.of()).build());

        assertThrows(
                IllegalArgumentException.class,
                () -> verifier.parse(jwt.serialize().getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void validatesChainIssuedByAListedAnchor() throws Exception {
        var ca = TestCertificates.ca("listed-ca");
        var keyPair = TestCertificates.ecKeyPair();
        var leaf = TestCertificates.leaf(ca, keyPair.getPublic(), "issuer");

        assertTrue(verifier.validatesTo(List.of(leaf.certificate()), ca.certificate()));
    }

    @Test
    void rejectsChainWithAppendedButNonIssuingAnchor() throws Exception {
        var attacker = TestCertificates.ca("attacker-ca");
        var listed = TestCertificates.ca("listed-ca");
        var keyPair = TestCertificates.ecKeyPair();
        var forgedLeaf = TestCertificates.leaf(attacker, keyPair.getPublic(), "issuer");
        var chain = List.of(forgedLeaf.certificate(), listed.certificate());

        assertFalse(verifier.validatesTo(chain, listed.certificate()));
    }

    private byte[] signedLote(
            TestCertificates.Ca signer,
            TestCertificates.Ca listedEntity,
            Instant nextUpdate,
            String serviceStatus)
            throws Exception {
        return signedLote(signer, signer, listedEntity, nextUpdate, serviceStatus);
    }

    private byte[] signedLote(
            TestCertificates.Ca keySigner,
            TestCertificates.Ca certSigner,
            TestCertificates.Ca listedEntity,
            Instant nextUpdate,
            String serviceStatus)
            throws Exception {
        var digitalIdentity = Map.of(
                "X509Certificates", List.of(Map.of(
                        "val", Base64.getEncoder().encodeToString(
                                listedEntity.certificate().getEncoded()))));
        var serviceInformation = serviceStatus == null
                ? Map.of("ServiceDigitalIdentity", digitalIdentity)
                : Map.of(
                        "ServiceDigitalIdentity", digitalIdentity,
                        "ServiceStatus", serviceStatus);
        var lote = Map.of(
                "ListAndSchemeInformation", Map.of(
                        "ListIssueDateTime", Instant.now().minus(1, ChronoUnit.HOURS).toString(),
                        "NextUpdate", nextUpdate.toString()),
                "TrustedEntitiesList", List.of(Map.of(
                        "TrustedEntityServices", List.of(Map.of(
                                "ServiceInformation", serviceInformation)))));

        var header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .x509CertChain(List.of(new com.nimbusds.jose.util.Base64(Base64.getEncoder()
                        .encodeToString(certSigner.certificate().getEncoded()))))
                .build();
        var jwt = new SignedJWT(header, new JWTClaimsSet.Builder().claim("LoTE", lote).build());
        jwt.sign(new ECDSASigner((ECPrivateKey) keySigner.keyPair().getPrivate()));
        return jwt.serialize().getBytes(StandardCharsets.US_ASCII);
    }

    private String tamperBase64Url(String segment) {
        var chars = segment.toCharArray();
        var index = chars.length / 2;
        chars[index] = chars[index] == 'A' ? 'B' : 'A';
        return new String(chars);
    }
}
