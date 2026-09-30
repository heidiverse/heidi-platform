// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.adapters;

import java.util.Base64;

import uniffi.heidi_signing.Heidi_signing_jvmKt;

/**
 * A backend's own signing-protocol credential.
 *
 * <p>One seed per service, whatever number of signing services a deployment registers: the key is
 * derived per provider scheme, so each service sees a different public key and none of them sees
 * the seed.
 *
 * <pre>
 *   seed ──HMAC-SHA256("AUTH PROVIDER" ‖ scheme ‖ "FINISHED")──► key for that signing service
 * </pre>
 */
public final class SigningClientKeys {
    private static final int SEED_BYTES = 32;

    private final String client;
    private final byte[] seed;

    public SigningClientKeys(String client, String base64Seed) {
        this.client = client;
        this.seed = decode(base64Seed);
    }

    public String client() {
        return client;
    }

    public boolean isConfigured() {
        return seed != null;
    }

    /** The public key this client shows the given signing service, Base64 encoded. */
    public String publicKey(String providerScheme) {
        requireConfigured();
        try {
            return Base64.getEncoder().encodeToString(
                    Heidi_signing_jvmKt.publicKey(seed, null, providerScheme));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not derive the signing client key", exception);
        }
    }

    public RemoteSigningKeyProvider.Authentication authentication() {
        requireConfigured();
        return RemoteSigningKeyProvider.Authentication.forClient(client, seed);
    }

    private void requireConfigured() {
        if (seed == null) {
            throw new IllegalStateException("No signing client seed is configured for " + client);
        }
    }

    private static byte[] decode(String base64Seed) {
        if (base64Seed == null || base64Seed.isBlank()) return null;

        var decoded = Base64.getDecoder().decode(base64Seed.trim());
        if (decoded.length != SEED_BYTES) {
            throw new IllegalArgumentException(
                    "A signing client seed is " + SEED_BYTES + " bytes, got " + decoded.length);
        }
        return decoded;
    }
}
