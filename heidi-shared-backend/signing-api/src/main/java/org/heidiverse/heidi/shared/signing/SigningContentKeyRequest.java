// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

/** Provider-neutral JWE key-management parameters; the payload and plaintext never cross the API. */
public record SigningContentKeyRequest(
        String algorithm,
        String contentEncryption,
        String ephemeralPublicJwk,
        String agreementPartyUInfo,
        String agreementPartyVInfo,
        byte[] encryptedKey) {
    public SigningContentKeyRequest {
        encryptedKey = encryptedKey == null ? null : encryptedKey.clone();
    }

    @Override
    public byte[] encryptedKey() {
        return encryptedKey == null ? null : encryptedKey.clone();
    }
}
