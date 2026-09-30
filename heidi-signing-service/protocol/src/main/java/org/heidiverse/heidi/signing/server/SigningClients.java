// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.heidiverse.heidi.shared.signing.SigningKeyException;

/**
 * The callers this service accepts, by name and public key.
 *
 * <p>Two ways to learn one, and a deployment picks which. An allow-list is configuration an
 * operator writes; platform-assisted acceptance lets the platform introduce the issuer and the
 * verifier, which moves public keys only. An empty allow-list accepts nobody.
 */
public final class SigningClients {
    public enum Acceptance {
        ALLOW_LIST("allow-list"),
        PLATFORM_ASSISTED("platform-assisted");

        private final String wireValue;

        Acceptance(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }

        public static Acceptance of(String wireValue) {
            for (var acceptance : values()) {
                if (acceptance.wireValue.equals(wireValue)) return acceptance;
            }
            throw new SigningKeyException("Unknown client acceptance mode: " + wireValue);
        }
    }

    private final SigningClientStore store;
    private final Acceptance acceptance;
    private final String administrator;

    public SigningClients(
            Map<String, byte[]> accepted, Acceptance acceptance, String administrator) {
        this(new InMemorySigningClientStore(), acceptance, administrator);
        accepted.forEach(store::put);
    }

    public SigningClients(
            SigningClientStore store, Acceptance acceptance, String administrator) {
        this.store = Objects.requireNonNull(store, "Signing client store must not be null");
        this.acceptance = acceptance;
        this.administrator = administrator;
    }

    public Acceptance acceptance() {
        return acceptance;
    }

    public List<String> names() {
        return store.findAll().keySet().stream().sorted().toList();
    }

    /** Public acceptance material lets operators detect a backend seed change. */
    public String publicKey(String client) {
        var key = store.findAll().get(client);
        return key == null ? null : java.util.Base64.getEncoder().encodeToString(key);
    }

    public boolean knows(String client) {
        return client != null && store.findAll().containsKey(client);
    }

    public boolean accepts(String client, byte[] publicKey) {
        if (client == null || publicKey == null) return false;
        var known = store.findAll().get(client);
        return known != null && publicKey != null && MessageDigest.isEqual(known, publicKey);
    }

    /**
     * Refuses unless the caller is the administrative client. Key lifecycle and the first policy on
     * a scope belong to it, because a scope without a policy is open and whoever creates one
     * decides who may use it. During platform-assisted bootstrap the configured administrator may
     * introduce the first public key; an empty allow-list has no administrator.
     */
    public void requireAdministrator(String caller, String action) {
        if (administrator != null && administrator.equals(caller)
                && (knows(caller) || acceptance == Acceptance.PLATFORM_ASSISTED)) return;

        throw new SigningAuthorizationException(
                "Only the '" + administrator + "' client may " + action);
    }

    /**
     * Platform-assisted acceptance: the administrative client hands over another client's public
     * key. Only that one client may, so a backend cannot introduce a peer of its own choosing.
     */
    public void add(String client, byte[] publicKey, String caller) {
        if (acceptance != Acceptance.PLATFORM_ASSISTED) {
            throw new SigningAuthorizationException(
                    "This signing service accepts clients from its configuration only");
        }
        requireAdministrator(caller, "introduce another");
        if (client == null || client.isBlank() || publicKey == null || publicKey.length == 0) {
            throw new SigningKeyException("A client needs a name and a public key");
        }
        store.put(client, publicKey);
    }

    /** Stops accepting a client, so a compromised or rotated key can be withdrawn. */
    public void remove(String client, String caller) {
        requireAdministrator(caller, "withdraw another");
        if (client == null || client.isBlank()) {
            throw new SigningKeyException("A client name is required");
        }
        if (administrator != null && administrator.equals(client)) {
            throw new SigningAuthorizationException("The administrative client cannot be withdrawn");
        }
        store.remove(client);
    }
}
