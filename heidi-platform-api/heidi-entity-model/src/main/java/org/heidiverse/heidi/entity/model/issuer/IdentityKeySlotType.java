// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

/** A role an identity assigns to a key or provider. */
public enum IdentityKeySlotType {
    CREDENTIAL_SIGNING,
    IDENTITY_STATEMENT,
    PRESENTATION_SIGNING,
    DECRYPTION,
    OPERATION,
    FEDERATION,
    STATUS_LIST;

    public boolean requiresCertificate(IssuerTrustSystem trust) {
        return trust == IssuerTrustSystem.EUDI
                && (this == CREDENTIAL_SIGNING
                || this == IDENTITY_STATEMENT
                || this == PRESENTATION_SIGNING);
    }

    public SigningCertificateProfile certificateProfile(IssuerTrustSystem trust) {
        if (this == STATUS_LIST) return SigningCertificateProfile.STATUS_LIST;
        if ((this == IDENTITY_STATEMENT || this == PRESENTATION_SIGNING)
                && trust == IssuerTrustSystem.EUDI) {
            return SigningCertificateProfile.ACCESS;
        }
        return SigningCertificateProfile.CREDENTIAL_SIGNING;
    }
}
