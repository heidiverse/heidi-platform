// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import com.nimbusds.jose.crypto.ECDSASigner;

import org.heidiverse.heidi.shared.testing.TestCertificates;
import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.heidiverse.heidi.verifier.sdjwt.IssuerWebKeyManager;
import org.heidiverse.heidi.verifier.sdjwt.util.DidUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

class StatusListVerifierTest {
    private static final URI URI_VALUE = URI.create("https://status.example/list");

    /**
     * The German PID layout: one root with sibling CAs for credential signing and status list
     * signing, and neither CA in any x5c. The status list must chain to a configured anchor rather
     * than to the credential's own certificate.
     */
    @Test
    void acceptsStatusListSignedUnderASiblingCaThatIsConfigured() throws Exception {
        var pki = SiblingPki.create();
        var fixture = x509Fixture(pki, pki.statusListCa(), true);

        new StatusListVerifier(uri -> fixture.statusList()).verify(
                fixture.presentation(),
                claims(),
                new TrustedRootX509CertVerifier(
                        List.of(pki.credentialCa().certificate(), pki.statusListCa().certificate())));
    }

    /** The case that failed before: the credential's own certificate was used as the anchor. */
    @Test
    void rejectsSiblingCaStatusListWithoutTrustAnchors() throws Exception {
        var pki = SiblingPki.create();
        var fixture = x509Fixture(pki, pki.statusListCa(), true);

        assertThatThrownBy(() -> new StatusListVerifier(uri -> fixture.statusList()).verify(
                        fixture.presentation(), claims()))
                .isInstanceOf(VpVerificationException.class)
                .hasMessageStartingWith("Could not validate credential status");
    }

    @Test
    void rejectsStatusListWhoseCaIsNotConfigured() throws Exception {
        var pki = SiblingPki.create();
        var fixture = x509Fixture(pki, pki.statusListCa(), true);

        assertThatThrownBy(() -> new StatusListVerifier(uri -> fixture.statusList()).verify(
                        fixture.presentation(),
                        claims(),
                        new TrustedRootX509CertVerifier(List.of(pki.credentialCa().certificate()))))
                .isInstanceOf(VpVerificationException.class);
    }

    /** Same key as the credential (draft-ietf-oauth-status-list, section 11.3): no own x5c. */
    @Test
    void acceptsStatusListSignedWithTheCredentialKeyUnderTrustAnchors() throws Exception {
        var pki = SiblingPki.create();
        var fixture = x509Fixture(pki, pki.credentialCa(), false);

        new StatusListVerifier(uri -> fixture.statusList()).verify(
                fixture.presentation(),
                claims(),
                new TrustedRootX509CertVerifier(List.of(pki.credentialCa().certificate())));
    }

    @Test
    void rejectsStatusListWithoutX5cAndOtherKeyIdUnderTrustAnchors() throws Exception {
        var pki = SiblingPki.create();
        var fixture = x509Fixture(pki, pki.statusListCa(), false);

        assertThatThrownBy(() -> new StatusListVerifier(uri -> fixture.statusList()).verify(
                        fixture.presentation(),
                        claims(),
                        new TrustedRootX509CertVerifier(
                                List.of(
                                        pki.credentialCa().certificate(),
                                        pki.statusListCa().certificate()))))
                .isInstanceOf(VpVerificationException.class)
                .hasMessage(
                        "Status list token must carry an x5c chain anchored in the configured"
                                + " identity trust anchors");
    }

    private record SiblingPki(
            TestCertificates.Ca credentialCa,
            TestCertificates.Ca statusListCa,
            KeyPair credentialKey,
            X509Certificate credentialCertificate) {

        static SiblingPki create() throws Exception {
            var root = TestCertificates.ca("pid-test-root");
            var credentialCa = TestCertificates.intermediate(root, "pid-signer-ca");
            var statusListCa = TestCertificates.intermediate(root, "pid-status-list-signer-ca");
            var credentialKey = TestCertificates.ecKeyPair();
            var credentialCertificate = TestCertificates.leaf(
                            credentialCa, credentialKey.getPublic(), "pid-signer")
                    .certificate();
            return new SiblingPki(credentialCa, statusListCa, credentialKey, credentialCertificate);
        }
    }

    /**
     * A credential with an {@code x5c} under the credential CA and a status list token. With
     * {@code statusListCa} equal to the credential CA and {@code tokenX5c} false, the token reuses
     * the credential key and kid; otherwise it uses its own key, certificate and kid.
     */
    private Fixture x509Fixture(SiblingPki pki, TestCertificates.Ca statusListCa, boolean tokenX5c)
            throws Exception {
        var sameKey = statusListCa == pki.credentialCa() && !tokenX5c;
        var tokenKey = sameKey ? pki.credentialKey() : TestCertificates.ecKeyPair();
        List<X509Certificate> tokenX5cChain = tokenX5c
                ? List.of(TestCertificates.leaf(
                                statusListCa, tokenKey.getPublic(), "pid-status-list-signer")
                        .certificate())
                : List.of();
        return x509Fixture(
                List.of(pki.credentialCertificate()),
                pki.credentialKey(),
                tokenX5cChain,
                tokenKey,
                sameKey ? "credential-key" : "status-key");
    }

