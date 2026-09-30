// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * What the Cockpit shows after a connection check: which clients the signing service knows, and
 * for the ones it does not, the public key an operator adds to it.
 */
class SigningClientStatusServiceTest {
    private static final String SCHEME = "local";

    @Test
    void showsTheKeyToAddForAClientTheServiceDoesNotKnow() {
        var service = new SigningClientStatusService(
                (scheme) -> Map.of("issuer", "issuer-key", "verifier", "verifier-key"));

        var status = service.describe(SCHEME, Map.of("platform", "platform-key"));

        assertEquals(List.of("issuer", "platform", "verifier"),
                status.stream().map(SigningClientStatusService.ClientStatus::name).sorted().toList());
        assertEquals("issuer-key", keyOf(status, "issuer"));
        assertNull(keyOf(status, "platform"));
        assertTrue(known(status, "platform"));
        assertTrue(!known(status, "issuer"));
    }

    /** A backend that publishes no key is simply not offered; nothing to paste, nothing to claim. */
    @Test
    void omitsAKeyForABackendWithoutASeed() {
        var service = new SigningClientStatusService((scheme) -> Map.of());

        var status = service.describe(SCHEME, Map.of("platform", "platform-key"));

        assertEquals(List.of("platform"),
                status.stream().map(SigningClientStatusService.ClientStatus::name).toList());
    }

    /** An unreachable backend must not fail the connection check that found the signing service. */
    @Test
    void survivesABackendThatCannotBeAsked() {
        var service = new SigningClientStatusService((scheme) -> {
            throw new IllegalStateException("verifier is down");
        });

        assertEquals(List.of("platform"),
                service.describe(SCHEME, Map.of("platform", "platform-key")).stream()
                        .map(SigningClientStatusService.ClientStatus::name).toList());
    }

    @Test
    void rejectsAnUnverifiedClientKey() {
        var service = new SigningClientStatusService(scheme -> Map.of("issuer", "current-key"));

        var status = service.describe(SCHEME, Map.of("issuer", "old-key"));

        assertTrue(!known(status, "issuer"));
        assertEquals("current-key", keyOf(status, "issuer"));
    }

    @Test
    void acceptsTheCurrentClientKey() {
        var service = new SigningClientStatusService(scheme -> Map.of("issuer", "current-key"));
        var status = service.describe(SCHEME, Map.of("issuer", "current-key"));
        assertTrue(known(status, "issuer"));
        assertNull(keyOf(status, "issuer"));
    }

    @Test
    void readsInternalBackendEndpointsWithServiceAuthentication() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "http://issuer.test/internal/issuer/v1/signing-client?provider=local"))
                .andExpect(header("Authorization", "Basic api-secret"))
                .andRespond(withSuccess(
                        "{\"client\":\"issuer\",\"publicKey\":\"issuer-key\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "http://verifier.test/internal/verifier/v1/signing-client?provider=local"))
                .andExpect(header("Authorization", "Basic api-secret"))
                .andRespond(withSuccess(
                        "{\"client\":\"verifier\",\"publicKey\":\"verifier-key\"}",
                        MediaType.APPLICATION_JSON));
        var service = new SigningClientStatusService(
                builder, "http://issuer.test", "http://verifier.test", "api-secret");

        var status = service.describe(SCHEME, Map.of());

        assertEquals("issuer-key", keyOf(status, "issuer"));
        assertEquals("verifier-key", keyOf(status, "verifier"));
        server.verify();
    }

    private static String keyOf(List<SigningClientStatusService.ClientStatus> status, String name) {
        return status.stream().filter(entry -> entry.name().equals(name))
                .findFirst().orElseThrow().publicKey();
    }

    private static boolean known(List<SigningClientStatusService.ClientStatus> status, String name) {
        return status.stream().filter(entry -> entry.name().equals(name))
                .findFirst().orElseThrow().known();
    }
}
