// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import java.time.Instant;
import java.util.List;

/** Startup audit for the identity-owned key migration. */
public record IdentityKeyIntegrityReport(Instant checkedAt, List<IdentityKeyViolation> violations) {
    public IdentityKeyIntegrityReport {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public boolean clean() {
        return violations.isEmpty();
    }
}
