// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.heidiverse.heidi.signing.server.SigningGrants.Grant;

/** Durable boundary for what a client may do with a scope. */
public interface SigningGrantStore {
    /**
     * The scope's policy, or empty where none was ever written. An empty list inside the optional
     * is a policy that grants nobody anything, which is not the same as no policy at all.
     */
    Optional<List<Grant>> find(String scope);

    /** Replaces the scope's policy; the platform always writes the complete list. */
    void replace(String scope, List<Grant> grants);

    void revoke(String scope);

    boolean isRevoked(String scope);

    default Set<String> scopes() {
        return Set.of();
    }
}
