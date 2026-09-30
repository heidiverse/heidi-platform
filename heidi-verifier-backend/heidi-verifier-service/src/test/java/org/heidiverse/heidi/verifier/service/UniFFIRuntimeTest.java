// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class UniFFIRuntimeTest {

    @Test
    void loadsCallbackClone() {
        // RC11-generated bindings require this callback API at test runtime.
        assertDoesNotThrow(() -> Class.forName("uniffi.runtime.UniffiCallbackInterfaceClone"));
    }
}
