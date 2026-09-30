// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.heidiverse.heidi.verifier.sdjwt.util.KapunJwtUtil;
import org.springframework.stereotype.Service;
import uniffi.kapun_trust_rust.Kapun_trust_rust_jvmKt;

import java.util.HashSet;
import java.util.List;

@Service
public class KapunOpenIdFederationTrustResolver implements OpenIdFederationTrustResolver {
    private static final Logger logger =
            LoggerFactory.getLogger(KapunOpenIdFederationTrustResolver.class);

    @Override
    public boolean isTrusted(String issuer, List<String> acceptedFederationEntities) {
        if (issuer == null || issuer.isBlank() || acceptedFederationEntities.isEmpty()) {
            return false;
        }
        try {
            // Kapun fetches, constructs, and cryptographically validates the federation chain.
            var trustChain = Kapun_trust_rust_jvmKt.oidcfTrustChainFromUrl(issuer, false);
            var entitiesInPath = new HashSet<String>();
            entitiesInPath.add(issuer);
            for (var statementValue : trustChain.getSubordinateStatements()) {
                // Kapun has already validated the complete federation chain before exposing it.
                var statement = KapunJwtUtil.parse(statementValue);
                var claims = KapunJwtUtil.payload(statement);
                if (claims.get("iss") instanceof String statementIssuer) {
                    entitiesInPath.add(statementIssuer);
                }
                if (claims.get("sub") instanceof String subject) entitiesInPath.add(subject);
            }
            return acceptedFederationEntities.stream().anyMatch(entitiesInPath::contains);
        } catch (Exception exception) {
            logger.warn("Could not validate OpenID Federation trust for issuer {}", issuer, exception);
            return false;
        }
    }
}
