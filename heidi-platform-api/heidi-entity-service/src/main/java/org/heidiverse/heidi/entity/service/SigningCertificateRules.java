// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.entity.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.heidiverse.heidi.entity.data.repository.SigningCertificateRepository;
import org.heidiverse.heidi.entity.model.entity.SigningCertificateEntity;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile;

/** The same certificate boundary applies at assignment, resolution and activation. */
final class SigningCertificateRules {
    private SigningCertificateRules() {}

    static boolean validAt(SigningCertificateEntity certificate, Instant now) {
        return certificate.getRetiredAt() == null
                && !certificate.getCertificateChain().isEmpty()
                && certificate.getNotBefore() != null && !now.isBefore(certificate.getNotBefore())
                && certificate.getNotAfter() != null && now.isBefore(certificate.getNotAfter());
    }

    static void requireUsable(SigningCertificateEntity certificate, UUID version,
            SigningCertificateProfile profile, IssuerTrustSystem trust) {
        if (!Objects.equals(certificate.getKeyVersionId(), version)) {
            throw new IllegalArgumentException("Select a certificate for the active key version");
        }
        if (certificate.getProfile() != profile
                || !Objects.equals(framework(certificate.getTrustSystem()), framework(trust))) {
            throw new IllegalArgumentException("Certificate profile or trust framework does not match the slot");
        }
        if (!validAt(certificate, Instant.now())) {
            throw new IllegalArgumentException("Slot certificate is expired, not yet valid, or incomplete");
        }
    }

    static IssuerTrustSystem framework(IssuerTrustSystem trust) {
        return trust == IssuerTrustSystem.Default ? null : trust;
    }

    static void markUsed(SigningCertificateRepository certificates, UUID certificateId) {
        if (certificateId == null) return;

        var certificate = certificates.findById(certificateId).orElseThrow();
        if (certificate.getFirstUsedAt() != null) return;

        certificate.setFirstUsedAt(Instant.now());
        certificates.save(certificate);
    }
}
