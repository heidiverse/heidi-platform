// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import java.util.List;
import java.util.Map;

public record IssuerTrustConfigurationRequest(
        String issuerClaim,
        String swissDid,
        String swissRegistryBaseUrl,
        String swissStatusRegistryApiUrl,
        String swissStatusRegistryPartnerId,
        String swissTrustRegistryAuthoringUrl,
        String swissTrustRegistryTokenUrl,
        String swissTrustRegistryClientId,
        String swissTrustRegistryClientSecret,
        String swissTrustRegistryRefreshToken,
        String swissIdentityStatement,
        Map<String, String> swissIssuanceStatements,
        List<String> eudiVerificationTrustAnchors,
        String swissVerificationTrustAnchor,
        /** Also trust the organisation's Issuing PKI; null keeps the current value. */
        Boolean eudiTrustOwnPki) {
    public IssuerTrustConfigurationRequest(
            String issuerClaim,
            String swissDid,
            String swissRegistryBaseUrl,
            String swissStatusRegistryApiUrl,
            String swissStatusRegistryPartnerId,
            String swissTrustRegistryAuthoringUrl,
            String swissTrustRegistryTokenUrl,
            String swissTrustRegistryClientId,
            String swissTrustRegistryClientSecret,
            String swissTrustRegistryRefreshToken,
            String swissIdentityStatement,
            Map<String, String> swissIssuanceStatements,
            List<String> eudiVerificationTrustAnchors,
            String swissVerificationTrustAnchor) {
        this(issuerClaim, swissDid, swissRegistryBaseUrl, swissStatusRegistryApiUrl,
                swissStatusRegistryPartnerId, swissTrustRegistryAuthoringUrl,
                swissTrustRegistryTokenUrl, swissTrustRegistryClientId,
                swissTrustRegistryClientSecret, swissTrustRegistryRefreshToken,
                swissIdentityStatement, swissIssuanceStatements, eudiVerificationTrustAnchors,
                swissVerificationTrustAnchor, null);
    }

    public IssuerTrustConfigurationRequest(
            String issuerClaim,
            String swissDid,
            String swissRegistryBaseUrl,
            String swissTrustRegistryAuthoringUrl,
            String swissTrustRegistryTokenUrl,
            String swissTrustRegistryClientId,
            String swissTrustRegistryClientSecret,
            String swissTrustRegistryRefreshToken,
            String swissIdentityStatement,
            Map<String, String> swissIssuanceStatements,
            List<String> eudiVerificationTrustAnchors,
            String swissVerificationTrustAnchor) {
        this(issuerClaim, swissDid, swissRegistryBaseUrl, null, null,
                swissTrustRegistryAuthoringUrl, swissTrustRegistryTokenUrl,
                swissTrustRegistryClientId, swissTrustRegistryClientSecret,
                swissTrustRegistryRefreshToken, swissIdentityStatement,
                swissIssuanceStatements, eudiVerificationTrustAnchors,
                swissVerificationTrustAnchor);
    }

    public IssuerTrustConfigurationRequest(
            String issuerClaim,
            String swissDid,
            String swissRegistryBaseUrl,
            String swissIdentityStatement,
            Map<String, String> swissIssuanceStatements,
            List<String> eudiVerificationTrustAnchors,
            String swissVerificationTrustAnchor) {
        this(issuerClaim, swissDid, swissRegistryBaseUrl, null, null, null, null, null, null, null,
                swissIdentityStatement, swissIssuanceStatements, eudiVerificationTrustAnchors,
                swissVerificationTrustAnchor);
    }
}
