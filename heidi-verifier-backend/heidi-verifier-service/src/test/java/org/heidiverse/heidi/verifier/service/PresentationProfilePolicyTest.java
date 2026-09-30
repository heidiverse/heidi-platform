// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.junit.jupiter.api.Test;

class PresentationProfilePolicyTest {

    @Test
    void eudiAdvertisesBothHaipResponseEncryptionMethods() {
        var policy = PresentationProfilePolicy.resolve("EUDI_PRESENTATION_2026_1");

        assertEquals("ECDH-ES", policy.responseEncryptionAlg());
        assertEquals(java.util.List.of("ECDH-ES"), policy.responseEncryptionAlgs());
        assertEquals("A256GCM", policy.responseEncryptionEnc());
        assertEquals(java.util.List.of("A128GCM", "A256GCM"), policy.responseEncryptionEncs());
    }

    @Test
    void unsupportedProfileIsRejected() {
        var exception = assertThrows(
                VpVerificationException.class,
                () -> PresentationProfilePolicy.resolve(
                        "UNSUPPORTED_PRESENTATION_2026_1"));

        assertEquals(
                "Unsupported presentation profile: UNSUPPORTED_PRESENTATION_2026_1",
                exception.getMessage());
    }
}
