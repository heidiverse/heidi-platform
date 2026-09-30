// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model;

import java.time.ZonedDateTime;

public interface Signable {
    ZonedDateTime getSignatureTimestamp();

    ZonedDateTime getExpiresAt();
}
