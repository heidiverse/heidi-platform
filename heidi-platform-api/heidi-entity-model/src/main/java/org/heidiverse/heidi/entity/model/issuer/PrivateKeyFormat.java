// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

/** Accepted transport formats for importing private key material. */
public enum PrivateKeyFormat {
    JWK,
    JWKS,
    PEM,
    PKCS12
}
