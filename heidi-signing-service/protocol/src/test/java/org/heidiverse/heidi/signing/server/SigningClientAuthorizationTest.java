// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import static org.heidiverse.heidi.signing.server.SigningProtocolTestSupport.auth;
import static org.heidiverse.heidi.signing.server.SigningProtocolTestSupport.publicKey;
import static org.heidiverse.heidi.signing.server.SigningProtocolTestSupport.seed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.heidiverse.heidi.shared.signing.SigningKeyException;
import org.heidiverse.heidi.shared.signing.SigningPurpose;
import org.junit.jupiter.api.Test;

/**
 * A client is a named caller of the signing protocol, and a grant is what that client may do with
 * one logical key. Together they replace "whoever holds the token may do everything".
 */
class SigningClientAuthorizationTest {
    private static final String PROVIDER = "software";
    private static final String KEY_SCOPE = "kc/0f7a2f2a-3a0e-4f28-9a94-6d5f2b0f1a11";
    private static final String KEY_URI = "software://" + KEY_SCOPE + "/8d2b4e10-1c6f-4f1a-9c3d-2f8e5a7b0c44";

    @Test
    void refusesARegistrationForAKeyItDoesNotAccept() {
        var accepted = seed();
        var clients = clients(Map.of("issuer", publicKey(accepted, PROVIDER)));
        var service = service(clients);

        var stranger = publicKey(seed(), PROVIDER);

        assertThrows(
                SigningKeyException.class,
                () -> service.open(PROVIDER, "issuer", stranger));
    }

    @Test
    void refusesARegistrationForAnUnknownClientName() {
        var seed = seed();
        var service = service(clients(Map.of("issuer", publicKey(seed, PROVIDER))));

        assertThrows(
                SigningKeyException.class,
                () -> service.open(PROVIDER, "cockpit", publicKey(seed, PROVIDER)));
    }

    @Test
    void returnsTheSameRegistrationForAClientThatComesBack() {
        var seed = seed();
        var publicKey = publicKey(seed, PROVIDER);
        var service = service(clients(Map.of("issuer", publicKey)));

        var first = service.open(PROVIDER, "issuer", publicKey);
        service.registerPublicKey(first.id(), publicKey, auth(seed, first.psk(), null, publicKey));
        var second = service.open(PROVIDER, "issuer", publicKey);

        assertEquals(first.id(), second.id());
    }

    @Test
    void namesTheClientOnTheRegistration() {
        var seed = seed();
        var publicKey = publicKey(seed, PROVIDER);
        var service = service(clients(Map.of("issuer", publicKey)));

        var registration = service.open(PROVIDER, "issuer", publicKey);

        assertEquals("issuer", service.client(registration.id()));
    }

    /**
     * Opening a registration proves nothing - the public key it names is public. Completion must
     * therefore present the key the service accepts, or a caller could open under another client's
     * name and finish with a key of its own.
     */
    @Test
    void refusesARegistrationCompletedWithAnotherKey() {
        var accepted = seed();
        var acceptedKey = publicKey(accepted, PROVIDER);
        var service = service(clients(Map.of("issuer", acceptedKey)));
        var registration = service.open(PROVIDER, "issuer", acceptedKey);

        var attacker = seed();
        var attackerKey = publicKey(attacker, PROVIDER);

        assertThrows(
                SigningKeyException.class,
                () -> service.registerPublicKey(
                        registration.id(),
                        attackerKey,
                        auth(attacker, registration.psk(), null, attackerKey)));
    }

    @Test
    void withdrawnClientCannotAuthenticateAnExistingRegistration() {
        var seed = seed();
        var key = publicKey(seed, PROVIDER);
        var clients = clients(Map.of(
                "issuer", key,
                "platform", publicKey(seed(), PROVIDER)));
        var service = service(clients);
        var registration = service.open(PROVIDER, "issuer", key);
        service.registerPublicKey(registration.id(), key, auth(seed, registration.psk(), null, key));
        clients.remove("issuer", "platform");

        var timestamp = Long.toString(System.currentTimeMillis());
        var request = "POST\n/v1/signatures\n{}".getBytes(StandardCharsets.UTF_8);
        var signature = auth(
                seed,
                registration.psk(),
                "key-1",
                (timestamp + ":signing:" + SigningProtocolTestSupport.hash(request))
                        .getBytes(StandardCharsets.UTF_8));

        assertThrows(
                SigningAuthorizationException.class,
                () -> service.authenticate(
                        registration.id(), "key-1", timestamp, "signing", request, signature));
    }

