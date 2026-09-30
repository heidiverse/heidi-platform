// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.verifierauthflow;

import org.heidiverse.heidi.coordinator.model.ProofSchemeResponse;

import java.time.Instant;
import java.util.UUID;

public record VerifierAuthFlowElement(
        UUID uuid,
        ProofSchemeResponse proofScheme,
        String displayName,
        Instant createdAt,
        Instant updatedAt) {}
