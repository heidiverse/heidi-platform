// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Set;

import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.junit.jupiter.api.Test;

class PlatformApiNoSecurityConfigTest {
    @Test
    void parsesConfiguredTrustSystems() {
        assertThat(PlatformApiNoSecurityConfig.parseTrustSystems("EUDI, Switzerland, EUDI"))
                .containsExactlyInAnyOrder(IssuerTrustSystem.EUDI, IssuerTrustSystem.Switzerland);
    }

    @Test
    void leavesTrustSystemsDisabledByDefault() {
        assertThat(PlatformApiNoSecurityConfig.parseTrustSystems(" ")).isEqualTo(Set.of());
    }

    @Test
    void rejectsRoutingFallback() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PlatformApiNoSecurityConfig.parseTrustSystems("Default"))
                .withMessageContaining("not an assignable trust system");
    }
}
