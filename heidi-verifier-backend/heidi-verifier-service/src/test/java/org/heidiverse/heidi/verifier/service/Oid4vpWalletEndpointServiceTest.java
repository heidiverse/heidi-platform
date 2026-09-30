// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.heidiverse.heidi.verifier.data.service.VerificationRequestService;
import org.heidiverse.heidi.verifier.data.service.VerificationSubmissionService;
import org.heidiverse.heidi.verifier.model.api.wallet.ClientMetadataDraft27;
import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.heidiverse.heidi.shared.trustframework.TrustConfiguration;
import org.heidiverse.heidi.verifier.model.vp.VerificationRequestData;
import org.heidiverse.heidi.verifier.model.vp.BbsIssuerMetadata;
import org.heidiverse.heidi.verifier.service.util.ResponseEncryptionSessionKeys;
import org.junit.jupiter.api.Test;
import org.kapunsdk.presentation.request.model.OID4VPVersion;

import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

class Oid4vpWalletEndpointServiceTest {

    @Test
    void rejectsStoredResponseModeOutsideSelectedProfile() {
        assertThrows(
                VpVerificationException.class,
                () -> serviceForResponseMode("direct_post").lookupRequestData("request-id"));
    }

    @Test
    void publishesConfiguredIssuerMetadataInZkpAuthorizationRequest() throws Exception {
        final var service = serviceForResponseMode("direct_post.jwt");

        final var request = service.lookupRequestData("request-id");
        final var serialized = new ObjectMapper().writeValueAsString(request);
        assertFalse(serialized.contains("client_id_scheme"));
        final var zkp = request.getZkp();
        final var metadata = (ClientMetadataDraft27) request.getClientMetadata();
        final var announcedKey = (Map<?, ?>) ((List<?>) metadata.jwks().get("keys")).getFirst();

        assertNotNull(zkp);
        assertEquals("zkp-definition", zkp.definition());
        assertEquals("circuit-proving-keys", zkp.provingKey());
        assertEquals("issuer-public-key", zkp.issuerPk());
        assertEquals("did:example:issuer", zkp.issuerId());
        assertEquals("did:example:issuer#key-1", zkp.issuerKeyId());
        assertEquals("ECDH-ES", announcedKey.get("alg"));
        assertEquals(
                List.of("A128GCM", "A256GCM"),
                metadata.encryptedResponseEncValuesSupported());
    }

    @Test
    void publishesStoredScopeInAuthorizationRequest() {
        final var request = serviceForResponseMode("direct_post.jwt", "ch.example.identity")
                .lookupRequestData("request-id");

        assertEquals("ch.example.identity", request.getScope());
    }

    private Oid4vpWalletEndpointService serviceForResponseMode(String responseMode) {
        return serviceForResponseMode(responseMode, null);
    }

    private Oid4vpWalletEndpointService serviceForResponseMode(
            String responseMode, String scope) {
        final var requestData =
                new VerificationRequestData(
                        "nonce",
                        "client-id",
                        responseMode,
                        null,
                        "proof-scheme",
                        "circuit-verification-keys",
                        "circuit-proving-keys",
                        "zkp-definition",
                        new uniffi.kapun_dcql_rust.DcqlQuery(
                                List.of(new uniffi.kapun_dcql_rust.CredentialQuery(
                                        "credential_bbs-termwise", "bbs-termwise", null,
                                        null, null, null, null, null)),
                                null),
                        scope,
                        null,
                        null,
                        null,
                        null,
                        Map.of(),
                        "verifier-signing-identity",
                        "verifier-signing-key-id",
                        null,
                        null,
                        OID4VPVersion.DRAFT_28,
                        new TrustConfiguration(null, null, null, null),
                        List.of(),
                        false, null, "EUDI_PRESENTATION_2026_1");
        final var requestService =
                new VerificationRequestService(null) {
                    @Override
                    public String findTransactionId(String requestId) {
                        return "transaction-id";
                    }

                    @Override
                    public void markStarted(String requestId) {}

                    @Override
                    public VerificationRequestData findVerificationRequestData(String requestId) {
                        return requestData;
                    }

                    @Override
                    public java.util.Optional<VerificationRequestService.ResponseEncryptionKey>
                            responseEncryptionKey(String requestId) {
                        return java.util.Optional.empty();
                    }

                    @Override
                    public VerificationRequestService.ResponseEncryptionKey saveResponseEncryptionKey(
                            String requestId, String keyId, String publicJwk, String encryptedPrivateJwk) {
                        return new VerificationRequestService.ResponseEncryptionKey(
                                requestId, keyId, publicJwk, encryptedPrivateJwk);
                    }
                };
        final var submissionService =
                new VerificationSubmissionService(null) {
                    @Override
                    public boolean submissionExists(String transactionId) {
                        return false;
                    }
                };
        final var sessionKeys = new ResponseEncryptionSessionKeys(new byte[32]);
        final var signingOperationClient = mock(SigningOperationClient.class);
        when(signingOperationClient.bbsIssuerMetadata(
                        "proof-scheme", List.of("credential_bbs-termwise")))
                .thenReturn(new BbsIssuerMetadata(
                        "issuer-public-key",
                        "did:example:issuer",
                        "did:example:issuer#key-1"));
        return new Oid4vpWalletEndpointService(
                new ObjectMapper(),
                "https://verifier.example",
                requestService,
                submissionService,
                List.of(),
                null,
                null,
                signingOperationClient,
                sessionKeys);
    }
}