    /** A credential (kid {@code credential-key}) and a status list token with the given x5c headers. */
    private Fixture x509Fixture(
            List<X509Certificate> credentialX5c,
            KeyPair credentialKey,
            List<X509Certificate> tokenX5c,
            KeyPair tokenKey,
            String tokenKeyId)
            throws Exception {
        var credential = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .keyID("credential-key")
                        .x509CertChain(encoded(credentialX5c))
                        .build(),
                new JWTClaimsSet.Builder().issuer("https://issuer.example").build());
        credential.sign(new ECDSASigner((ECPrivateKey) credentialKey.getPrivate()));

        var tokenHeader = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType("statuslist+jwt"))
                .keyID(tokenKeyId);
        if (!tokenX5c.isEmpty()) {
            tokenHeader.x509CertChain(encoded(tokenX5c));
        }
        var now = Instant.now();
        var statusList = new SignedJWT(
                tokenHeader.build(),
                new JWTClaimsSet.Builder()
                        .subject(URI_VALUE.toString())
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(60)))
                        .claim("status_list", Map.of("bits", 1, "lst", encode((byte) 0)))
                        .build());
        statusList.sign(new ECDSASigner((ECPrivateKey) tokenKey.getPrivate()));
        return new Fixture(credential.serialize() + "~", statusList.serialize(), null);
    }

    private static List<com.nimbusds.jose.util.Base64> encoded(List<X509Certificate> chain)
            throws Exception {
        var result = new java.util.ArrayList<com.nimbusds.jose.util.Base64>();
        for (var certificate : chain) {
            result.add(com.nimbusds.jose.util.Base64.encode(certificate.getEncoded()));
        }
        return result;
    }

    /**
     * France-Identité-style chain: the credential x5c carries its issuing CA and only the root is
     * configured. A status list signed under that same issuing CA is accepted (case 2).
     */
    @Test
    void acceptsStatusListUnderTheIssuingCaFromTheCredentialX5c() throws Exception {
        var root = TestCertificates.ca("issuer-root");
        var issuingCa = TestCertificates.intermediate(root, "issuer-issuing-ca");
        var credentialKey = TestCertificates.ecKeyPair();
        var credentialLeaf = TestCertificates.leaf(issuingCa, credentialKey.getPublic(), "signer")
                .certificate();
        var tokenKey = TestCertificates.ecKeyPair();
        var tokenLeaf = TestCertificates.leaf(issuingCa, tokenKey.getPublic(), "status-signer")
                .certificate();
        var fixture = x509Fixture(
                List.of(credentialLeaf, issuingCa.certificate()),
                credentialKey,
                List.of(tokenLeaf),
                tokenKey,
                "status-key");

        new StatusListVerifier(uri -> fixture.statusList()).verify(
                fixture.presentation(),
                claims(),
                new TrustedRootX509CertVerifier(List.of(root.certificate())));
    }

    /** Case 2 never treats the credential's signing certificate as a CA. */
    @Test
    void rejectsStatusSignerIssuedByTheCredentialSigningCertificate() throws Exception {
        var pki = SiblingPki.create();
        var credentialSignerAsCa = new TestCertificates.Ca(
                "pid-signer", pki.credentialKey(), pki.credentialCertificate());
        var tokenKey = TestCertificates.ecKeyPair();
        var tokenLeaf = TestCertificates.leaf(
                        credentialSignerAsCa, tokenKey.getPublic(), "status-signer")
                .certificate();
        var fixture = x509Fixture(
                List.of(pki.credentialCertificate()),
                pki.credentialKey(),
                List.of(tokenLeaf),
                tokenKey,
                "status-key");

        assertThatThrownBy(() -> new StatusListVerifier(uri -> fixture.statusList()).verify(
                        fixture.presentation(),
                        claims(),
                        new TrustedRootX509CertVerifier(List.of(pki.credentialCa().certificate()))))
                .isInstanceOf(VpVerificationException.class)
                .hasMessage(
                        "Status list token certificate chain is not anchored in the configured"
                                + " identity trust anchors");
    }

    /** Case 1: the token carries the credential's own certificate and is signed with its key. */
    @Test
    void acceptsStatusListCarryingTheCredentialCertificate() throws Exception {
        var pki = SiblingPki.create();
        var fixture = x509Fixture(
                List.of(pki.credentialCertificate()),
                pki.credentialKey(),
                List.of(pki.credentialCertificate()),
                pki.credentialKey(),
                "another-kid");

        new StatusListVerifier(uri -> fixture.statusList()).verify(
                fixture.presentation(),
                claims(),
                new TrustedRootX509CertVerifier(List.of(pki.credentialCa().certificate())));
    }

    /**
     * Credential verification may resolve a pinned issuer key without checking x5c, so the
     * same-key case must not trust a credential chain that is not anchored itself.
     */
    @Test
    void rejectsSameKeyStatusListWhenTheCredentialChainIsNotAnchored() throws Exception {
        var pki = SiblingPki.create();
        var fixture = x509Fixture(pki, pki.credentialCa(), false);

        assertThatThrownBy(() -> new StatusListVerifier(uri -> fixture.statusList()).verify(
                        fixture.presentation(),
                        claims(),
                        new TrustedRootX509CertVerifier(List.of(pki.statusListCa().certificate()))))
                .isInstanceOf(VpVerificationException.class)
                .hasMessage(
                        "Status list token must carry an x5c chain anchored in the configured"
                                + " identity trust anchors");
    }

    @Test
    void acceptsAValidEntryFromAnExternalList() throws Exception {
        var fixture = fixture(0);

        new StatusListVerifier(uri -> fixture.statusList()).verify(
                fixture.presentation(), claims());
    }

    @Test
    void rejectsAnInvalidEntryFromAnExternalList() throws Exception {
        var fixture = fixture(1);

        assertThatThrownBy(() -> new StatusListVerifier(uri -> fixture.statusList()).verify(
                        fixture.presentation(), claims()))
                .isInstanceOf(VpVerificationException.class)
                .hasMessage("Credential status is 1");
    }

    @Test
    void resolvesRelativeStatusKeyAgainstSwissIssuerDid() throws Exception {
        var fixture = fixture(0, "status-key", "credential-key", "did:webvh:scid:issuer.example");

        try (MockedStatic<DidUtil> didUtil = mockStatic(DidUtil.class, CALLS_REAL_METHODS)) {
            didUtil.when(() -> DidUtil.resolveJwkFromDidKey(
                            "did:webvh:scid:issuer.example#status-key"))
                    .thenReturn(fixture.publicKey());

            new StatusListVerifier(uri -> fixture.statusList()).verify(
                    fixture.presentation(), claims());
        }
    }

    @Test
    void fallsBackToIssuerJwksWhenDidResolutionFails() throws Exception {
        var issuer = "did:webvh:scid:issuer.example";
        var fixture = fixture(0, "status-key", "credential-key", issuer);

        try (MockedStatic<DidUtil> didUtil = mockStatic(DidUtil.class, CALLS_REAL_METHODS);
                MockedStatic<IssuerWebKeyManager> webKeys = mockStatic(IssuerWebKeyManager.class)) {
            didUtil.when(() -> DidUtil.resolveJwkFromDidKey(issuer + "#status-key"))
                    .thenThrow(new IOException("key is not in the DID document"));
            webKeys.when(() -> IssuerWebKeyManager.getIssuerKey(
                            JWSAlgorithm.RS256, issuer, "status-key"))
                    .thenReturn(fixture.publicKey());

            new StatusListVerifier(uri -> fixture.statusList()).verify(
                    fixture.presentation(), claims());

            webKeys.verify(() -> IssuerWebKeyManager.getIssuerKey(
                    JWSAlgorithm.RS256, issuer, "status-key"));
        }
    }

    @Test
    void rejectsATamperedListSignature() throws Exception {
        var fixture = fixture(0);
        var tampered = tamper(fixture.statusList());

        assertThatThrownBy(() -> new StatusListVerifier(uri -> tampered).verify(
                        fixture.presentation(), claims()))
                .isInstanceOf(VpVerificationException.class)
                .hasMessage("Status list token signature is invalid");
    }

    private String tamper(String jwt) {
        var parts = jwt.split("\\.");
        var signature = parts[2].toCharArray();
        var index = signature.length / 2;
        signature[index] = signature[index] == 'A' ? 'B' : 'A';
        return parts[0] + "." + parts[1] + "." + new String(signature);
    }

    private Fixture fixture(int status) throws Exception {
        return fixture(status, "status-key", "status-key", "https://issuer.example");
    }

    private Fixture fixture(int status, String statusKeyId, String credentialKeyId, String issuer)
            throws Exception {
        var key = new RSAKeyGenerator(2048).keyID(statusKeyId).generate();
        var publicKey = key.toPublicJWK();
        var credential = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(credentialKeyId)
                        .jwk(publicKey)
                        .build(),
                new JWTClaimsSet.Builder().issuer(issuer).build());
        credential.sign(new RSASSASigner(key));

        var now = Instant.now();
        var statusList = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(new JOSEObjectType("statuslist+jwt"))
                        .keyID(key.getKeyID())
                        .build(),
                new JWTClaimsSet.Builder()
                        .subject(URI_VALUE.toString())
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(60)))
                        .claim("status_list", Map.of("bits", 1, "lst", encode((byte) status)))
                        .build());
        statusList.sign(new RSASSASigner(key));
        return new Fixture(credential.serialize() + "~", statusList.serialize(), publicKey);
    }

    private Map<String, Object> claims() {
        return Map.of("status", Map.of(
                "status_list", Map.of("idx", 0, "uri", URI_VALUE.toString())));
    }

    private String encode(byte value) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var compressed = new DeflaterOutputStream(output)) {
            compressed.write(new byte[] {value});
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
    }

    private record Fixture(
            String presentation, String statusList, JWK publicKey) {}
}
