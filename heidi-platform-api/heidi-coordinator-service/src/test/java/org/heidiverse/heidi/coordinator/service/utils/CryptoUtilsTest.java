// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class CryptoUtilsTest {

    @Test
    void sriHashUsesStandardBase64() throws Exception {
        assertEquals(
                "sha256-LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ=",
                CryptoUtils.computeSha256HashSri("hello"));
    }

    @Test
    void nonceHas256BitsOfRandomness() {
        var nonce = CryptoUtils.generateNonce();

        assertEquals(32, Base64.getUrlDecoder().decode(nonce).length);
    }
}
