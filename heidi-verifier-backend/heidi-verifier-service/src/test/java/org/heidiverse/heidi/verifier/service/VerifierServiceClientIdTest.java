// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import org.heidiverse.heidi.verifier.model.api.verifier.CredentialsRequest;
import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.heidiverse.heidi.shared.trustframework.TrustConfiguration;
import org.heidiverse.heidi.shared.trustframework.TrustFrameworkType;
import org.kapunsdk.presentation.request.model.OID4VPVersion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.ObjectMapper;
import uniffi.kapun_dcql_rust.CredentialQuery;
import uniffi.kapun_dcql_rust.DcqlQuery;

import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import org.heidiverse.heidi.verifier.data.repository.VerificationRequestRepository;
import org.heidiverse.heidi.verifier.data.service.VerificationRequestService;
import org.heidiverse.heidi.verifier.model.entity.VerificationRequestEntity;

/**
 * An {@code x509_san_dns} request is signed with a verifier identity's certificate. Without an
 * identity there is no certificate a wallet could trust, so the request is refused up front.
 */
class VerifierServiceClientIdTest {

    private static final String VERIFIER_URL = "https://verifier.example/wallet";

    private final VerifierService service =
            new VerifierService(null, null, Duration.ofMinutes(5), new ObjectMapper());

