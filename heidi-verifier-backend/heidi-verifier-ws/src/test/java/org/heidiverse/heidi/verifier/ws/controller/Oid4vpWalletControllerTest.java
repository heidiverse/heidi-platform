// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.ws.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.heidiverse.heidi.shared.testing.TestCertificates;
import org.heidiverse.heidi.verifier.data.service.VerificationRequestService;
import org.heidiverse.heidi.verifier.model.api.wallet.SubmissionResponse;
import org.heidiverse.heidi.verifier.model.api.wallet.VerificationRequest;
import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.heidiverse.heidi.shared.trustframework.TrustConfiguration;
import org.heidiverse.heidi.verifier.model.vp.VerificationRequestData;
import org.heidiverse.heidi.verifier.service.ClientIdService;
import org.heidiverse.heidi.verifier.service.IdentitySigningService;
import org.heidiverse.heidi.verifier.service.Oid4vpWalletEndpointService;
import org.heidiverse.heidi.verifier.ws.advice.ExceptionHandling;
import org.heidiverse.heidi.verifier.ws.advice.WalletExceptionHandling;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.Payload;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import org.kapunsdk.presentation.request.model.OID4VPVersion;
import tools.jackson.databind.ObjectMapper;
import org.heidiverse.heidi.verifier.service.util.ResponseEncryptionSessionKeys;

import java.security.interfaces.ECPrivateKey;
import java.time.Instant;
import java.util.List;

class Oid4vpWalletControllerTest {

    private static final String REQUEST_ID = "request-id";

    @Test
    void swissPresentationUsesDidClientIdByDefault() throws Exception {
        final var did = "did:webvh:verifier.example";
        final var identitySigningService = mock(IdentitySigningService.class);
        final var keyPair = TestCertificates.ecKeyPair();
        when(identitySigningService.resolveSnapshot("original-snapshot"))
                .thenReturn(
                        new IdentitySigningService.ResolvedSigner(
                                TestCertificates.publicJwk(keyPair),
                                new ECDSASigner((ECPrivateKey) keyPair.getPrivate()),
                                did,
                                "verifier-key"));

        final var response =
                controller(
                                "decentralized_identifier:" + did,
                                "verifier",
                                "Switzerland",
                                null,
                                identitySigningService,
                                "SWISS_PRESENTATION_2026_1")
                        .fetchVerificationRequest(REQUEST_ID);

        final var jwt = (SignedJWT) JWTParser.parse(response.getBody());
        assertEquals("decentralized_identifier:" + did,
                jwt.getJWTClaimsSet().getStringClaim("client_id"));
        assertEquals(did + "#verifier-key", jwt.getHeader().getKeyID());
    }

    @Test
    void swissVqpsUsesScopeInsteadOfTopLevelDcql() throws Exception {
        final var did = "did:webvh:verifier.example";
        final var scope = "ch.example.identity";
        final var identitySigningService = mock(IdentitySigningService.class);
        final var keyPair = TestCertificates.ecKeyPair();
        when(identitySigningService.resolveSnapshot("original-snapshot"))
                .thenReturn(
                        new IdentitySigningService.ResolvedSigner(
                                TestCertificates.publicJwk(keyPair),
                                new ECDSASigner((ECPrivateKey) keyPair.getPrivate()),
                                did,
                                "verifier-key"));

        final var response =
                controller(
                                "decentralized_identifier:" + did,
                                "verifier",
                                "Switzerland",
                                null,
                                identitySigningService,
                                "SWISS_PRESENTATION_2026_1",
                                scope)
                        .fetchVerificationRequest(REQUEST_ID);

        final var claims = JWTParser.parse(response.getBody()).getJWTClaimsSet();
        assertEquals(scope, claims.getStringClaim("scope"));
        assertNull(claims.getClaim("dcql_query"));
    }

