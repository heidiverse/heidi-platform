// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import org.heidiverse.heidi.verifier.sdjwt.model.DefaultX509CertVerifier;
import org.heidiverse.heidi.verifier.sdjwt.model.exception.InvalidCertChainException;

import org.jetbrains.annotations.NotNull;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.stream.Stream;

import uniffi.kapun_crypto_rust.Kapun_crypto_rust_jvmKt;

/**
 * Validates SD-JWT issuer certificate chains against the configured trusted root CAs.
 *
 * <p>Not a Spring bean: the anchors are per issuer and arrive with the verification request, so
 * {@link RequestTrustVerifierFactory} constructs one instance per request rather than a singleton
 * wired from configuration.
 *
 * <p>The cryptographic work is delegated to the Kapun SDK (the {@code heidi_x509} Rust crate) via
 * {@link Kapun_crypto_rust_jvmKt#verifyChain(List)}. That call takes no trust anchors of its own and
 * treats the last certificate of the chain as the anchor, so anchor selection stays here - see {@link
 * #anchoredChain(List)}.
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
        return Kapun_crypto_rust_jvmKt.verifyChain(toKapunChain(anchoredChain(x509Chain)));
    }

    /**
     * Resolves the trusted root for this chain and guarantees the chain terminates in it.
     *
     * <p>{@link #findRootCA(X509Certificate)} matches on the issuer's X500 principal name, which the
     * presenter of the chain controls. Appending the resolved root forces the chain verification to
     * check a signature made by a root we actually hold, rather than anchoring on whatever
     * certificate the presenter put last. The append is a no-op for chains that already include
     * their root.
     *
     * @throws InvalidCertChainException if no configured root CA matches the chain's issuer
     */
    private List<X509Certificate> anchoredChain(final List<X509Certificate> chain) {
        final var rootCA = findRootCA(chain.getLast());
        if (rootCA == null) {
            throw new InvalidCertChainException(
                    "No matching trusted root CA found for issuer: "
                            + chain.getLast().getIssuerX500Principal());
        }
        return chain.getLast().equals(rootCA)
                ? chain
                : Stream.concat(chain.stream(), Stream.of(rootCA)).toList();
    }

    private List<uniffi.kapun_crypto_rust.X509Certificate> toKapunChain(
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
