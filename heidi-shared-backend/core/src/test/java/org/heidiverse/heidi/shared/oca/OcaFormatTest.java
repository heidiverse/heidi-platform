// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.oca;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OcaFormatTest {
    @Test
    void recognizesWalletAgents() {
        assertEquals(OcaFormat.SWIYU, OcaFormat.select(null, "swiyuWallet"));
        assertEquals(OcaFormat.SWIYU, OcaFormat.select(null, "swiyuSandboxWallet/2.0"));
        assertEquals(OcaFormat.LEGACY, OcaFormat.select(null, null));
        assertEquals(OcaFormat.LEGACY, OcaFormat.select(null, "Heidi"));
        assertEquals(OcaFormat.LEGACY, OcaFormat.select(null, "swiyuWalletUnrelated"));
    }

    @Test
    void explicitFormatWins() {
        assertEquals(OcaFormat.LEGACY, OcaFormat.select("legacy", "swiyuWallet"));
        assertEquals(OcaFormat.SWIYU, OcaFormat.select("swiyu", null));
        assertThrows(IllegalArgumentException.class, () -> OcaFormat.select("other", null));
    }
}
