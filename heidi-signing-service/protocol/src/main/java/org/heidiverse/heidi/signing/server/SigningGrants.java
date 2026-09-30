// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import java.util.List;
import java.util.Set;

import org.heidiverse.heidi.shared.signing.SigningKeyScope;
import org.heidiverse.heidi.shared.signing.SigningPurpose;

/** Exact-version policies prevent a logical key grant from authorizing unused versions. */
public final class SigningGrants {
    private final SigningGrantStore store;
    private final boolean failOpen;

    public SigningGrants(SigningGrantStore store) {
        this(store, false);
    }

    public SigningGrants(SigningGrantStore store, boolean failOpen) {
        this.store = store;
        this.failOpen = failOpen;
    }

    public record Grant(String client, Set<SigningPurpose> purposes) {
        public Grant {
            purposes = Set.copyOf(purposes);
        }
    }

    public List<Grant> find(String scope) {
        return store.find(scope).orElse(List.of());
    }

    public void replace(String scope, List<Grant> grants) {
        store.replace(scope, grants);
    }

    public void revoke(String keyUri) { store.revoke(keyUri); }

    public void requireNotRevoked(String keyUri, SigningPurpose purpose) {
        if (purpose == SigningPurpose.KEY_MANAGEMENT || purpose == SigningPurpose.READ) return;
        if (store.isRevoked(keyUri)) throw new SigningAuthorizationException("Key version is revoked: " + keyUri);
    }

    public Set<String> scopes() {
        return store.scopes();
    }

    public boolean hasPolicy(String scope) {
        return store.find(scope).isPresent();
    }

    /**
     * Closes a newly created scope around its creator, so a key is never briefly open between
     * being created and the platform writing its real policy.
     */
    public void initialise(String scope, String creator) {
        if (creator == null || hasPolicy(scope)) return;
        store.replace(scope, List.of(new Grant(creator, Set.of(SigningPurpose.KEY_MANAGEMENT))));
    }

    /**
     * Requires the purpose on this exact version or operation; unknown scopes are closed.
     */
    public void require(String client, String keyUri, SigningPurpose purpose) {
        requireNotRevoked(keyUri, purpose);
        var policy = store.find(keyUri);
        if (policy.isEmpty()) {
            if (failOpen) return;
            throw new SigningAuthorizationException("No grant policy exists on " + keyUri);
        }

        var granted = policy.get().stream()
                .filter(grant -> grant.client().equals(client))
                .anyMatch(grant -> grant.purposes().contains(purpose));
        if (granted) return;

        throw new SigningAuthorizationException("Client '" + client + "' has no '"
                + purpose.wireValue() + "' grant on " + keyUri);
    }

    /**
     * Refuses unless the client was granted something on the exact key version. Reading a key returns
     * its public half and needs no purpose of its own, but a client with no business with the key
     * scope should not enumerate it either.
     */
    public void requireAny(String client, String keyUri) {
        var policy = store.find(keyUri);
        if (policy.isEmpty()) {
            if (failOpen) return;
            throw new SigningAuthorizationException("No grant policy exists on " + keyUri);
        }

        var granted = policy.get().stream().anyMatch(grant -> grant.client().equals(client));
        if (granted) return;

        throw new SigningAuthorizationException(
                "Client '" + client + "' has no grant on " + keyUri);
    }

    /** The stable logical-key scope of a namespaced provider key. */
    public static String scope(String keyUri) {
        return SigningKeyScope.of(keyUri);
    }
}
