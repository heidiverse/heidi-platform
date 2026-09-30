// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.heidiverse.heidi.shared.testing.TestCertificates;
import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.junit.jupiter.api.Test;
import org.kapunsdk.credentials.models.credential.CredentialType;
import uniffi.kapun_dcql_rust.CredentialQuery;
import uniffi.kapun_dcql_rust.TrustedAuthority;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

class TrustedAuthorityVerifierTest {

    @Test
    void acceptsAnExactDidIssuerAndRejectsAnotherIssuer() {
        var verifier = verifier((issuer, entities) -> false, (chain, identifiers) -> false);
        var query = query(new TrustedAuthority("did", List.of("did:example:trusted")));

        assertDoesNotThrow(() -> verifier.verify(
                CredentialType.SdJwt,
                "not-needed",
                Map.of("iss", "did:example:trusted"),
                query));
        assertThrows(
                VpVerificationException.class,
                () -> verifier.verify(
                        CredentialType.SdJwt,
                        "not-needed",
                        Map.of("iss", "did:example:attacker"),
                        query));
    }

    @Test
    void treatsDifferentAuthorityEntriesAsAlternatives() {
        var verifier = verifier(
                (issuer, entities) -> issuer.equals("https://issuer.example")
                        && entities.contains("https://federation.example"),
                (chain, identifiers) -> false);
        var query = query(
                new TrustedAuthority("did", List.of("did:example:other")),
                new TrustedAuthority(
                        "openid_federation", List.of("https://federation.example")));

        assertDoesNotThrow(() -> verifier.verify(
                CredentialType.SdJwt,
                "not-needed",
                Map.of("iss", "https://issuer.example"),
                query));
    }

    @Test
    void delegatesEtsiTrustedListMatchingWithThePresentedChain() throws Exception {
        var ca = TestCertificates.ca("etsi-ca");
        var keyPair = TestCertificates.ecKeyPair();
        var leaf = TestCertificates.leaf(ca, keyPair.getPublic(), "issuer");
        var verifier = verifier(
                (issuer, entities) -> false,
                (chain, identifiers) -> chain.size() == 2
                        && identifiers.equals(List.of("https://tl.example/list.xml")));
        var token = signedJwt(keyPair, leaf.encodedChain(), "https://issuer.example");
        var query = query(new TrustedAuthority(
                "etsi_tl", List.of("https://tl.example/list.xml")));

        assertDoesNotThrow(() -> verifier.verify(
                CredentialType.SdJwt,
                token,
                Map.of("iss", "https://issuer.example"),
                query));
    }

    @Test
    void matchesTheAuthorityKeyIdentifierFromThePresentedChain() throws Exception {
        var ca = TestCertificates.ca("aki-ca");
        var keyPair = TestCertificates.ecKeyPair();
        var leaf = TestCertificates.leaf(ca, keyPair.getPublic(), "issuer");
        var token = signedJwt(keyPair, leaf.encodedChain(), "https://issuer.example");
        var extension = leaf.certificate().getExtensionValue(
                Extension.authorityKeyIdentifier.getId());
        var keyIdentifier = AuthorityKeyIdentifier.getInstance(
                        ASN1OctetString.getInstance(extension).getOctets())
                .getKeyIdentifier();
        var expected = Base64.getUrlEncoder().withoutPadding().encodeToString(keyIdentifier);
        var verifier = verifier((issuer, entities) -> false, (chain, identifiers) -> false);

        assertDoesNotThrow(() -> verifier.verify(
                CredentialType.SdJwt,
                token,
                Map.of("iss", "https://issuer.example"),
                query(new TrustedAuthority("aki", List.of(expected)))));
        assertThrows(
                VpVerificationException.class,
                () -> verifier.verify(
                        CredentialType.SdJwt,
                        token,
                        Map.of("iss", "https://issuer.example"),
                        query(new TrustedAuthority("aki", List.of("different")))));

        var fallback = verifier.x509FallbackVerifier(
                query(new TrustedAuthority("aki", List.of(expected))));
        var chain = List.of(leaf.certificate(), ca.certificate());
        assertTrue(fallback.validCertChain(chain));
        assertFalse(verifier.x509FallbackVerifier(
                        query(new TrustedAuthority("aki", List.of("different"))))
                .validCertChain(chain));
    }

    @Test
    void failsClosedForUnsupportedConfiguredAuthorityTypes() {
        var verifier = verifier((issuer, entities) -> false, (chain, identifiers) -> false);

        assertThrows(
                VpVerificationException.class,
                () -> verifier.verify(
                        CredentialType.SdJwt,
                        "not-needed",
                        Map.of("iss", "did:example:issuer"),
                        query(new TrustedAuthority("unknown", List.of("anything")))));
    }

    private TrustedAuthorityVerifier verifier(
            OpenIdFederationTrustResolver federation,
            EtsiTrustedListVerifier trustedListVerifier) {
        return new TrustedAuthorityVerifier(federation, trustedListVerifier);
    }

    private CredentialQuery query(TrustedAuthority... authorities) {
        return new CredentialQuery(
                "credential", "dc+sd-jwt", false, null, List.of(authorities), true, List.of(), null);
    }

    private String signedJwt(
            java.security.KeyPair keyPair, List<String> encodedChain, String issuer)
            throws Exception {
        var privateJwk = new ECKey.Builder(Curve.P_256, (ECPublicKey) keyPair.getPublic())
                .privateKey((ECPrivateKey) keyPair.getPrivate())
                .build();
        var header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .x509CertChain(encodedChain.stream()
                        .map(com.nimbusds.jose.util.Base64::new)
                        .toList())
                .build();
        var jwt = new SignedJWT(
                header, new JWTClaimsSet.Builder().issuer(issuer).build());
        jwt.sign(new ECDSASigner(privateJwk));
        return jwt.serialize();
    }
}
