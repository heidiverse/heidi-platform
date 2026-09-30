// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.statuslist;

import jakarta.validation.constraints.PositiveOrZero;

public record StatusListEntryRequest(
        @PositiveOrZero int index,
        @PositiveOrZero int status) {}
