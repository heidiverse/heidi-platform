// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.model;

import org.heidiverse.heidi.verifier.sdjwt.model.exception.InvalidCertChainException;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.*;
import java.util.List;
import java.util.Set;

public class DefaultX509CertVerifier implements X509CertVerifier {

    private java.time.Instant validationInstant;

    private static final Logger logger = LoggerFactory.getLogger(DefaultX509CertVerifier.class);

    protected final List<X509Certificate> trustedRootCAs;

    public DefaultX509CertVerifier(List<X509Certificate> trustedRootCAs) {
        this.trustedRootCAs = trustedRootCAs;

        if (trustedRootCAs.isEmpty()) {
            logger.warn("No trusted root CAs provided.");
        } else {
            logger.info("Loaded {} trusted root CAs:", trustedRootCAs.size());
            trustedRootCAs.forEach(
                    rootCA ->
                            logger.info(
                                    " - Trusted Root CA: {}", rootCA.getSubjectX500Principal()));
        }
    }

    /** Finds a matching root certificate based on X500 principal name - or null if none present */
    protected X509Certificate findRootCA(final X509Certificate x509Certificate) {
        String issuerPrincipal = x509Certificate.getIssuerX500Principal().getName();
        logger.debug("Searching for root CA matching issuer: {}", issuerPrincipal);

        X509Certificate matchingRoot =
                trustedRootCAs.stream()
                        .filter(
                                rootCert ->
                                        rootCert.getSubjectX500Principal()
                                                .getName("RFC2253")
                                                .equals(
                                                        x509Certificate
                                                                .getIssuerX500Principal()
                                                                .getName("RFC2253")))
                        .findFirst()
                        .orElse(null);

        if (matchingRoot != null) {
            logger.info(
                    "Found matching trusted root CA: {}", matchingRoot.getSubjectX500Principal());
        } else {
            logger.warn("No matching trusted root CA found for issuer: {}", issuerPrincipal);
        }

        return matchingRoot;
    }

    @Override
    public void setValidationDate(java.time.Instant instant) {
        this.validationInstant = instant;
    }

    @Override
    public boolean validCertChain(@NotNull final List<X509Certificate> x509Chain) {
        boolean result = true;
        if (x509Chain.isEmpty()) {
            logger.debug("Certificate chain is empty.");
            result = false;
        } else {
            try {
                final CertPath certPath =
                        CertificateFactory.getInstance("X509").generateCertPath(x509Chain);
                final var certPathValidator = CertPathValidator.getInstance("PKIX");

                final var rootCA = findRootCA(x509Chain.getLast());
                if (rootCA == null) {
                    throw new InvalidCertChainException(
                            "No matching trusted root CA found for issuer: "
                                    + x509Chain.getLast().getIssuerX500Principal());
                }

                PKIXParameters pkixParameters =
                        new PKIXParameters(Set.of(new TrustAnchor(rootCA, null)));
                pkixParameters.setRevocationEnabled(false);
                if (validationInstant != null) {
                    pkixParameters.setDate(java.util.Date.from(validationInstant));
                    logger.info("Custom Date for Cert validation set: {}", java.util.Date.from(validationInstant));
                }

                logger.debug("Validating certificate chain...");
                certPathValidator.validate(certPath, pkixParameters);
                logger.info("Certificate chain successfully validated.");
            } catch (CertificateException
                    | NoSuchAlgorithmException
                    | InvalidAlgorithmParameterException e) {
                logger.error("Certificate exception occurred: {}", e.getMessage(), e);
                throw new InvalidCertChainException("Invalid x509 cert chain");
            } catch (CertPathValidatorException e) {
                // failed to validate
                logger.warn("Certificate path validation failed: {}", e.getMessage(), e);
                result = false;
            }
        }
        return result;
    }
}
