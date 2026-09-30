// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.entity.service;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Only decryption slots need no certificate or external publication before activation. */
@Service
public class KeyRotationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyRotationService.class);
    private final SigningKeyService keys;
    private final IssuerService identities;

    public KeyRotationService(SigningKeyService keys, IssuerService identities) {
        this.keys = keys;
        this.identities = identities;
    }

    @Scheduled(fixedDelayString = "${heidi.platform.keys.rotation-check-interval-ms:60000}")
    public void rotateDueKeys() {
        var rotated = 0;
        var now = Instant.now();
        for (var key : keys.keys()) {
            try {
                if (keys.rotateDue(key.getTenantId(), key.getId(), now).isPresent()) rotated++;
            } catch (RuntimeException exception) {
                // Retry next pass without preventing other providers from rotating.
                LOGGER.warn("Could not rotate decryption key {}", key.getId(), exception);
            }
        }
        if (rotated > 0) identities.reconcileSigningGrants();
    }
}
