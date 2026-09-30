// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import java.util.Map;
import java.util.Set;

import org.heidiverse.heidi.shared.signing.SigningPurpose;

/** Desired identity authority and the policy confirmed by its signing provider. */
public record IdentityGrantStatus(
        String scope,
        int providerId,
        Map<String, Set<SigningPurpose>> desired,
        Map<String, Set<SigningPurpose>> confirmed,
        boolean pending) {}