    // Null covers the default scheme outside the Swiss trust system.
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "x509_san_dns")
    void x509SanDnsRequiresAVerifierIdentity(String scheme) {
        ReflectionTestUtils.setField(service, "responseBaseUrl", VERIFIER_URL);

        var exception =
                assertThrows(
                        VpVerificationException.class,
                        () -> service.initiateTransaction(request(scheme)));

        assertEquals("x509_san_dns requires a verifier identity", exception.getMessage());
    }

    @org.junit.jupiter.api.Test
    void x509SanDnsKeepsExactVerifierHost() {
        ReflectionTestUtils.setField(
                service, "responseBaseUrl", "https://www.verifier.example/wallet");
        var request = mock(CredentialsRequest.class);
        when(request.presentationProfileId()).thenReturn("CUSTOM_PRESENTATION_2026_1");
        when(request.clientIdScheme()).thenReturn("x509_san_dns");
        when(request.signingIdentity()).thenReturn("identity");

        var clientId = ReflectionTestUtils.<String>invokeMethod(
                service, "resolveClientId", request, OID4VPVersion.DRAFT_28, null);

        assertEquals("x509_san_dns:www.verifier.example", clientId);
    }

    @org.junit.jupiter.api.Test
    void swissDefaultRequiresAVerifierIdentityForDid() {
        var exception =
                assertThrows(
                        VpVerificationException.class,
                        () -> service.initiateTransaction(request(
                                null, "Switzerland", null, "SWISS_PRESENTATION_2026_1")));

        assertEquals("decentralized_identifier requires a verifier identity", exception.getMessage());
    }

    @org.junit.jupiter.api.Test
    void eudiDefaultRequiresAVerifierIdentityForX509Hash() {
        var exception =
                assertThrows(
                        VpVerificationException.class,
                        () -> service.initiateTransaction(request(
                                null, "EUDI", null, "EUDI_PRESENTATION_2026_1")));

        assertEquals("x509_hash requires a verifier identity", exception.getMessage());
    }

    @org.junit.jupiter.api.Test
    void unknownPresentationProfileIsRejected() {
        var exception = assertThrows(
                VpVerificationException.class,
                () -> service.initiateTransaction(
                        request(null, null, null, "UNKNOWN_PROFILE")));

        assertEquals("Unsupported presentation profile: UNKNOWN_PROFILE", exception.getMessage());
    }

    @org.junit.jupiter.api.Test
    void explicitClientIdSchemeCannotOverridePresentationProfile() {
        var exception = assertThrows(
                VpVerificationException.class,
                () -> service.initiateTransaction(
                        request(
                                "decentralized_identifier",
                                null,
                                null,
                                "EUDI_PRESENTATION_2026_1")));

        assertEquals(
                "Client ID scheme 'decentralized_identifier' does not match presentation profile '"
                        + "EUDI_PRESENTATION_2026_1'",
                exception.getMessage());
    }

    @org.junit.jupiter.api.Test
    void unsupportedClientIdSchemeIsRejected() {
        var exception = assertThrows(
                VpVerificationException.class,
                () -> service.initiateTransaction(
                        request("openid_federation", null, null,
                                "CUSTOM_PRESENTATION_2026_1")));

        assertEquals("Client ID scheme 'openid_federation' does not match presentation profile "
                        + "'CUSTOM_PRESENTATION_2026_1'",
                exception.getMessage());
    }

    @org.junit.jupiter.api.Test
    void germanHaipRequiresAVerifierIdentityForX509Hash() {
        var exception =
                assertThrows(
                        VpVerificationException.class,
                        () -> service.initiateTransaction(
                                request(null, "EUDI", TrustFrameworkType.DE,
                                        "EUDI_PRESENTATION_2026_1")));

        assertEquals("x509_hash requires a verifier identity", exception.getMessage());
    }

    private static CredentialsRequest request(String clientIdScheme) {
        return request(clientIdScheme, null, null, "CUSTOM_PRESENTATION_2026_1");
    }

    @org.junit.jupiter.api.Test
    void persistsOneSigningSnapshot() throws Exception {
        var repository = mock(VerificationRequestRepository.class);
        var stored = new ArrayList<VerificationRequestEntity>();
        when(repository.save(any(VerificationRequestEntity.class))).thenAnswer(call -> {
            var entity = call.<VerificationRequestEntity>getArgument(0);
            stored.add(entity);
            return entity;
        });
        when(repository.findByRequestId(anyString())).thenAnswer(call -> stored.stream()
                .filter(entity -> entity.getRequestId().equals(call.getArgument(0))).findFirst());
        var data = new VerificationRequestService(repository);
        var signing = mock(IdentitySigningService.class);
        var signer = new IdentitySigningService.ResolvedSigner(null, null, null, "identity-key");
        when(signing.snapshot("identity", "EUDI", null, "EUDI_PRESENTATION_2026_1"))
                .thenReturn("original-configuration");
        when(signing.resolveSnapshot("original-configuration")).thenReturn(signer);
        when(signing.x509CertificateHash(signer)).thenReturn("original-hash");
        var verifier = new VerifierService(data, null, Duration.ofMinutes(5),
                new ObjectMapper(), signing, null);
        var request = new CredentialsRequest("nonce", "proof", null, null, null,
                request(null).dcqlQuery(), null, null, null, null, "tenant", null, "identity", null,
                "EUDI", "x509_hash", null, null, false,
                "EUDI_PRESENTATION_2026_1");

        verifier.initiateTransaction(request);
        when(signing.snapshot("identity", "EUDI", null, "EUDI_PRESENTATION_2026_1"))
                .thenReturn("renewed-configuration");

        assertEquals(2, stored.size());
        for (var entity : stored) {
            var persisted = data.findVerificationRequestData(entity.getRequestId());
            assertEquals("original-configuration", persisted.signingSnapshot());
            assertEquals("x509_hash:original-hash", persisted.clientId());
            assertEquals("direct_post.jwt", entity.getResponseMode());
        }
        verify(signing).snapshot("identity", "EUDI", null, "EUDI_PRESENTATION_2026_1");
        verify(signing).retainSnapshot("identity", "original-configuration",
                stored.getFirst().getTransactionId(), stored.getFirst().getExpiresAt());
    }

    @org.junit.jupiter.api.Test
    void retriesFailedFlowRelease() {
        var data = mock(VerificationRequestService.class);
        var signing = mock(IdentitySigningService.class);
        when(data.finishedSigningFlows()).thenReturn(List.of("flow"));
        doThrow(new IllegalStateException("Platform unavailable")).doNothing()
                .when(signing).releaseSnapshot("flow");
        var verifier = new VerifierService(data, null, Duration.ofMinutes(5),
                new ObjectMapper(), signing, null);

        verifier.releaseFinishedFlows();
        verify(data, never()).markSigningFlowReleased("flow");
        verifier.releaseFinishedFlows();
        verify(data).markSigningFlowReleased("flow");
    }

    private static CredentialsRequest request(String clientIdScheme, String signingTrustSystem) {
        return request(clientIdScheme, signingTrustSystem, null);
    }

    private static CredentialsRequest request(
            String clientIdScheme,
            String signingTrustSystem,
            TrustFrameworkType trustFramework) {
        return request(clientIdScheme, signingTrustSystem, trustFramework, null);
    }

    private static CredentialsRequest request(
            String clientIdScheme,
            String signingTrustSystem,
            TrustFrameworkType trustFramework,
            String presentationProfileId) {
        var dcql =
                new DcqlQuery(
                        List.of(new CredentialQuery(
                                "credential", "dc+sd-jwt", null, null, null, null, null, null)),
                        null);
        return new CredentialsRequest(
                "nonce", "proof-scheme", null, null, null, dcql, null, null, null, null, "tenant",
                null, null, null, signingTrustSystem, clientIdScheme,
                new TrustConfiguration(trustFramework, null, null, null), null, false,
                presentationProfileId);
    }
}
