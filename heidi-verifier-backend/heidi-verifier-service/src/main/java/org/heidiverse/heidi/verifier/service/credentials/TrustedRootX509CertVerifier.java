// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import org.heidiverse.heidi.verifier.sdjwt.model.DefaultX509CertVerifier;
import org.heidiverse.heidi.verifier.sdjwt.model.exception.InvalidCertChainException;

import org.jetbrains.annotations.NotNull;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;

import uniffi.kapun_crypto_rust.Kapun_crypto_rust_jvmKt;

/**
 * Validates SD-JWT and mdoc issuer certificate chains against the configured trust anchors.
 *
 * <p>Not a Spring bean: the anchors are per issuer and arrive with the verification request, so
 * {@link RequestTrustVerifierFactory} constructs one instance per request rather than a singleton
 * wired from configuration.
 *
 * <p>A trust anchor is any certificate the operator configured - a self-signed root or an
 * intermediate CA such as an ecosystem's issuing CA. Trust comes from that configuration, not from
 * a self-signature (RFC 5280, section 6.1.1), so the anchor need not be self-signed. Chains are
 * often presented without their anchor (HAIP excludes it from {@code x5c}).
 */
public class TrustedRootX509CertVerifier extends DefaultX509CertVerifier {

    public TrustedRootX509CertVerifier(final List<X509Certificate> trustedRootCAs) {
        super(trustedRootCAs);
    }

    @Override
    public boolean validCertChain(@NotNull final List<X509Certificate> x509Chain) {
        if (x509Chain.isEmpty()) {
            return false;
        }
        requireCandidateAnchor(x509Chain.getLast());
        // An empty anchor list never reaches this point (no candidate), and Kapun rejects every
        // chain for an empty list anyway - it never falls back to self-signed anchoring.
        return Kapun_crypto_rust_jvmKt.verifyChainWithTrustAnchors(
                toKapunChain(x509Chain), toKapunChain(trustedRootCAs));
    }

    /**
     * Fails fast with a descriptive error when no configured anchor can possibly terminate the
     * chain: the last presented certificate is neither an anchor itself nor named as issued by
     * one. Kapun repeats the name match and additionally verifies the signature.
     *
     * @throws InvalidCertChainException if no configured anchor matches the chain's issuer
     */
    private void requireCandidateAnchor(final X509Certificate last) {
        if (trustedRootCAs.contains(last) || findRootCA(last) != null) {
            return;
        }
        throw new InvalidCertChainException(
                "No matching trusted root CA found for issuer: " + last.getIssuerX500Principal());
    }

    private static List<uniffi.kapun_crypto_rust.X509Certificate> toKapunChain(
            final List<X509Certificate> chain) {
        return chain.stream().map(TrustedRootX509CertVerifier::toKapunCert).toList();
    }

    private static uniffi.kapun_crypto_rust.X509Certificate toKapunCert(
            final X509Certificate cert) {
        try {
            return Kapun_crypto_rust_jvmKt.extractCerts(cert.getEncoded()).getFirst();
        } catch (CertificateEncodingException e) {
            throw new InvalidCertChainException("Could not encode certificate: " + e.getMessage());
        }
    }
}
