// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nimbusds.jose.jwk.JWK;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class DidUtilTest {

    private static final String DID_KEY_ID =
            "did:tdw:QmPEZPhDFR4nEYSFK5bMnvECqdpf1tPTPJuWs9QrMjCumw:"
                    + "identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:9a5559f0-b81c-4368-a170-e7b4ae424527"
                    + "#assert-key-f4bc032f-bfe0-4bab-acb3-bc3ef7fb3400";

    private static final String WEBVH_DID_KEY_ID =
            "did:webvh:QmdVPcfEJgvQAJKEjaTWAhskT1kc59KZQiXNenqHBB7iH5:"
                    + "identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:4c131dc4-ced1-454b-bbd4-9401c7512e37"
                    + "#trust-statement-issuer-int-prod-assertion-01";

    @Test
    void testTransformDidToHttpsUrl_withDomainOnly() throws IOException {
        String did = "did:tdw:Qm123abc:example.com";
        String expected = "https://example.com/.well-known/did.jsonl";
        String actual = DidUtil.transformDidToHttpsUrl(did);
        assertEquals(expected, actual);
    }

    @Test
    void testTransformDidToHttpsUrl_withPath() throws IOException {
        String did = "did:webvh:Qm123abc:example.com:dids:issuer";
        String expected = "https://example.com/dids/issuer/did.jsonl";
        String actual = DidUtil.transformDidToHttpsUrl(did);
        assertEquals(expected, actual);
    }

    @Test
    void testTransformDidToHttpsUrl_invalidScheme() {
        String invalidDid = "did:key:abc123";
        assertThrows(IOException.class, () -> DidUtil.transformDidToHttpsUrl(invalidDid));
    }

    @Test
    void testTransformDidToHttpsUrl_forValidTdwKid() throws Exception {
        // Given
        String kid =
                "did:tdw:QmPEZPhDFR4nEYSFK5bMnvECqdpf1tPTPJuWs9QrMjCumw:"
                        + "identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:9a5559f0-b81c-4368-a170-e7b4ae424527"
                        + "#assert-key-f4bc032f-bfe0-4bab-acb3-bc3ef7fb3400";

        String expectedUrl =
                "https://identifier-reg.trust-infra.swiyu-int.admin.ch/api/v1/did/9a5559f0-b81c-4368-a170-e7b4ae424527/did.jsonl";

        // When
        String did = kid.split("#")[0]; // Remove fragment
        String actualUrl = DidUtil.transformDidToHttpsUrl(did);

        // Then
        assertEquals(expectedUrl, actualUrl);
    }

    @Test
    void testResolveJwkFromDidKey_withRealJsonlFromSwiyuExample() throws Exception {
        String didJsonl = didLogFixture();

        try (MockedStatic<DidUtil> utilMock = mockStatic(DidUtil.class, CALLS_REAL_METHODS)) {
            utilMock.when(() -> DidUtil.fetchDidJsonl(anyString())).thenReturn(didJsonl);

            JWK jwk = DidUtil.resolveJwkFromDidKey(DID_KEY_ID);

            assertNotNull(jwk);
            assertEquals("EC", jwk.getKeyType().getValue());
            assertEquals("P-256", jwk.toECKey().getCurve().getName());
            assertEquals(
                    "zDTIeVtBZGBFH7l5k_soE79FEgeqtB9cJZc_jjwE1vc", jwk.toECKey().getX().toString());
            assertEquals(
                    "eTGNVxoW54gEHPrFTpOg2zNFLWcxLHs9vi85sXxMhXA", jwk.toECKey().getY().toString());
        }
    }

    @Test
    void testResolveJwkFromDidKey_rejectsTamperedDidLog() throws Exception {
        String tamperedDidJsonl =
                didLogFixture()
                        .replace(
                                "\"x\":\"zDTIeVtBZGBFH7l5k_soE79FEgeqtB9cJZc_jjwE1vc\"",
                                "\"x\":\"ADTIeVtBZGBFH7l5k_soE79FEgeqtB9cJZc_jjwE1vc\"");

        try (MockedStatic<DidUtil> utilMock = mockStatic(DidUtil.class, CALLS_REAL_METHODS)) {
            utilMock.when(() -> DidUtil.fetchDidJsonl(anyString())).thenReturn(tamperedDidJsonl);

            IOException exception =
                    assertThrows(IOException.class, () -> DidUtil.resolveJwkFromDidKey(DID_KEY_ID));

            assertTrue(exception.getMessage().startsWith("Failed to verify DID document:"));
        }
    }

    @Test
    void testResolveJwkFromDidKey_withWebVhJsonl() throws Exception {
        String didJsonl = webVhDidLogFixture();

        try (MockedStatic<DidUtil> utilMock = mockStatic(DidUtil.class, CALLS_REAL_METHODS)) {
            utilMock.when(() -> DidUtil.fetchDidJsonl(anyString())).thenReturn(didJsonl);

            JWK jwk = DidUtil.resolveJwkFromDidKey(WEBVH_DID_KEY_ID);

            assertNotNull(jwk);
            assertEquals(
                    "dlS2GpFqIP-BKLjbtzikmVGyfkpe63NNmDB4Ro3WpIs",
                    jwk.toECKey().getX().toString());
            assertEquals(
                    "XKOaPPxaGrx7Vu3ilhkB6CTLDB6RY9TRhJ-YaCEcy_4",
                    jwk.toECKey().getY().toString());
        }
    }

    private static String didLogFixture() throws IOException {
        try (var stream =
                Objects.requireNonNull(DidUtilTest.class.getResourceAsStream("/did/tdw03.jsonl"))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String webVhDidLogFixture() throws IOException {
        try (var stream = Objects.requireNonNull(
                DidUtilTest.class.getResourceAsStream("/did/webvh10.jsonl"))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
