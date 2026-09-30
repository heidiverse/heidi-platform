// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.entity.model.issuer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.heidiverse.heidi.shared.signing.SigningPurpose;

public record SigningFlowRequest(@NotNull UUID flowId, @NotNull IdentityKeySlotConsumer client,
                                 @NotBlank String keyUri, @NotNull SigningPurpose purpose,
                                 @NotNull Instant expiresAt) {}
