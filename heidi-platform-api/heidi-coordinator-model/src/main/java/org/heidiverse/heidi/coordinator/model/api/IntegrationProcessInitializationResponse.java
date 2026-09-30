// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.api;

import java.time.ZonedDateTime;
import java.util.UUID;

/** Backend-only response returned before a process is started. */
public record IntegrationProcessInitializationResponse(
        UUID processId, String processToken, String txCode, ZonedDateTime expiresAt) {}