    /** An identity nobody may use is written as an empty policy, which is not the same as none. */
    @Test
    void anExplicitlyEmptyPolicyDeniesEveryone() {
        var grants = grants();
        grants.replace(KEY_URI, List.of(new SigningGrants.Grant("issuer", Set.of(SigningPurpose.SIGNING))));
        grants.replace(KEY_URI, List.of());

        assertThrows(
                SigningKeyException.class,
                () -> grants.require("issuer", KEY_URI, SigningPurpose.SIGNING));
        assertThrows(
                SigningKeyException.class,
                () -> grants.requireAny("issuer", KEY_URI));
    }

    @Test
    void onlyTheAdministrativeClientMayIntroduceAnother() {
        var admin = new SigningClients(
                Map.of("platform", publicKey(seed(), PROVIDER)),
                SigningClients.Acceptance.PLATFORM_ASSISTED,
                "platform");

        assertThrows(
                SigningAuthorizationException.class,
                () -> admin.add("issuer", publicKey(seed(), PROVIDER), "verifier"));
        admin.add("issuer", publicKey(seed(), PROVIDER), "platform");

        assertEquals(List.of("issuer", "platform"), admin.names());
    }

    /**
     * Only the administrator may create keys and establish their initial policy.
     */
    @Test
    void onlyTheAdministrativeClientMayOpenANewScope() {
        var clients = clients(Map.of("platform", publicKey(seed(), PROVIDER)));

        assertThrows(
                SigningAuthorizationException.class,
                () -> clients.requireAdministrator("issuer", "create a key"));
        clients.requireAdministrator("platform", "create a key");
    }

    /** An empty allow-list has no administrator and cannot register an arbitrary client. */
    @Test
    void anUnconfiguredAllowListIsClosed() {
        var empty = clients(Map.of());
        assertThrows(
                SigningAuthorizationException.class,
                () -> empty.requireAdministrator("issuer", "create a key"));
        assertThrows(
                SigningKeyException.class,
                () -> service(empty).open(PROVIDER, "issuer", publicKey(seed(), PROVIDER)));
    }

    @Test
    void aNewScopeIsClosedToEveryoneButItsCreator() {
        var grants = grants();
        grants.initialise(KEY_URI, "platform");

        assertTrue(grants.hasPolicy(KEY_URI));
        grants.require("platform", KEY_URI, SigningPurpose.KEY_MANAGEMENT);
        assertThrows(
                SigningKeyException.class,
                () -> grants.require("issuer", KEY_URI, SigningPurpose.SIGNING));
    }

    @Test
    void allowsSigningWithAGrant() {
        var grants = grants();
        grants.replace(KEY_URI, List.of(new SigningGrants.Grant("issuer", Set.of(SigningPurpose.SIGNING))));

        grants.require("issuer", KEY_URI, SigningPurpose.SIGNING);
    }

    @Test
    void refusesSigningWithoutAGrantForTheClient() {
        var grants = grants();
        grants.replace(KEY_URI, List.of(new SigningGrants.Grant("issuer", Set.of(SigningPurpose.SIGNING))));

        var refused = assertThrows(
                SigningKeyException.class,
                () -> grants.require("verifier", KEY_URI, SigningPurpose.SIGNING));

        assertEquals(
                "Client 'verifier' has no 'signing' grant on " + KEY_URI,
                refused.getMessage());
    }

    @Test
    void refusesAPurposeTheClientWasNotGranted() {
        var grants = grants();
        grants.replace(KEY_URI, List.of(new SigningGrants.Grant("issuer", Set.of(SigningPurpose.SIGNING))));

        assertThrows(
                SigningKeyException.class,
                () -> grants.require("issuer", KEY_URI, SigningPurpose.KEY_MANAGEMENT));
    }

    @Test
    void logicalPolicyCannotSignVersions() {
        var grants = grants();
        grants.replace(KEY_SCOPE, List.of(new SigningGrants.Grant("issuer", Set.of(SigningPurpose.SIGNING))));

        assertThrows(SigningAuthorizationException.class,
                () -> grants.require("issuer", KEY_URI, SigningPurpose.SIGNING));
        assertThrows(SigningAuthorizationException.class,
                () -> grants.requireAny("issuer", KEY_URI));
    }

    @Test
    void logicalPolicyCannotReopenVersion() {
        var grants = grants();
        grants.replace(KEY_SCOPE, List.of(new SigningGrants.Grant("issuer", Set.of(SigningPurpose.SIGNING))));
        grants.replace(KEY_URI, List.of());

        assertThrows(
                SigningAuthorizationException.class,
                () -> grants.require("issuer", KEY_URI, SigningPurpose.SIGNING));
    }

