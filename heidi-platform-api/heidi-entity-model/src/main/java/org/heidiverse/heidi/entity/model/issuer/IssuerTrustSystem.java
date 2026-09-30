// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

public enum IssuerTrustSystem {
    Switzerland(false),
    EUDI(true),
    Custom(false),
    OIDF(false),
    /** Global routing fallback; it applies to every trust framework when used for decryption. */
    Default(false);

    private final boolean certificateRequired;

    IssuerTrustSystem(boolean certificateRequired) {
        this.certificateRequired = certificateRequired;
    }

    public boolean isCertificateRequired() {
        return certificateRequired;
    }
}
