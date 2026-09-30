// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.heidiverse.heidi.shared.signing.ProviderHealth;
import org.heidiverse.heidi.shared.signing.SigningContentKeyProvider;
import org.heidiverse.heidi.shared.signing.SigningContentKeyRequest;
import org.heidiverse.heidi.shared.signing.SigningKeyProvider;
import org.heidiverse.heidi.shared.signing.SigningKeyRef;
import org.heidiverse.heidi.shared.signing.SigningPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import tools.jackson.databind.ObjectMapper;

class SigningProtocolControllerTest {
    private static final String KEY_URI = "test://kc/00000000-0000-0000-0000-000000000001/key";

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {" "})
    void revokeRequiresKeyUri(String uri) {
        var controller = new SigningProtocolController(new ContentProvider(), new ObjectMapper(), null,
                new SigningClients(Map.of("platform", new byte[] {1}),
                        SigningClients.Acceptance.ALLOW_LIST, "platform"),
                new SigningGrants(new InMemorySigningGrantStore()));
        var platform = new MockHttpServletRequest();
        SigningClientContext.set(platform, "platform");

        assertThrows(SigningProtocolException.class,
                () -> controller.revoke(platform, new SigningProtocolModels.RevokeKeyRequest(uri)));
    }

    @Test
    void revocationSurvivesGrantRetry() {
        var grants = new SigningGrants(new InMemorySigningGrantStore());
        var policy = List.of(
                new SigningGrants.Grant("platform", Set.of(SigningPurpose.KEY_MANAGEMENT)),
                new SigningGrants.Grant("issuer", Set.of(SigningPurpose.SIGNING, SigningPurpose.DECRYPT)));
        grants.replace(KEY_URI, policy);
        var controller = new SigningProtocolController(new ContentProvider(), new ObjectMapper(), null,
                new SigningClients(Map.of("platform", new byte[] {1}, "issuer", new byte[] {2}),
                        SigningClients.Acceptance.ALLOW_LIST, "platform"), grants);
        var platform = new MockHttpServletRequest();
        SigningClientContext.set(platform, "platform");
        var issuer = new MockHttpServletRequest();
        SigningClientContext.set(issuer, "issuer");
        var revoke = new SigningProtocolModels.RevokeKeyRequest(KEY_URI);
        var csr = new SigningProtocolModels.CsrRequest(KEY_URI,
                new org.heidiverse.heidi.shared.signing.SigningCsrRequest("CN=Issuer", List.of(), List.of()));

        assertThrows(SigningAuthorizationException.class, () -> controller.revoke(issuer, revoke));
        assertThrows(SigningAuthorizationException.class, () -> controller.createCsr(issuer, csr));
        controller.revoke(platform, revoke);
        controller.revoke(platform, revoke);
        // A delayed policy write must never restore private-key authority.
        grants.replace(KEY_URI, policy);
        assertThrows(SigningAuthorizationException.class, () -> controller.createCsr(platform, csr));

        assertThrows(SigningAuthorizationException.class, () -> controller.sign(issuer,
                new SigningProtocolModels.SignRequest(KEY_URI, "RS256", new byte[] {1}, null, null)));
        assertThrows(SigningAuthorizationException.class, () -> controller.contentKey(issuer,
                new SigningProtocolModels.ContentKeyRequest(KEY_URI, "RSA-OAEP-256", "A256GCM",
                        null, null, null, new byte[] {3})));
        assertDoesNotThrow(() -> grants.require("platform", KEY_URI, SigningPurpose.KEY_MANAGEMENT));
        assertDoesNotThrow(() -> grants.requireAny("platform", KEY_URI));
    }

    @Test
    void revocationOverridesFailOpen() {
        var grants = new SigningGrants(new InMemorySigningGrantStore(), true);
        grants.revoke(KEY_URI);
        var controller = new SigningProtocolController(new ContentProvider(), new ObjectMapper(),
                null, null, grants);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "failOpen", true);

        assertThrows(SigningAuthorizationException.class, () -> controller.sign(new MockHttpServletRequest(),
                new SigningProtocolModels.SignRequest(KEY_URI, "RS256", new byte[] {1}, null, null)));
    }

    @Test
    void allowsRsaContentKeyRequestsWithoutAnEphemeralKey() {
        var provider = new ContentProvider();
        var grants = new SigningGrants(new InMemorySigningGrantStore());
        grants.replace(
                KEY_URI,
                List.of(new SigningGrants.Grant("issuer", Set.of(SigningPurpose.DECRYPT))));
        var controller = new SigningProtocolController(
                provider,
                new ObjectMapper(),
                null,
                new SigningClients(
                        Map.of("issuer", new byte[] {1}),
                        SigningClients.Acceptance.ALLOW_LIST,
                        "issuer"),
                grants);
        var request = new MockHttpServletRequest();
        SigningClientContext.set(request, "issuer");

        var response = controller.contentKey(
                request,
                new SigningProtocolModels.ContentKeyRequest(
                        KEY_URI, "RSA-OAEP-256", "A256GCM", null,
                        null, null, new byte[] {3}));

        assertArrayEquals(new byte[] {9, 8, 7}, response.contentKey());
        org.junit.jupiter.api.Assertions.assertNull(provider.request.ephemeralPublicJwk());
    }

    @Test
    void requiresAnEphemeralKeyForEcdhContentKeyRequests() {
        var provider = new ContentProvider();
        var grants = new SigningGrants(new InMemorySigningGrantStore());
        grants.replace(
                KEY_URI,
                List.of(new SigningGrants.Grant("issuer", Set.of(SigningPurpose.DECRYPT))));
        var controller = new SigningProtocolController(
                provider, new ObjectMapper(), null,
                new SigningClients(
                        Map.of("issuer", new byte[] {1}),
                        SigningClients.Acceptance.ALLOW_LIST,
                        "issuer"),
                grants);
        var request = new MockHttpServletRequest();
        SigningClientContext.set(request, "issuer");

        assertThrows(
                SigningProtocolException.class,
                () -> controller.contentKey(
                        request,
                        new SigningProtocolModels.ContentKeyRequest(
                                KEY_URI, "ECDH-ES", "A256GCM", null,
                                null, null, null)));
    }

    private static final class ContentProvider
            implements SigningKeyProvider, SigningContentKeyProvider {
        private final SigningKeyRef ref =
                new SigningKeyRef(KEY_URI, "{\"kty\":\"RSA\"}", "RS256");
        private SigningContentKeyRequest request;

        @Override
        public String scheme() {
            return "test";
        }

        @Override
        public List<String> supportedAlgorithms() {
            return List.of("RS256");
        }

        @Override
        public SigningKeyRef resolve(String keyUri) {
            return ref;
        }

        @Override
        public byte[] sign(SigningKeyRef ref, byte[] message) {
            return new byte[] {1};
        }

        @Override
        public ProviderHealth health() {
            return ProviderHealth.up(0);
        }

        @Override
        public List<String> contentKeyAlgorithms() {
            return List.of("RSA-OAEP-256", "ECDH-ES");
        }

        @Override
        public byte[] contentKey(SigningKeyRef ref, SigningContentKeyRequest request) {
            this.request = request;
            return new byte[] {9, 8, 7};
        }
    }
}