    @Test
    void renewalKeepsRequestCertificate() throws Exception {
        final var ca = TestCertificates.ca("Verifier Test CA");
        final var keyPair = TestCertificates.ecKeyPair();
        final var leaf = TestCertificates.leaf(
                ca, keyPair.getPublic(), "verifier.example", List.of("verifier.example"));
        final var publicJwk = new com.nimbusds.jose.jwk.ECKey.Builder(
                TestCertificates.publicJwk(keyPair))
                .x509CertChain(leaf.encodedChain().stream()
                        .map(com.nimbusds.jose.util.Base64::new)
                        .toList())
                .build();
        final var identitySigningService = mock(IdentitySigningService.class);
        when(identitySigningService.resolveSnapshot("original-snapshot"))
                .thenReturn(
                        new IdentitySigningService.ResolvedSigner(
                                publicJwk,
                                new ECDSASigner((ECPrivateKey) keyPair.getPrivate()),
                                null,
                                "verifier-key"));

        final var clientId =
                "x509_hash:" + IdentitySigningService.x509CertificateHash(publicJwk);
        when(identitySigningService.x509CertificateHash(
                        any(IdentitySigningService.ResolvedSigner.class)))
                .thenReturn(clientId.substring("x509_hash:".length()));
        final var response =
                controller(clientId, "verifier", "EUDI", null, identitySigningService,
                        "EUDI_PRESENTATION_2026_1")
                        .fetchVerificationRequest(REQUEST_ID);

        final var jwt = (SignedJWT) JWTParser.parse(response.getBody());
        assertEquals(clientId, jwt.getJWTClaimsSet().getStringClaim("client_id"));
        assertFalse(jwt.getHeader().getX509CertChain().isEmpty());
        verify(identitySigningService, org.mockito.Mockito.never()).resolve("verifier", "EUDI", null);
    }

    @Test
    void allowsMissingAgreementPartyInfo() throws Exception {
        final var sessionKeys = new ResponseEncryptionSessionKeys(new byte[32]);
        final var generated = sessionKeys.generate("state");
        final var endpointService = mock(Oid4vpWalletEndpointService.class);
        final var requestService = mock(VerificationRequestService.class);
        when(requestService.responseEncryptionKeys(any(Instant.class)))
                .thenReturn(List.of(new VerificationRequestService.ResponseEncryptionKey(
                        "state", generated.keyId(), generated.publicJwk(), generated.encryptedPrivateJwk())));
        when(endpointService.handleAuthorizationResponse(
                any(), eq("state"), eq((String) null), any(byte[].class)))
                .thenReturn(new SubmissionResponse(null));
        final var controller = new Oid4vpWalletController(
                new ObjectMapper(),
                null,
                endpointService,
                sessionKeys,
                requestService,
                null);
        final var encryptionJwk = com.nimbusds.jose.jwk.JWK.parse(generated.publicJwk()).toECKey();
        final var object = new JWEObject(
                new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM).build(),
                new Payload("{\"vp_token\":{},\"state\":\"state\"}"));
        object.encrypt(new ECDHEncrypter(encryptionJwk.toECPublicKey()));

        final var response = controller.submitAuthorizationResponseEncryptedJwt(object.serialize());

