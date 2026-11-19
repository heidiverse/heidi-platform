// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import static org.junit.jupiter.api.Assertions.*;

import org.heidiverse.heidi.verifier.sdjwt.model.exception.InvalidCertChainException;
import org.heidiverse.heidi.shared.testing.TestCertificates;

import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

class TrustedRootX509CertVerifierTest {

    private static final String ROOT_CN = "heidi-test-root";
    private static final String DOMAIN = "verifier.example.org";

    private static List<X509Certificate> chainUnder(final TestCertificates.Ca ca) throws Exception {
        final var leaf =
                TestCertificates.leaf(
                        ca, TestCertificates.ecKeyPair().getPublic(), DOMAIN, List.of(DOMAIN));
        return List.of(leaf.certificate(), ca.certificate());
    }

    @Test
    void acceptsAChainIssuedByATrustedRoot() throws Exception {
        final var ca = TestCertificates.ca(ROOT_CN);
        final var verifier = new TrustedRootX509CertVerifier(List.of(ca.certificate()));

        assertTrue(verifier.validCertChain(chainUnder(ca)));
    }

    @Test
    void acceptsAChainThatOmitsItsRoot() throws Exception {
        final var ca = TestCertificates.ca(ROOT_CN);
        final var verifier = new TrustedRootX509CertVerifier(List.of(ca.certificate()));

        // only the leaf is presented; the verifier has to supply the anchor itself
        final var leafOnly = List.of(chainUnder(ca).getFirst());

        assertTrue(verifier.validCertChain(leafOnly));
    }

    @Test
    void rejectsAnEmptyChain() throws Exception {
        final var ca = TestCertificates.ca(ROOT_CN);
        final var verifier = new TrustedRootX509CertVerifier(List.of(ca.certificate()));

        assertFalse(verifier.validCertChain(List.of()));
    }

    @Test
    void throwsWhenNoConfiguredRootMatchesTheIssuer() throws Exception {
        final var trusted = TestCertificates.ca(ROOT_CN);
        final var stranger = TestCertificates.ca("some-other-root");
        final var verifier = new TrustedRootX509CertVerifier(List.of(trusted.certificate()));

        assertThrows(
                InvalidCertChainException.class,
                () -> verifier.validCertChain(chainUnder(stranger)));
    }

    @Test
    void rejectsAnExpiredChain() throws Exception {
        final var now = Instant.now();
        final var expired =
                TestCertificates.ca(
                        ROOT_CN, now.minus(800, ChronoUnit.DAYS), now.minus(400, ChronoUnit.DAYS));
        final var verifier = new TrustedRootX509CertVerifier(List.of(expired.certificate()));

        assertFalse(verifier.validCertChain(chainUnder(expired)));
    }

    /**
     * The subject and issuer DNs of a presented certificate are attacker-controlled, so matching
     * them against a trusted root's subject DN proves nothing on its own. Here the presented chain
     * is complete and internally consistent, and terminates in a self-signed CA carrying a trusted
     * root's DN — but signed by a key we do not trust.
     *
     * <p>This is the regression guard for the anchoring step in {@link
     * TrustedRootX509CertVerifier#validCertChain(List)}. Without it the chain is verified against
     * its own last certificate and this forgery is accepted.
     */
    @Test
    void rejectsAChainForgingATrustedRootsNameWithAnUntrustedKey() throws Exception {
        final var trusted = TestCertificates.ca(ROOT_CN);
        final var impostor = TestCertificates.ca(ROOT_CN); // same DN, different key
        final var verifier = new TrustedRootX509CertVerifier(List.of(trusted.certificate()));

        final var forgedChain = chainUnder(impostor);

        assertEquals(
                trusted.certificate().getSubjectX500Principal(),
                forgedChain.getLast().getSubjectX500Principal(),
                "precondition: the forged root must carry the trusted root's DN");
        assertNotEquals(
                trusted.certificate(),
                forgedChain.getLast(),
                "precondition: the forged root must not be the trusted root");
        assertFalse(verifier.validCertChain(forgedChain));
    }

    /**
     * Ecosystem trusted lists (e.g. the German PID-provider list) publish the issuing CA, which is
     * itself issued by an offline root, and HAIP leaves the anchor out of {@code x5c}. The
     * configured anchor therefore need not be self-signed.
     */
    @Test
    void acceptsALeafIssuedByATrustedIntermediateAnchor() throws Exception {
        final var root = TestCertificates.ca(ROOT_CN);
        final var issuingCa = TestCertificates.intermediate(root, "heidi-test-issuing-ca");
        final var verifier = new TrustedRootX509CertVerifier(List.of(issuingCa.certificate()));

        assertTrue(verifier.validCertChain(List.of(chainUnder(issuingCa).getFirst())));
    }

    @Test
    void acceptsAChainThatEndsInATrustedIntermediateAnchor() throws Exception {
        final var root = TestCertificates.ca(ROOT_CN);
        final var issuingCa = TestCertificates.intermediate(root, "heidi-test-issuing-ca");
        final var verifier = new TrustedRootX509CertVerifier(List.of(issuingCa.certificate()));

        assertTrue(verifier.validCertChain(chainUnder(issuingCa)));
    }

    @Test
    void acceptsAChainThroughAPresentedIntermediateUpToATrustedRoot() throws Exception {
        final var root = TestCertificates.ca(ROOT_CN);
        final var issuingCa = TestCertificates.intermediate(root, "heidi-test-issuing-ca");
        final var verifier = new TrustedRootX509CertVerifier(List.of(root.certificate()));

        final var leaf = chainUnder(issuingCa).getFirst();

        assertTrue(verifier.validCertChain(List.of(leaf, issuingCa.certificate())));
    }

    @Test
    void rejectsALeafForgingATrustedIntermediateAnchorsName() throws Exception {
        final var root = TestCertificates.ca(ROOT_CN);
        final var trusted = TestCertificates.intermediate(root, "heidi-test-issuing-ca");
        final var impostor = TestCertificates.ca("heidi-test-issuing-ca"); // same DN, other key
        final var verifier = new TrustedRootX509CertVerifier(List.of(trusted.certificate()));

        assertFalse(verifier.validCertChain(List.of(chainUnder(impostor).getFirst())));
    }

    @Test
    void throwsWhenNoAnchorsAreConfigured() throws Exception {
        final var ca = TestCertificates.ca(ROOT_CN);
        final var verifier = new TrustedRootX509CertVerifier(List.of());

        assertThrows(
                InvalidCertChainException.class,
                () -> verifier.validCertChain(chainUnder(ca)));
    }

    @Test
    void picksTheMatchingRootOutOfSeveralConfiguredRoots() throws Exception {
        final var other = TestCertificates.ca("unrelated-root");
        final var ca = TestCertificates.ca(ROOT_CN);
        final var verifier =
                new TrustedRootX509CertVerifier(
                        List.of(other.certificate(), ca.certificate()));

        assertTrue(verifier.validCertChain(chainUnder(ca)));
    }
}
