// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import uniffi.heidi_signing.Heidi_signing_jvmKt;

/**
 * A backend registers as itself: it names its client and presents the public key derived from its
 * own seed, so no credential of the platform's travels to reach the signing service.
 */
class SigningClientRegistrationTest {
    private static final String CAPABILITIES =
            "{\"scheme\":\"software\",\"supportedAlgorithms\":[\"ES256\"],"
                    + "\"digestSigningAlgorithms\":[],\"canCreate\":false,"
                    + "\"canImport\":false,\"canDelete\":false,"
                    + "\"clientAcceptance\":\"allow-list\"}";
    private static final String KEY =
            "{\"uri\":\"software://kc/9f1c/active\",\"publicKeyDocument\":"
                    + "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"x\",\"y\":\"y\"},"
                    + "\"algorithm\":\"ES256\"}";

    private final byte[] seed = Heidi_signing_jvmKt.generateSeed();

    @Test
    void registersUnderItsClientNameWithTheKeyDerivedFromItsSeed() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var psk = Heidi_signing_jvmKt.generatePsk("software");
        var body = new StringBuilder();

        server.expect(request -> {})
                .andRespond(MockRestResponseCreators.withSuccess(CAPABILITIES, MediaType.APPLICATION_JSON));
        server.expect(request -> {
            assertEquals("/v1/auth/registrations", request.getURI().getPath());
            body.append(((org.springframework.mock.http.client.MockClientHttpRequest) request)
                    .getBodyAsString());
        }).andRespond(MockRestResponseCreators.withSuccess(
                "{\"registrationId\":\"registration-1\",\"psk\":\""
                        + Base64.getEncoder().encodeToString(psk) + "\"}",
                MediaType.APPLICATION_JSON));
        server.expect(request -> assertEquals(
                        "/v1/auth/registrations/registration-1/key", request.getURI().getPath()))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.NO_CONTENT));
        server.expect(request -> {})
                .andRespond(MockRestResponseCreators.withSuccess(KEY, MediaType.APPLICATION_JSON));

        provider(builder).resolve("software://kc/9f1c/active");

        var expectedKey = Base64.getEncoder().encodeToString(
                Heidi_signing_jvmKt.publicKey(seed, null, "software"));
        assertTrue(body.toString().contains("\"client\":\"issuer\""), body.toString());
        assertTrue(body.toString().contains(expectedKey), body.toString());
        server.verify();
    }

    /** The purpose is part of the proof, so a signing credential cannot be reused to manage keys. */
    @Test
    void namesTheOperationClassOfEachRequest() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var psk = Heidi_signing_jvmKt.generatePsk("software");
        var purposes = new StringBuilder();

        server.expect(request -> {})
                .andRespond(MockRestResponseCreators.withSuccess(CAPABILITIES, MediaType.APPLICATION_JSON));
        server.expect(request -> {}).andRespond(MockRestResponseCreators.withSuccess(
                "{\"registrationId\":\"registration-1\",\"psk\":\""
                        + Base64.getEncoder().encodeToString(psk) + "\"}",
                MediaType.APPLICATION_JSON));
        server.expect(request -> {})
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.NO_CONTENT));
        server.expect(request -> purposes.append(
                        request.getHeaders().getFirst("Signing-Authentication-Purpose")))
                .andRespond(MockRestResponseCreators.withSuccess(KEY, MediaType.APPLICATION_JSON));
        server.expect(request -> purposes.append(" ").append(
                        request.getHeaders().getFirst("Signing-Authentication-Purpose")))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"signature\":\"" + Base64.getEncoder().encodeToString(new byte[64])
                                + "\",\"algorithm\":\"ES256\"}",
                        MediaType.APPLICATION_JSON));

        var provider = provider(builder);
        var ref = provider.resolve("software://kc/9f1c/active");
        provider.sign(ref, "payload".getBytes(StandardCharsets.UTF_8));

        // Reading a key describes it rather than using it; signing says so.
        assertEquals("read signing", purposes.toString());
        server.verify();
    }

    @Test
    void derivesADifferentKeyPerSigningServiceFromOneSeed() throws Exception {
        var forSoftware = Heidi_signing_jvmKt.publicKey(seed, null, "software");
        var forHsm = Heidi_signing_jvmKt.publicKey(seed, null, "pkcs11");

        assertNotEquals(
                Base64.getEncoder().encodeToString(forSoftware),
                Base64.getEncoder().encodeToString(forHsm));
    }

    private RemoteSigningKeyProvider provider(RestClient.Builder builder) {
        return new RemoteSigningKeyProvider(
                builder,
                URI.create("http://provider.example"),
                RemoteSigningKeyProvider.Authentication.forClient("issuer", seed));
    }
}
