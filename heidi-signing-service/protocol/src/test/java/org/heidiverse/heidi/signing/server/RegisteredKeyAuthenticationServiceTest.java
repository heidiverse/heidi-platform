// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import static org.heidiverse.heidi.signing.server.SigningProtocolTestSupport.auth;
import static org.heidiverse.heidi.signing.server.SigningProtocolTestSupport.publicKey;
import static org.heidiverse.heidi.signing.server.SigningProtocolTestSupport.seed;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.heidiverse.heidi.shared.signing.SigningKeyException;
import org.junit.jupiter.api.Test;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;

class RegisteredKeyAuthenticationServiceTest {
    @Test
    void verifiesTheNormativeRegistrationAndRequestConstructionAndRejectsReplay() {
        var seed = seed();
        var publicKey = publicKey(seed, "software");
        var clients = new SigningClients(
                java.util.Map.of("issuer", publicKey),
                SigningClients.Acceptance.ALLOW_LIST,
                "platform");
        var service = new RegisteredKeyAuthenticationService(
                "software", Duration.ofSeconds(60),
                new InMemoryRegisteredKeyAuthenticationStore(), clients);
        var registration = service.open("software", "issuer", publicKey);
        service.registerPublicKey(registration.id(), publicKey, auth(seed, registration.psk(), null, publicKey));

        var timestamp = Long.toString(System.currentTimeMillis());
        var canonicalRequest = "POST\n/v1/signatures\n{}".getBytes(StandardCharsets.UTF_8);
        var signature = auth(
                seed,
                registration.psk(),
                "key-1",
                (timestamp + ":signing:" + SigningProtocolTestSupport.hash(canonicalRequest))
                        .getBytes(StandardCharsets.UTF_8));

        service.authenticate(
                registration.id(), "key-1", timestamp, "signing", canonicalRequest, signature);
        assertThrows(
                SigningKeyException.class,
                () -> service.authenticate(
                        registration.id(), "key-1", timestamp, "signing", canonicalRequest, signature));
    }

    @Test
    void canonicalRequestBindsTheRawQuery() {
        var first = new MockHttpServletRequest("DELETE", "/v1/keys");
        first.setQueryString("uri=local%3A%2F%2Fkey-a");
        var second = new MockHttpServletRequest("DELETE", "/v1/keys");
        second.setQueryString("uri=local%3A%2F%2Fkey-b");

        var firstBytes = SigningSecurityConfiguration.SigningAuthenticationFilter
                .canonicalRequest(first, "/v1/keys", new byte[0]);
        var secondBytes = SigningSecurityConfiguration.SigningAuthenticationFilter
                .canonicalRequest(second, "/v1/keys", new byte[0]);

        org.junit.jupiter.api.Assertions.assertFalse(
                java.util.Arrays.equals(firstBytes, secondBytes));
        org.junit.jupiter.api.Assertions.assertEquals(
                "DELETE\n/v1/keys?uri=local%3A%2F%2Fkey-a\n",
                new String(firstBytes, StandardCharsets.UTF_8));
    }

    @Test
    void permitsPublicCapabilityDiscoveryBeforeRegistration() throws Exception {
        var filter = new SigningSecurityConfiguration.SigningAuthenticationFilter(
                "registered", "",
                new RegisteredKeyAuthenticationService("software", Duration.ofSeconds(60)));
        var request = new MockHttpServletRequest("GET", "/v1/capabilities");
        var response = new MockHttpServletResponse();
        var continued = new AtomicBoolean();
        FilterChain chain = (requestToContinue, responseToContinue) -> continued.set(true);

        filter.doFilter(request, response, chain);

        org.junit.jupiter.api.Assertions.assertTrue(continued.get());
        org.junit.jupiter.api.Assertions.assertEquals(200, response.getStatus());
    }

    @Test
    void permitsRegisteredDiscoveryBeforeRegistration() throws Exception {
        var filter = new SigningSecurityConfiguration.SigningAuthenticationFilter(
                "registered", "", "issuer",
                new RegisteredKeyAuthenticationService("software", Duration.ofSeconds(60)));
        var request = new MockHttpServletRequest("GET", "/v1/capabilities");
        var response = new MockHttpServletResponse();
        var continued = new AtomicBoolean();
        FilterChain chain = (requestToContinue, responseToContinue) -> continued.set(true);

        filter.doFilter(request, response, chain);

        org.junit.jupiter.api.Assertions.assertTrue(continued.get());
        org.junit.jupiter.api.Assertions.assertEquals(200, response.getStatus());
    }

