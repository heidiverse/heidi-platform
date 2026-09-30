// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** A provider whose grants the platform can write. Not every backend offers this. */
public interface SigningGrantWriter {
    /** Replaces the scope's grants; what is left out is revoked. */
    void replaceGrants(String scope, Map<String, Set<SigningPurpose>> grants);

    /** Existing grant scopes, used to close policies that are no longer referenced. */
    default Set<String> grantScopes() {
        return Set.of();
    }

    /** Provider-confirmed policy for one exact scope. */
    default Optional<Map<String, Set<SigningPurpose>>> grants(String scope) {
        return Optional.empty();
    }
}
