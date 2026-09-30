// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import java.util.UUID;

import tools.jackson.databind.JsonNode;

/** Request to create or replace one identity slot. */
public record IdentityKeySlotRequest(
        UUID id,
        IdentityKeySlotType type,
        IssuerTrustSystem trustSystem,
        String operation,
        UUID keyId,
        Integer providerId,
        UUID certificateId,
        Integer order,
        JsonNode configuration,
        IdentityKeySlotConsumer consumer) {
    public IdentityKeySlotRequest(
            UUID id,
            IdentityKeySlotType type,
            IssuerTrustSystem trustSystem,
            String operation,
            UUID keyId,
            Integer providerId,
            UUID certificateId,
            Integer order,
            JsonNode configuration) {
        this(id, type, trustSystem, operation, keyId, providerId, certificateId,
                order, configuration, null);
    }
}
