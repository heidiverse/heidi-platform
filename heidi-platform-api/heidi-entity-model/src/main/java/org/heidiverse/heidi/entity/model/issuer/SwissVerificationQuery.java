// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/** A verification-query public statement returned by the Swiss authoring API. */
public record SwissVerificationQuery(
        String id,
        Integer version,
        String status,
        String purposeName,
        String purposeDescription,
        String scope,
        JsonNode query,
        String jwt,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt) {}
