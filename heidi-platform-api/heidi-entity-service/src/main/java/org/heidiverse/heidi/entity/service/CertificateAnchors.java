// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.entity.service;

import java.util.List;
import org.heidiverse.heidi.signing.adapters.ManagedCertificates;
import org.heidiverse.heidi.entity.model.entity.IssuingSubcaEntity;
import org.heidiverse.heidi.entity.model.issuer.PlatformKeyResponse;
import org.heidiverse.heidi.entity.model.issuer.PlatformKeyResponse.AnchorOrigin;

/** Classify the stored anchor before profile-specific output removes any root. */
public final class CertificateAnchors {
    private CertificateAnchors() {}

    public static List<PlatformKeyResponse.ChainEntry> details(List<String> chain) {
        return chain.stream().map(encoded -> {
            var certificate = ManagedCertificates.info(encoded);
            return new PlatformKeyResponse.ChainEntry(certificate.getSubject().getDisplay(),
                    certificate.getIssuer().getDisplay(), certificate.getFingerprint(),
                    certificate.getPathLength(), java.time.Instant.ofEpochSecond(certificate.getNotAfter()));
        }).toList();
    }

    public static PlatformKeyResponse.Anchor resolve(List<String> chain, List<IssuingSubcaEntity> cas) {
        if (chain.isEmpty()) return new PlatformKeyResponse.Anchor(AnchorOrigin.UNKNOWN, null, null, null);
        var encoded = chain.getLast();
        try {
            var certificate = ManagedCertificates.info(encoded);
            var fingerprint = certificate.getFingerprint();
            var subject = certificate.getSubject().getDisplay();
            if (!certificate.isCa() || !ManagedCertificates.selfSigned(encoded)) {
                return new PlatformKeyResponse.Anchor(AnchorOrigin.UNKNOWN, subject, fingerprint, null);
            }
            var managed = cas.stream().filter(ca -> ca.getParentCaId() == null
                    && (encoded.equals(ca.getGeneratedRootCertificate())
                        || (ca.getGeneratedRootCertificate() == null
                            && "SELF_SIGNED".equals(ca.getCertificateSource())
                            && ca.getCertificateHistory().contains(encoded)))).findFirst();
            return new PlatformKeyResponse.Anchor(managed.isPresent() ? AnchorOrigin.PLATFORM : AnchorOrigin.EXTERNAL,
                    subject, fingerprint, managed.map(IssuingSubcaEntity::getId).orElse(null));
        } catch (Exception error) {
            return new PlatformKeyResponse.Anchor(AnchorOrigin.UNKNOWN, null, null, null);
        }
    }
}