    @Test
    void versionPoliciesAreIsolated() {
        var grants = grants();
        grants.replace("software://" + KEY_SCOPE + "/1b9c6d22-0f44-4a77-bd21-9c0e3f5a7d88", List.of(new SigningGrants.Grant("issuer", Set.of(SigningPurpose.SIGNING))));
        grants.replace(KEY_URI, List.of(new SigningGrants.Grant(
                "issuer", Set.of(SigningPurpose.KEY_MANAGEMENT))));

        grants.require("issuer", "software://" + KEY_SCOPE + "/1b9c6d22-0f44-4a77-bd21-9c0e3f5a7d88",
                SigningPurpose.SIGNING);
        assertThrows(
                SigningAuthorizationException.class,
                () -> grants.require("issuer", KEY_URI, SigningPurpose.SIGNING));
    }

    /** Local development may explicitly allow an unknown scope. */
    @Test
    void allowsAnyClientOnAKeyWithoutGrants() {
        var development = new SigningGrants(new InMemorySigningGrantStore(), true);
        development.require("verifier", KEY_URI, SigningPurpose.SIGNING);
        development.requireAny("verifier", KEY_URI);
    }

    @Test
    void closesAKeyWithoutPolicyByDefault() {
        assertThrows(
                SigningAuthorizationException.class,
                () -> strictGrants().require("issuer", KEY_URI, SigningPurpose.SIGNING));
    }

    /**
     * Reading a key returns its public half, so it needs no purpose of its own - but a client with
     * no business with the key has no business enumerating it either.
     */
    @Test
    void readingNeedsVersionGrant() {
        var grants = grants();
        grants.replace(KEY_URI, List.of(new SigningGrants.Grant("issuer", Set.of(SigningPurpose.SIGNING))));

        grants.requireAny("issuer", KEY_URI);
        assertThrows(
                SigningKeyException.class,
                () -> grants.requireAny("verifier", KEY_URI));
    }

    @Test
    void derivesTheRequiredPurposeFromTheRequest() {
        assertEquals(SigningPurpose.SIGNING, SigningPurpose.required("POST", "/v1/signatures"));
        assertEquals(SigningPurpose.OPERATIONS, SigningPurpose.required("POST", "/v1/operations"));
        assertEquals(SigningPurpose.KEY_MANAGEMENT, SigningPurpose.required("POST", "/v1/keys"));
        assertEquals(SigningPurpose.KEY_MANAGEMENT, SigningPurpose.required("POST", "/v1/keys/import"));
        assertEquals(SigningPurpose.KEY_MANAGEMENT, SigningPurpose.required("DELETE", "/v1/keys"));
        assertEquals(SigningPurpose.KEY_MANAGEMENT, SigningPurpose.required("PUT", "/v1/keys/grants"));
        assertEquals(SigningPurpose.DECRYPT,
                SigningPurpose.required("POST", "/v1/keys/content-key"));
        assertEquals(SigningPurpose.KEY_MANAGEMENT,
                SigningPurpose.required("POST", "/v1/auth/clients"));
        assertEquals(SigningPurpose.KEY_MANAGEMENT,
                SigningPurpose.required("DELETE", "/v1/auth/clients"));
        assertNull(SigningPurpose.required("POST", "/v1/keysfoo"));
        assertNull(SigningPurpose.required("GET", "/v1/keys"));
    }

    @Test
    void acceptsAPlatformIntroducedClientOnlyWhenConfiguredToDoSo() {
        var introduced = publicKey(seed(), PROVIDER);
        var allowList = clients(Map.of());
        assertThrows(
                SigningKeyException.class, () -> allowList.add("issuer", introduced, "platform"));

        var assisted = new SigningClients(
                Map.of(), SigningClients.Acceptance.PLATFORM_ASSISTED, "platform");
        assisted.add("issuer", introduced, "platform");

        assertEquals(List.of("issuer"), assisted.names());
        assertEquals(java.util.Base64.getEncoder().encodeToString(introduced), assisted.publicKey("issuer"));
    }

    private static SigningClients clients(Map<String, byte[]> accepted) {
        return new SigningClients(accepted, SigningClients.Acceptance.ALLOW_LIST, "platform");
    }

    private static SigningGrants grants() {
        return new SigningGrants(new InMemorySigningGrantStore());
    }

    private static SigningGrants strictGrants() {
        return new SigningGrants(new InMemorySigningGrantStore());
    }

    private static RegisteredKeyAuthenticationService service(SigningClients clients) {
        return new RegisteredKeyAuthenticationService(
                PROVIDER,
                Duration.ofSeconds(60),
                new InMemoryRegisteredKeyAuthenticationStore(),
                clients);
    }
}
