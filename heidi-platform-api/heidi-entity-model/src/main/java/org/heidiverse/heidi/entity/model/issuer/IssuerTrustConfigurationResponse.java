// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The trust material configured for one issuer under one trust system.
 *
 * <p>The certificate chain here answers one question:
 *
 * <ul>
 *   <li>{@code issuerCertificateAvailable} / {@code eudiIssuerCertificateChains} - certificate
 *       chains used only to sign metadata and other trust-framework artifacts.
 * </ul>
 *
 * <p>The EUDI identity-statement slot uses the issuer's access certificate. The distinct
 * presentation-signing slot uses the verifier's access certificate.
 */
public record IssuerTrustConfigurationResponse(
        Integer issuerId,
        IssuerTrustSystem trustSystem,
        boolean issuerCertificateAvailable,
        List<IssuerCertificateChain> eudiIssuerCertificateChains,
        String issuerClaim,
        String swissDid,
        String swissRegistryBaseUrl,
        String swissStatusRegistryApiUrl,
        String swissStatusRegistryPartnerId,
        String swissTrustRegistryAuthoringUrl,
        String swissTrustRegistryTokenUrl,
        String swissTrustRegistryClientId,
        boolean swissTrustRegistryCredentialsConfigured,
        String swissIdentityStatement,
        Instant swissIdentityStatementExpiresAt,
        Map<String, String> swissIssuanceStatements,
        Instant swissIssuanceStatementsExpiresAt,
        List<String> eudiVerificationTrustAnchors,
        String swissVerificationTrustAnchor,
        boolean eudiTrustOwnPki) {

    public record IssuerCertificateChain(
            UUID keyId,
            String logicalKeyId,
            String algorithm,
            List<String> certificateChain) {}
}
