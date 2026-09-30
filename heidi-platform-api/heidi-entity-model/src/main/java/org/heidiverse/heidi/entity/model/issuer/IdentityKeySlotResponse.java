// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/** Public representation of an identity-owned key slot. */
public record IdentityKeySlotResponse(
        UUID id,
        IdentityKeySlotType type,
        IssuerTrustSystem trustSystem,
        String operation,
        UUID keyId,
        Integer providerId,
        UUID certificateId,
        int order,
        JsonNode configuration,
        Instant updatedAt,
        IdentityKeySlotConsumer consumer) {
    public IdentityKeySlotResponse(
            UUID id,
            IdentityKeySlotType type,
            IssuerTrustSystem trustSystem,
            String operation,
            UUID keyId,
            Integer providerId,
            UUID certificateId,
            int order,
            JsonNode configuration,
            Instant updatedAt) {
        this(id, type, trustSystem, operation, keyId, providerId, certificateId,
                order, configuration, updatedAt, null);
    }
}
