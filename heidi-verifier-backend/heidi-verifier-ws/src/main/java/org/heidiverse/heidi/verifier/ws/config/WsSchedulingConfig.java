// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.ws.config;

import org.heidiverse.heidi.verifier.data.service.VerificationRequestService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@EnableScheduling
@Configuration
public class WsSchedulingConfig {
    private static final Logger logger = LoggerFactory.getLogger(WsSchedulingConfig.class);

    private final VerificationRequestService verificationRequestService;

    public WsSchedulingConfig(final VerificationRequestService verificationRequestService) {
        this.verificationRequestService = verificationRequestService;
    }

    @Scheduled(fixedDelayString = "${heidi.verifier.oid4vp.request-cleanup}", initialDelay = 5000)
    void cleanupVerificationRequests() {
        final var numDeletedEntries = verificationRequestService.cleanupExpiredEntries();
        logger.debug("Cleaned up {} expired entries", numDeletedEntries);
    }
}
