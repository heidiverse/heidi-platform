// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

/** How a certificate entered the platform. */
public enum SigningCertificateSource {
    DEVELOPMENT,
    SELF_SIGNED,
    CA_ISSUED,
    IMPORTED,
    REGISTRAR
}
