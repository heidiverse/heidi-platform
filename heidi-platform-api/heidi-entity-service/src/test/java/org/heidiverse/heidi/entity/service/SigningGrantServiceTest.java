// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.heidiverse.heidi.shared.signing.SigningGrantWriter;
import org.heidiverse.heidi.shared.signing.SigningPurpose;
import org.junit.jupiter.api.Test;

/**
 * The platform decides who may use a key from the role that key plays in an identity. The verifier
 * signs request objects with the trust key and is never granted the credential key.
 */
class SigningGrantServiceTest {
    private static final String CREDENTIAL_KEY = "software://kc/0f7a/8d2b";
    private static final String TRUST_KEY = "software://kc/1b9c/4e10";

    private final RecordingWriter writer = new RecordingWriter();

    @Test
    void grantsTheCredentialKeyToTheIssuerAlone() {
        var grants = SigningGrantService.credentialKeyGrants();

        assertEquals(Set.of(SigningPurpose.SIGNING), grants.get(SigningGrantService.CLIENT_ISSUER));
        assertEquals(
                Set.of(SigningPurpose.KEY_MANAGEMENT),
                grants.get(SigningGrantService.CLIENT_PLATFORM));
        assertFalse(grants.containsKey(SigningGrantService.CLIENT_VERIFIER));
    }

    /** Both services state the identity: the issuer in its metadata, the verifier in its requests. */
    @Test
    void grantsTheTrustKeyToBothBackends() {
        var grants = SigningGrantService.trustKeyGrants();

        assertEquals(Set.of(SigningPurpose.SIGNING), grants.get(SigningGrantService.CLIENT_ISSUER));
        assertEquals(Set.of(SigningPurpose.SIGNING), grants.get(SigningGrantService.CLIENT_VERIFIER));
        assertTrue(grants.get(SigningGrantService.CLIENT_PLATFORM)
                .contains(SigningPurpose.KEY_MANAGEMENT));
    }

    @Test
    void grantsVerifierOperationsToTheVerifier() {
        var grants = SigningGrantService.verifierOperationGrants();

        assertEquals(Set.of(SigningPurpose.OPERATIONS),
                grants.get(SigningGrantService.CLIENT_VERIFIER));
        assertEquals(Set.of(SigningPurpose.KEY_MANAGEMENT),
                grants.get(SigningGrantService.CLIENT_PLATFORM));
        assertFalse(grants.containsKey(SigningGrantService.CLIENT_ISSUER));
    }

    @Test
    void writesGrantsAgainstTheKeyRatherThanTheKey() {
        new SigningGrantService((tenant, provider) -> writer)
                .publish("tenant", 1, CREDENTIAL_KEY, SigningGrantService.credentialKeyGrants());

        assertEquals(Set.of("kc/0f7a"), writer.written.keySet());
        assertEquals(
                Set.of(SigningPurpose.SIGNING),
                writer.written.get("kc/0f7a").get(SigningGrantService.CLIENT_ISSUER));
    }

    @Test
    void canWriteAnExactVersionScope() {
        new SigningGrantService((tenant, provider) -> writer)
                .publishScope("tenant", 1, CREDENTIAL_KEY,
                        SigningGrantService.credentialKeyGrants());

        assertTrue(writer.written.containsKey(CREDENTIAL_KEY));
    }

    @Test
    void doesNotRewriteAnUnchangedPolicy() {
        var service = new SigningGrantService((tenant, provider) -> writer);
        var policy = SigningGrantService.credentialKeyGrants();

        service.publishScope("tenant", 1, CREDENTIAL_KEY, policy);
        service.publishScope("tenant", 1, CREDENTIAL_KEY, policy);

        assertEquals(1, writer.attempts);
    }

    @Test
    void rewritesAChangedPolicy() {
        var service = new SigningGrantService((tenant, provider) -> writer);

        service.publishScope("tenant", 1, CREDENTIAL_KEY,
                SigningGrantService.credentialKeyGrants());
        service.publishScope("tenant", 1, CREDENTIAL_KEY,
                SigningGrantService.trustKeyGrants());

        assertEquals(2, writer.attempts);
        assertEquals(Set.of(SigningPurpose.SIGNING),
                writer.written.get(CREDENTIAL_KEY).get(SigningGrantService.CLIENT_VERIFIER));
    }

