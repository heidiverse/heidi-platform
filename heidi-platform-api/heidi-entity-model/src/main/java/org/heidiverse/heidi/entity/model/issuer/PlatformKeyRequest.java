// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.heidiverse.heidi.entity.model.signing.SigningKeyId;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;

/** Request to create one neutral, provider-backed platform key. */
public record PlatformKeyRequest(
        @NotBlank @Pattern(regexp = SigningKeyId.PATTERN, message = SigningKeyId.MESSAGE)
        String keyId,
        @NotBlank String algorithm,
        Integer providerId,
        SigningKeyUsage usage,
        String privateJwk,
        PrivateKeyFormat privateKeyFormat,
        String privateKey,
        String privateKeyPassword) {
    public PlatformKeyRequest {
        usage = usage == null ? SigningKeyUsage.SIGN : usage;
    }

    /** Keeps the original private-JWK request shape source-compatible. */
    public PlatformKeyRequest(
            String keyId,
            String algorithm,
            Integer providerId,
            SigningKeyUsage usage,
            String privateJwk) {
        this(keyId, algorithm, providerId, usage, privateJwk, null, null, null);
    }
}
