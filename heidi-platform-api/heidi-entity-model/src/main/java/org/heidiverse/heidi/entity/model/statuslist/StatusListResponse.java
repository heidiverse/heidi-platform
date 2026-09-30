// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.statuslist;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record StatusListResponse(
        UUID id,
        String tenantId,
        String name,
        StatusListType type,
        int bits,
        int entryCount,
        StatusListPublishMode publishMode,
        URI endpoint,
        URI uri,
        UUID signingKeyId,
        String signingKeyName,
        Long ttl,
        Instant publishedAt,
        UUID swissStatusListId,
        URI swissStatusListUrl,
        Instant swissPublishedAt,
        Instant createdAt,
        Instant updatedAt) {}