    @Test
    void writesWhenANewPolicyCannotBeRead() {
        var unreadable = new RecordingWriter() {
            @Override
            public Optional<Map<String, Set<SigningPurpose>>> grants(String scope) {
                throw new IllegalStateException("No policy exists");
            }
        };
        var service = new SigningGrantService((tenant, provider) -> unreadable);

        service.publishScope("tenant", 1, CREDENTIAL_KEY,
                SigningGrantService.credentialKeyGrants());

        assertEquals(1, unreadable.attempts);
        assertFalse(service.hasPending());
    }

    @Test
    void staysSilentWhereThereIsNoProviderOrKey() {
        var service = new SigningGrantService((tenant, provider) -> writer);

        service.publish("tenant", null, CREDENTIAL_KEY, SigningGrantService.credentialKeyGrants());
        service.publish("tenant", 1, "", SigningGrantService.credentialKeyGrants());

        assertTrue(writer.written.isEmpty());
    }

    @Test
    void writesAnEmptyPolicyToRevokeAccess() {
        new SigningGrantService((tenant, provider) -> writer)
                .publish("tenant", 1, TRUST_KEY, Map.of());

        assertTrue(writer.written.containsKey("kc/1b9c"));
        assertTrue(writer.written.get("kc/1b9c").isEmpty());
    }

    @Test
    void retriesAFailedGrantWrite() {
        var flaky = new FlakyWriter();
        var service = new SigningGrantService((tenant, provider) -> flaky);

        service.publish("tenant", 1, CREDENTIAL_KEY, SigningGrantService.credentialKeyGrants());
        assertEquals(1, flaky.attempts);
        assertTrue(flaky.written.isEmpty());
        assertTrue(service.hasPending());

        service.retryPending();

        assertEquals(2, flaky.attempts);
        assertEquals(Set.of(SigningPurpose.SIGNING),
                flaky.written.get("kc/0f7a").get(SigningGrantService.CLIENT_ISSUER));
        assertFalse(service.hasPending());
    }

    /** A backend that cannot hold grants must not break saving an identity. */
    @Test
    void toleratesAProviderThatDoesNotSupportGrants() {
        new SigningGrantService((tenant, provider) -> null)
                .publish("tenant", 1, CREDENTIAL_KEY, SigningGrantService.credentialKeyGrants());

        assertTrue(writer.written.isEmpty());
    }

    @Test
    void reportsConfirmedAndPendingPolicies() {
        var service = new SigningGrantService((tenant, provider) -> writer);
        service.publishScope("tenant", 1, CREDENTIAL_KEY,
                SigningGrantService.credentialKeyGrants());

        var confirmed = service.status("tenant", 1, CREDENTIAL_KEY);
        assertEquals(writer.written.get(CREDENTIAL_KEY), confirmed.confirmed());
        assertFalse(confirmed.pending());

        var flaky = new FlakyWriter();
        service = new SigningGrantService((tenant, provider) -> flaky);
        service.publishScope("tenant", 1, CREDENTIAL_KEY,
                SigningGrantService.credentialKeyGrants());

        var pending = service.status("tenant", 1, CREDENTIAL_KEY);
        assertEquals(null, pending.confirmed());
        assertTrue(pending.pending());
    }

    private static class RecordingWriter implements SigningGrantWriter {
        private final Map<String, Map<String, Set<SigningPurpose>>> written = new HashMap<>();
        int attempts;

        @Override
        public void replaceGrants(String key, Map<String, Set<SigningPurpose>> grants) {
            attempts++;
            written.put(key, grants);
        }

        @Override
        public Optional<Map<String, Set<SigningPurpose>>> grants(String scope) {
            return Optional.ofNullable(written.get(scope));
        }
    }

    private static final class FlakyWriter implements SigningGrantWriter {
        private final Map<String, Map<String, Set<SigningPurpose>>> written = new HashMap<>();
        private int attempts;

        @Override
        public void replaceGrants(String key, Map<String, Set<SigningPurpose>> grants) {
            attempts++;
            if (attempts == 1) throw new IllegalStateException("provider unavailable");
            written.put(key, grants);
        }
    }
}
