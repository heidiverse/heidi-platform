// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import uniffi.heidi_signing.Heidi_signing_jvmKt;

/** The seed a backend keeps, and the public keys an operator or the platform sees. */
class SigningClientKeysTest {
    private final String seed =
            Base64.getEncoder().encodeToString(Heidi_signing_jvmKt.generateSeed());

    @Test
    void publishesAStableKeyPerSigningService() {
        var keys = new SigningClientKeys("issuer", seed);

        assertEquals(keys.publicKey("local"), keys.publicKey("local"));
        assertNotEquals(keys.publicKey("local"), keys.publicKey("pkcs11"));
        assertEquals("issuer", keys.client());
    }

    /** The same seed in another process gives the same key: what an operator pasted stays valid. */
    @Test
    void theKeyDependsOnlyOnTheSeedAndTheScheme() {
        assertEquals(
                new SigningClientKeys("issuer", seed).publicKey("local"),
                new SigningClientKeys("issuer", seed).publicKey("local"));
    }

    @Test
    void anUnconfiguredBackendPublishesNothing() {
        var keys = new SigningClientKeys("issuer", "");

        assertFalse(keys.isConfigured());
        assertThrows(IllegalStateException.class, () -> keys.publicKey("local"));
    }

    @Test
    void refusesASeedThatIsNotThirtyTwoBytes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SigningClientKeys("issuer", Base64.getEncoder().encodeToString(new byte[8])));
    }

    @Test
    void authenticatesAsItsClient() {
        var keys = new SigningClientKeys("issuer", seed);

        assertTrue(keys.isConfigured());
        assertEquals("issuer", keys.authentication().client());
    }
}
