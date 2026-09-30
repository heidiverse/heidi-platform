// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Runtime configuration returned only by the service-to-service issuer endpoint. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IssuerSigningConfigurationInternal(
        IssuerTrustSystem trustSystem,
        String issuerClaim,
        String keyId,
        String keyUri,
        String algorithm,
        String providerEndpoint,
        String providerAuthenticationMode,
        String issuerJwk,
        List<String> certificateChain,
        List<String> previousIssuerJwks,
        List<String> supportedAlgorithms,
        List<String> supportedOperations,
        String profileId) {

    public IssuerSigningConfigurationInternal(
            IssuerTrustSystem trustSystem,
            String issuerClaim,
            String keyId,
            String keyUri,
            String algorithm,
            String providerEndpoint,
            String providerAuthenticationMode,
            String issuerJwk,
            List<String> certificateChain,
            List<String> previousIssuerJwks,
            List<String> supportedAlgorithms,
            List<String> supportedOperations) {
        this(trustSystem, issuerClaim, keyId, keyUri, algorithm, providerEndpoint,
                providerAuthenticationMode, issuerJwk, certificateChain, previousIssuerJwks,
                supportedAlgorithms, supportedOperations, null);
    }
}
