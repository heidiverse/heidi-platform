// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.adapters;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import java.util.List;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.heidiverse.heidi.shared.signing.ProviderHealth;
import org.heidiverse.heidi.shared.signing.SigningKeyCapabilities;
import org.heidiverse.heidi.shared.signing.SigningKeyException;
import org.heidiverse.heidi.shared.signing.SigningKeyProvider;
import org.heidiverse.heidi.shared.signing.SigningKeyRef;
import org.heidiverse.heidi.shared.signing.SigningOperationRequest;
import uniffi.heidi_signing.Heidi_signing_jvmKt;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;

class ProviderAdaptersTest {
    private static final String BBS_OPERATION =
            "w3c.bbs-data-integrity-credential-issuance";
    private static final String BBS_PRESENTATION_SETUP_OPERATION =
            "w3c.bbs-data-integrity-presentation-setup";

    @Test
    void discoversTheSchemeFromCapabilitiesWhenNoExpectedSchemeIsConfigured() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> {
            if (!"http://provider.example/v1/capabilities".equals(request.getURI().toString())) {
                throw new AssertionError("Unexpected provider URI: " + request.getURI());
            }
        }).andRespond(MockRestResponseCreators.withSuccess(
                "{\"scheme\":\"pkcs11\",\"supportedAlgorithms\":[\"ES256\"],"
                        + "\"digestSigningAlgorithms\":[],\"canCreate\":false,"
                        + "\"canImport\":false,\"canDelete\":false}",
                MediaType.APPLICATION_JSON));

        var provider = new RemoteSigningKeyProvider(
                builder, new tools.jackson.databind.ObjectMapper(), URI.create("http://provider.example"),
                RemoteSigningKeyProvider.Authentication.none());

        assertEquals("pkcs11", provider.scheme());
        assertEquals(List.of("ES256"), provider.supportedAlgorithms());
        assertEquals(false, SigningKeyCapabilities.of(provider).canCreate());
        assertThrows(SigningKeyException.class, () -> provider.createKey("new", "ES256"));
        server.verify();
    }

    @Test
    void explainsWhichProviderFailedCapabilityDiscovery() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertEquals("/v1/capabilities", request.getURI().getPath()))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        var provider = new RemoteSigningKeyProvider(
                builder, URI.create("http://provider.example"),
                RemoteSigningKeyProvider.Authentication.none());

        var exception = assertThrows(SigningKeyException.class, provider::capabilities);

        assertEquals(
                "Could not call /v1/capabilities at http://provider.example/v1/capabilities: "
                        + "signing provider returned HTTP 503 Service Unavailable",
                exception.getMessage());
        server.verify();
    }

    @Test
    void registeredClientSignsRequestsWithoutSharedSecret() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var psk = Heidi_signing_jvmKt.generatePsk("software");
        var encodedPsk = java.util.Base64.getEncoder().encodeToString(psk);

        server.expect(request -> {
            assertEquals("GET", request.getMethod().name());
            assertEquals("/v1/capabilities", request.getURI().getPath());
            assertEquals(null, request.getHeaders().getFirst("Signing-Registration-Bootstrap"));
        }).andRespond(MockRestResponseCreators.withSuccess(
                "{\"scheme\":\"software\",\"supportedAlgorithms\":[\"ES256\"],"
                        + "\"digestSigningAlgorithms\":[],\"canCreate\":false,"
                        + "\"canImport\":false,\"canDelete\":false}",
                MediaType.APPLICATION_JSON));
        server.expect(request -> {
            assertEquals("POST", request.getMethod().name());
            assertEquals("/v1/auth/registrations", request.getURI().getPath());
            assertEquals(null, request.getHeaders().getFirst("Signing-Registration-Bootstrap"));
        }).andRespond(MockRestResponseCreators.withSuccess(
                "{\"registrationId\":\"registration-1\",\"psk\":\"" + encodedPsk + "\"}",
                MediaType.APPLICATION_JSON));
        server.expect(request -> {
            assertEquals("POST", request.getMethod().name());
            assertEquals("/v1/auth/registrations/registration-1/key", request.getURI().getPath());
            assertEquals(null, request.getHeaders().getFirst("Signing-Registration-Bootstrap"));
        }).andRespond(MockRestResponseCreators.withStatus(HttpStatus.NO_CONTENT));
        server.expect(request -> {
            assertEquals("/v1/keys", request.getURI().getPath());
            assertEquals("uri=software://issuer-key", request.getURI().getQuery());
            assertRegisteredHeaders(request);
        }).andRespond(MockRestResponseCreators.withSuccess(
                "{\"uri\":\"software://issuer-key\",\"publicKeyDocument\":"
                        + "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"x\",\"y\":\"y\"},"
                        + "\"algorithm\":\"ES256\"}",
                MediaType.APPLICATION_JSON));

        var provider = new RemoteSigningKeyProvider(
                builder,
                URI.create("http://provider.example"),
                RemoteSigningKeyProvider.Authentication.forClient("issuer", new byte[32]));

        assertEquals(List.of("ES256"), provider.supportedAlgorithms());
        assertEquals("ES256", provider.resolve("software://issuer-key").algorithm());
        server.verify();
    }

    @Test
    void executesBbsOperationThroughTheProviderProtocol() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
                server.expect(request -> assertEquals("/v1/capabilities", request.getURI().getPath()))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"scheme\":\"next-gen\",\"supportedAlgorithms\":[\"BBS\"],"
                                + "\"digestSigningAlgorithms\":[],"
                                + "\"supportedOperations\":[\""
                                + BBS_OPERATION + "\"],"
                                + "\"canCreate\":true,"
                                + "\"canImport\":false,\"canDelete\":true}",
                        MediaType.APPLICATION_JSON));
        server.expect(request -> {
            assertEquals("/v1/keys", request.getURI().getPath());
            assertEquals("uri=next-gen://bbs-key", request.getURI().getQuery());
        }).andRespond(MockRestResponseCreators.withSuccess(
                "{\"uri\":\"next-gen://bbs-key\",\"publicKeyDocument\":"
                        + "{\"kty\":\"BBS\",\"crv\":\"BLS12-381-G2\",\"x\":\"public\"},"
                        + "\"algorithm\":\"BBS\"}",
                MediaType.APPLICATION_JSON));
        server.expect(request -> {
            assertEquals("/v1/operations", request.getURI().getPath());
            var body = ((org.springframework.mock.http.client.MockClientHttpRequest) request)
                    .getBodyAsString(StandardCharsets.UTF_8);
            assertTrue(body.contains("\"operation\":\""
                    + BBS_OPERATION + "\""));
            assertTrue(body.contains("\"keyUri\":\"next-gen://bbs-key\""));
            assertTrue(body.contains("\"issuerId\":\"did:example:issuer\""));
            assertFalse(body.contains("issuer_sk"));
            assertFalse(body.contains("secretKey"));
        }).andRespond(MockRestResponseCreators.withSuccess(
                "{\"operation\":\"" + BBS_OPERATION
                        + "\",\"status\":\"COMPLETED\","
                        + "\"result\":{\"credential\":\"encoded-bbs-credential\"}}",
                MediaType.APPLICATION_JSON));

        var provider = new RemoteSigningKeyProvider(
                builder, URI.create("http://provider.example"),
                RemoteSigningKeyProvider.Authentication.none());
        var ref = provider.resolve("next-gen://bbs-key");

        var result = provider.execute(ref, new SigningOperationRequest(
                BBS_OPERATION,
                null,
                "{\"algorithm\":\"BBS\",\"claims\":{\"name\":{\"@value\":\"Alice\"}},"
                        + "\"issuerId\":\"did:example:issuer\","
                        + "\"issuerKeyId\":\"did:example:issuer#key-1\","
                        + "\"credentialType\":\"ExampleCredential\","
                        + "\"deviceBinding\":{\"x\":\"x\",\"y\":\"y\"}}"));
        assertEquals("COMPLETED", result.status());
        assertEquals("encoded-bbs-credential",
                new tools.jackson.databind.ObjectMapper().readTree(result.resultJson())
                        .path("credential").asString());
        server.verify();
    }

    @Test
    void executesKeylessOperationThroughTheProviderProtocol() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertEquals("/v1/capabilities", request.getURI().getPath()))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"scheme\":\"next-gen\",\"supportedAlgorithms\":[\"BBS\"],"
                                + "\"digestSigningAlgorithms\":[],"
                                + "\"supportedOperations\":[\"" + BBS_PRESENTATION_SETUP_OPERATION + "\"],"
                                + "\"keylessOperations\":[\"" + BBS_PRESENTATION_SETUP_OPERATION + "\"],"
                                + "\"canCreate\":true,\"canImport\":false,\"canDelete\":true}",
                        MediaType.APPLICATION_JSON));
        server.expect(request -> {
            assertEquals("/v1/operations", request.getURI().getPath());
            var body = ((org.springframework.mock.http.client.MockClientHttpRequest) request)
                    .getBodyAsString(StandardCharsets.UTF_8);
            assertTrue(body.contains("\"operation\":\"" + BBS_PRESENTATION_SETUP_OPERATION + "\""));
            assertTrue(body.contains("\"requirements\":[{\"type\":\"required\",\"key\":\"name\"}]"));
            assertFalse(body.contains("keyUri"));
        }).andRespond(MockRestResponseCreators.withSuccess(
                "{\"operation\":\"" + BBS_PRESENTATION_SETUP_OPERATION
                        + "\",\"status\":\"COMPLETED\","
                        + "\"result\":{\"provingKeys\":{},\"verifyingKeys\":{}}}",
                MediaType.APPLICATION_JSON));

        var provider = new RemoteSigningKeyProvider(
                builder, URI.create("http://provider.example"),
                RemoteSigningKeyProvider.Authentication.none());

        var result = provider.executeKeyless(new SigningOperationRequest(
                BBS_PRESENTATION_SETUP_OPERATION,
                null,
                "{\"requirements\":[{\"type\":\"required\",\"key\":\"name\"}]}"));

        assertEquals("COMPLETED", result.status());
        server.verify();
    }

    @Test
    void canonicalRegisteredRequestsIncludeRawQuery() {
        var first = RemoteSigningKeyProvider.canonicalRequest(
                "DELETE", URI.create("https://provider.example/v1/keys?uri=local%3A%2F%2Fa"), new byte[0]);
        var second = RemoteSigningKeyProvider.canonicalRequest(
                "DELETE", URI.create("https://provider.example/v1/keys?uri=local%3A%2F%2Fb"), new byte[0]);
        org.junit.jupiter.api.Assertions.assertFalse(java.util.Arrays.equals(first, second));
    }

    private static void assertRegisteredHeaders(org.springframework.http.client.ClientHttpRequest request) {
        org.junit.jupiter.api.Assertions.assertNotNull(
                request.getHeaders().getFirst("Signing-Authentication"));
        org.junit.jupiter.api.Assertions.assertNotNull(
                request.getHeaders().getFirst("Signing-Authentication-Timestamp"));
        org.junit.jupiter.api.Assertions.assertNotNull(
                request.getHeaders().getFirst("Signing-Authentication-Purpose"));
        org.junit.jupiter.api.Assertions.assertNotNull(
                request.getHeaders().getFirst("Signing-Authentication-Signature"));
    }

    @Test
    void contentSignerConvertsEcdsaJoseEncodingToDer() throws Exception {
        var raw = new byte[64];
        raw[31] = 7;
        raw[63] = 9;
        var signer = new ProviderContentSigner(
                provider(raw, "ES256"),
                ref("ES256"),
                "SHA256withECDSA",
                "ES256");
        signer.getOutputStream().write(new byte[] {1, 2, 3});

        var sequence = ASN1Sequence.getInstance(signer.getSignature());
        assertEquals(2, sequence.size());
        assertEquals(7, ASN1Integer.getInstance(sequence.getObjectAt(0)).getValue().intValue());
        assertEquals(9, ASN1Integer.getInstance(sequence.getObjectAt(1)).getValue().intValue());
    }

    @Test
    void nimbusSignerUsesTheKeyAlgorithmAndRawProviderOutput() throws Exception {
        var signature = new byte[] {4, 5, 6};
        var signer = new ProviderJwsSigner(provider(signature, "EdDSA"), ref("EdDSA"));
        var encoded = signer.sign(new JWSHeader(JWSAlgorithm.EdDSA), new byte[] {1, 2});

        assertArrayEquals(signature, encoded.decode());
        assertThrows(
                JOSEException.class,
                () -> signer.sign(new JWSHeader(JWSAlgorithm.RS256), new byte[] {1}));
    }

    private static SigningKeyProvider provider(byte[] signature, String algorithm) {
        return new SigningKeyProvider() {
            @Override public String scheme() { return "software"; }
            @Override public List<String> supportedAlgorithms() { return List.of(algorithm); }
            @Override public SigningKeyRef resolve(String uri) { return ref(algorithm); }
            @Override public byte[] sign(SigningKeyRef ref, byte[] message) { return signature.clone(); }
            @Override public ProviderHealth health() { return ProviderHealth.up(0); }
        };
    }

    private static SigningKeyRef ref(String algorithm) {
        return new SigningKeyRef("software://adapter-key", "{}", algorithm);
    }
}
