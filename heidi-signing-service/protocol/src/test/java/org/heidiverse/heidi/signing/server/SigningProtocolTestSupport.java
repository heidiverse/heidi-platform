// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import static uniffi.heidi_signing.Heidi_signing_jvmKt.authenticate;
import static uniffi.heidi_signing.Heidi_signing_jvmKt.generateSeed;

import java.security.MessageDigest;
import java.util.Base64;

final class SigningProtocolTestSupport {
    private SigningProtocolTestSupport() {}

    static byte[] seed() {
        return generateSeed();
    }

    static byte[] publicKey(byte[] seed, String provider) {
        try {
            return uniffi.heidi_signing.Heidi_signing_jvmKt.publicKey(seed, null, provider);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    static byte[] auth(byte[] seed, byte[] psk, String keyId, byte[] request) {
        try {
            return authenticate(seed, null, psk, "software", keyId, request);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    static String hash(byte[] request) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(request));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
