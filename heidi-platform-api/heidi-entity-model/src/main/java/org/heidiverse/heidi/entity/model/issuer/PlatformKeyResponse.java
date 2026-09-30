// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;

/** Public metadata for one neutral platform key and its immutable versions. */
public record PlatformKeyResponse(
        UUID id,
        String keyId,
        Integer providerId,
        UUID activeVersionId,
        List<Version> versions,
        Instant createdAt,
        KeyRotationPolicy rotationPolicy) {
    public PlatformKeyResponse {
        versions = versions == null ? List.of() : List.copyOf(versions);
    }

    public record Version(
            UUID id,
            int version,
            String algorithm,
            String publicJwk,
            String status,
            List<Certificate> certificates,
            Instant createdAt,
            Set<SigningKeyUsage> usages,
            Instant previousUntil) {
        public Version {
            certificates = certificates == null ? List.of() : List.copyOf(certificates);
        }
    }

    public record Certificate(
            UUID id, SigningCertificateProfile profile, SigningCertificateSource source,
            IssuerTrustSystem trustSystem, List<String> chain, Instant notBefore, Instant notAfter,
            Instant firstUsedAt, Instant retiredAt, String eudiLeafProfile) {}
}
