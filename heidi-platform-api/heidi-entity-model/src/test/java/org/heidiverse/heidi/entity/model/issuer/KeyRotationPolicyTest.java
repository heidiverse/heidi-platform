// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.entity.model.issuer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class KeyRotationPolicyTest {
    @Test
    void intervalIsRequiredOnlyForAutomaticRotation() {
        assertDoesNotThrow(() -> new KeyRotationPolicy(KeyRotationMode.MANUAL, null, 0));
        assertDoesNotThrow(() -> new KeyRotationPolicy(KeyRotationMode.EXTERNAL, null, 0));
        assertDoesNotThrow(() -> new KeyRotationPolicy(KeyRotationMode.AUTOMATIC, 60L, 0));

        assertThrows(IllegalArgumentException.class,
                () -> new KeyRotationPolicy(KeyRotationMode.AUTOMATIC, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new KeyRotationPolicy(KeyRotationMode.AUTOMATIC, 0L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new KeyRotationPolicy(KeyRotationMode.MANUAL, 60L, 0));
    }
}