    @Test
    void keepsRegistrationTransportProtectedOutsideRegisteredMode() throws Exception {
        var filter = new SigningSecurityConfiguration.SigningAuthenticationFilter(
                "bearer", "secret", "platform",
                new RegisteredKeyAuthenticationService("software", Duration.ofSeconds(60)));
        var request = new MockHttpServletRequest("POST", "/v1/auth/registrations");
        var response = new MockHttpServletResponse();
        var continued = new AtomicBoolean();
        FilterChain chain = (requestToContinue, responseToContinue) -> continued.set(true);

        filter.doFilter(request, response, chain);

        org.junit.jupiter.api.Assertions.assertFalse(continued.get());
        org.junit.jupiter.api.Assertions.assertEquals(401, response.getStatus());
    }

    @Test
    void bindsTransportClientToTheRegistrationBody() throws Exception {
        var issuerKey = publicKey(seed(), "software");
        var clients = new SigningClients(
                java.util.Map.of(
                        "platform", publicKey(seed(), "software"),
                        "issuer", issuerKey),
                SigningClients.Acceptance.ALLOW_LIST,
                "platform");
        var service = new RegisteredKeyAuthenticationService(
                "software",
                Duration.ofSeconds(60),
                new InMemoryRegisteredKeyAuthenticationStore(),
                clients);
        var controller = new SigningProtocolController(null, null, service, clients, null);
        var filter = new SigningSecurityConfiguration.SigningAuthenticationFilter(
                "bearer", "secret", "platform", service);
        var request = new MockHttpServletRequest("POST", "/v1/auth/registrations");
        request.addHeader("Authorization", "Bearer secret");
        FilterChain chain = (requestToContinue, responseToContinue) ->
                controller.openRegistration(
                        (MockHttpServletRequest) requestToContinue,
                        new SigningProtocolModels.OpenRegistrationRequest(
                                "software", "issuer", issuerKey));

        assertThrows(
                SigningAuthorizationException.class,
                () -> filter.doFilter(request, new MockHttpServletResponse(), chain));
    }

    @Test
    void completionIsIdempotentForTheSameKey() {
        var seed = seed();
        var publicKey = publicKey(seed, "software");
        var clients = new SigningClients(
                java.util.Map.of("issuer", publicKey),
                SigningClients.Acceptance.ALLOW_LIST,
                "platform");
        var service = new RegisteredKeyAuthenticationService(
                "software", Duration.ofSeconds(60),
                new InMemoryRegisteredKeyAuthenticationStore(), clients);
        var registration = service.open("software", "issuer", publicKey);
        var proof = auth(seed, registration.psk(), null, publicKey);

        service.registerPublicKey(registration.id(), publicKey, proof);
        assertDoesNotThrow(() -> service.registerPublicKey(registration.id(), publicKey, proof));
    }

    @Test
    void recordsLastUseForTheAuthenticatedClient() {
        var clientSeed = seed();
        var clientKey = publicKey(clientSeed, "software");
        var clients = new SigningClients(
                java.util.Map.of("issuer", clientKey),
                SigningClients.Acceptance.ALLOW_LIST,
                "issuer");
        var service = new RegisteredKeyAuthenticationService(
                "software", Duration.ofSeconds(60),
                new InMemoryRegisteredKeyAuthenticationStore(), clients);
        var registration = service.open("software", "issuer", clientKey);
        service.registerPublicKey(
                registration.id(), clientKey,
                auth(clientSeed, registration.psk(), null, clientKey));
        var timestamp = Long.toString(System.currentTimeMillis());
        var request = "GET\n/v1/keys?uri=local%3A%2F%2Fkey\n".getBytes(StandardCharsets.UTF_8);
        var signature = auth(
                clientSeed, registration.psk(), null,
                (timestamp + ":read:" + SigningProtocolTestSupport.hash(request))
                        .getBytes(StandardCharsets.UTF_8));

        service.authenticate(registration.id(), null, timestamp, "read", request, signature);

        org.junit.jupiter.api.Assertions.assertNotNull(service.lastUse("issuer"));
    }

    @Test
    void registrationRejectsUnnamedClients() {
        var service = new RegisteredKeyAuthenticationService(
                "software", Duration.ofSeconds(60), new InMemoryRegisteredKeyAuthenticationStore(),
                new SigningClients(
                        java.util.Map.of(), SigningClients.Acceptance.PLATFORM_ASSISTED, "platform"));

        assertThrows(
                SigningAuthorizationException.class,
                () -> service.open("software", null, null));
    }
}