        assertEquals(new SubmissionResponse(null), response.getBody());
        verify(endpointService).handleAuthorizationResponse(
                any(), eq("state"), eq((String) null), any(byte[].class));
    }

    @Test
    void swissPresentationRequiresVerifierIdentity() {
        final var exception =
                assertThrows(
                        VpVerificationException.class,
                        () ->
                                controller(
                                                "decentralized_identifier:did:webvh:verifier.example",
                                                null,
                                                "Switzerland",
                                                null,
                                                null,
                                                "SWISS_PRESENTATION_2026_1")
                                        .fetchVerificationRequest(REQUEST_ID));

        assertEquals("decentralized_identifier requires a verifier identity",
                exception.getMessage());
    }

    @Test
    void eudiPresentationRequiresVerifierIdentity() {
        final var exception =
                assertThrows(
                        VpVerificationException.class,
                        ()
                                -> controller(
                                                "x509_hash:hash",
                                                null,
                                                "EUDI",
                                                null,
                                                null,
                                                "EUDI_PRESENTATION_2026_1")
                                        .fetchVerificationRequest(REQUEST_ID));

        assertEquals("x509_hash requires a verifier identity", exception.getMessage());
    }

    @Test
    void presentationProfileRejectsConflictingClientIdScheme() {
        final var exception = assertThrows(
                VpVerificationException.class,
                () -> controller(
                                "decentralized_identifier:did:webvh:verifier.example",
                                null,
                                null,
                                "decentralized_identifier",
                                null,
                                "EUDI_PRESENTATION_2026_1")
                        .fetchVerificationRequest(REQUEST_ID));

        assertEquals(
                "Client ID scheme 'decentralized_identifier' does not match presentation profile '"
                        + "EUDI_PRESENTATION_2026_1'",
                exception.getMessage());
    }

    @Test
    void walletEndpointReturnsOAuthErrorForUnhandledException() throws Exception {
        final var mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new Oid4vpWalletController(
                                        null,
                                        null,
                                        new FailingWalletEndpointService(),
                                        new ResponseEncryptionSessionKeys(new byte[32]),
                                        null,
                                        null))
                        .setControllerAdvice(new WalletExceptionHandling(), new ExceptionHandling())
                        .build();

        mockMvc.perform(get("/v1/wallet/par/request-id").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("server_error"))
                .andExpect(
                        jsonPath("$.error_description").value("Failed to process wallet request."))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    private static Oid4vpWalletController controller(
            String clientId,
            String signingIdentity,
            String signingTrustSystem,
            String clientIdScheme,
            IdentitySigningService identitySigningService) {
        return controller(
                clientId,
                signingIdentity,
                signingTrustSystem,
                clientIdScheme,
                identitySigningService,
                null,
                null);
    }

    private static Oid4vpWalletController controller(
            String clientId,
            String signingIdentity,
            String signingTrustSystem,
            String clientIdScheme,
            IdentitySigningService identitySigningService,
            String presentationProfileId) {
        return controller(
                clientId,
                signingIdentity,
                signingTrustSystem,
                clientIdScheme,
                identitySigningService,
                presentationProfileId,
                null);
    }

    private static Oid4vpWalletController controller(
            String clientId,
            String signingIdentity,
            String signingTrustSystem,
            String clientIdScheme,
            IdentitySigningService identitySigningService,
            String presentationProfileId,
            String scope) {
        final var endpointService = mock(Oid4vpWalletEndpointService.class);
        final var requestService = mock(VerificationRequestService.class);
        when(endpointService.lookupRequestData(REQUEST_ID))
                .thenReturn(
                        VerificationRequest.builder()
                                .withClientId(clientId)
                                .withResponseMode("direct_post.jwt")
                                .withDcqlQuery(scope == null
                                        ? null
                                        : new uniffi.kapun_dcql_rust.DcqlQuery(List.of(), null))
                                .withScope(scope)
                                .build());
        when(requestService.findVerificationRequestData(REQUEST_ID))
                .thenReturn(
                        new VerificationRequestData(
                                "nonce",
                                clientId,
                                "direct_post.jwt",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                signingIdentity,
                                null,
                                signingTrustSystem,
                                clientIdScheme,
                                OID4VPVersion.DRAFT_28,
                                new TrustConfiguration(null, null, null, null),
                                null,
                                false, "original-snapshot", presentationProfileId));
        return new Oid4vpWalletController(
                new ObjectMapper(),
                new ClientIdService(),
                endpointService,
                new ResponseEncryptionSessionKeys(new byte[32]),
                requestService,
                identitySigningService);
    }

    private static class FailingWalletEndpointService extends Oid4vpWalletEndpointService {

        FailingWalletEndpointService() {
            super(
                    null,
                    "http://localhost",
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    null,
                    new ResponseEncryptionSessionKeys(new byte[32]));
        }

        @Override
        public VerificationRequest lookupRequestData(String requestId) {
            throw new IllegalStateException("Unexpected failure");
        }
    }
}
