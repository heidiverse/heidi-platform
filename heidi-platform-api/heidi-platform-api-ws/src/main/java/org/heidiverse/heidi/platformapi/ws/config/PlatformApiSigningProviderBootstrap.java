// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.config;

import org.heidiverse.heidi.entity.service.SigningProviderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;

/** Keeps a chart-configured global signing provider available in the Cockpit. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlatformApiSigningProviderBootstrap implements ApplicationRunner {
    private final SigningProviderService signingProviderService;
    private final String endpoint;

    public PlatformApiSigningProviderBootstrap(
            SigningProviderService signingProviderService,
            @Value("${heidi.platform.global-signing-provider.endpoint:}") String endpoint) {
        this.signingProviderService = signingProviderService;
        this.endpoint = endpoint;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (endpoint != null && !endpoint.isBlank()) {
            signingProviderService.ensureGlobalProvider();
        }
    }
}
