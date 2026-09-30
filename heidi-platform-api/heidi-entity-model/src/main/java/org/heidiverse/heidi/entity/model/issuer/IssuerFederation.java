// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * One identity's place in OpenID Federation.
 *
 * <p>Every entity the identity publishes - a credential issuer per credential schema version, its
 * verifier, and its authority entity when {@code authority} is set - is signed with the active
 * version of {@code signingKeyId}. A subordinate statement needs both sides: the subordinate
 * lists the authority in {@code authorityHints}, and the authority lists the subordinate in
 * {@code subordinateIds}.
 *
 * @param signingKeyId legacy compatibility value; the effective key is the OIDF identity-statement
 *     slot assignment; null opts out
 * @param authorityHints entity identifiers of superiors, on this platform or elsewhere
 * @param authority whether the identity acts as an intermediate or trust anchor
 * @param subordinateIds identities this authority accepts as subordinates
 * @param credentialSchemeIds credential schemes published as issuer entities; {@code null} means
 *     all eligible schemes for backwards compatibility
 */
public record IssuerFederation(
        UUID signingKeyId,
        List<String> authorityHints,
        boolean authority,
        List<Integer> subordinateIds,
        List<Integer> credentialSchemeIds) {

    /** Compatibility constructor for persisted/configuration clients predating scheme selection. */
    public IssuerFederation(
            UUID signingKeyId,
            List<String> authorityHints,
            boolean authority,
            List<Integer> subordinateIds) {
        this(signingKeyId, authorityHints, authority, subordinateIds, null);
    }

    public IssuerFederation {
        authorityHints = authorityHints == null ? List.of() : List.copyOf(authorityHints);
        subordinateIds = subordinateIds == null ? List.of() : List.copyOf(subordinateIds);
        credentialSchemeIds = credentialSchemeIds == null
                ? null : List.copyOf(credentialSchemeIds);
    }

    public static IssuerFederation disabled() {
        return new IssuerFederation(null, null, false, null);
    }

    public boolean enabled() {
        return signingKeyId != null;
    }

    /** Trims and de-duplicates the lists and rejects hints that are not entity identifiers. */
    public IssuerFederation validated() {
        var hints = new LinkedHashSet<String>();
        for (var hint : authorityHints) {
            if (hint == null || hint.isBlank()) continue;
            hints.add(requireEntityIdentifier(hint.trim()));
        }
        return new IssuerFederation(
                signingKeyId,
                List.copyOf(hints),
                authority,
                List.copyOf(new LinkedHashSet<>(subordinateIds)),
                credentialSchemeIds == null
                        ? null : List.copyOf(new LinkedHashSet<>(credentialSchemeIds)));
    }

    // Entity identifiers are absolute http(s) URLs without query or fragment.
    private static String requireEntityIdentifier(String value) {
        try {
            var uri = URI.create(value);
            var scheme = uri.getScheme();
            var web = "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
            if (web && uri.getHost() != null && uri.getQuery() == null && uri.getFragment() == null) {
                return value;
            }
        } catch (IllegalArgumentException ignored) {
            // Reported below.
        }
        throw new IllegalArgumentException("Authority hint is not an entity identifier: " + value);
    }
}
