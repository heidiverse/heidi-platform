// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.statuslist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.net.URI;
import java.util.UUID;

public record StatusListRequest(
        @NotBlank String name,
        @NotNull StatusListType type,
        int bits,
        @Positive int entryCount,
        @NotNull StatusListPublishMode publishMode,
        URI endpoint,
        @NotNull UUID signingKeyId,
        @Positive Long ttl) {}
