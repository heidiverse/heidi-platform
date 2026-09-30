// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.ws.controller;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.heidiverse.heidi.entity.model.entity.SigningCertificateEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.issuer.PlatformKeyRequest;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateSource;
import org.heidiverse.heidi.entity.service.SigningKeyService;
import org.heidiverse.heidi.entity.service.IssuerService;
import org.heidiverse.heidi.entity.service.LocalDevelopmentTrustSeed;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class KeyManagementControllerTest {
    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void listsGlobalKeysWithoutFallingBackToTheAdministratorsTenant() {
        var service = mock(SigningKeyService.class);
        var key = new SigningKeyEntity();
        key.setId(UUID.randomUUID());
        key.setLogicalKeyId("platform-identity");
        when(service.keys(null)).thenReturn(List.of(key));
        var controller = new KeyManagementCoordinatorController(service, mock(IssuerService.class));

        var result = controller.findAllGlobal();

        assertEquals(List.of("platform-identity"), result.stream()
                .map(org.heidiverse.heidi.entity.model.issuer.PlatformKeyResponse::keyId)
                .toList());
        verify(service).keys(null);
    }

    @Test
    void provisionsDecryptionUsage() {
        var jwt = Jwt.withTokenValue("test").header("alg", "none")
                .subject("operator").claim("companyId", "tenant-a").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        var service = mock(SigningKeyService.class);
        var key = new SigningKeyEntity();
        key.setId(UUID.randomUUID());
        key.setLogicalKeyId("request-encryption");
        when(service.keys("tenant-a")).thenReturn(List.of(key));
        var controller = new KeyManagementCoordinatorController(service, mock(IssuerService.class));

        controller.create("tenant-a", new PlatformKeyRequest("request-encryption", "ES256", 7,
                SigningKeyUsage.KEY_AGREEMENT, null));

        verify(service).create("tenant-a", "request-encryption",
                Set.of(SigningKeyUsage.KEY_AGREEMENT), "ES256", 7, null);
    }

    @Test
    void developmentCertificateRetryReturnsExistingCertificate() throws Exception {
        var jwt = Jwt.withTokenValue("test").header("alg", "none")
                .subject("operator").claim("companyId", "tenant-a").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        var keys = mock(SigningKeyService.class);
        var seed = mock(LocalDevelopmentTrustSeed.class);
        var key = new SigningKeyEntity();
        key.setId(UUID.randomUUID());
        key.setLogicalKeyId("credential");
        var version = new SigningKeyVersionEntity();
        version.setId(UUID.randomUUID());
        var certificate = new SigningCertificateEntity();
        certificate.setId(UUID.randomUUID());
        certificate.setProfile(SigningCertificateProfile.CREDENTIAL_SIGNING);
        certificate.setSource(SigningCertificateSource.DEVELOPMENT);
        certificate.setTrustSystem(IssuerTrustSystem.EUDI);
        certificate.setCertificateChain(List.of("leaf"));
        certificate.setNotBefore(Instant.now().minusSeconds(60));
        certificate.setNotAfter(Instant.now().plusSeconds(60));
        when(keys.keys("tenant-a")).thenReturn(List.of(key));
        when(keys.version("tenant-a", key.getId(), version.getId())).thenReturn(version);
        when(keys.certificates("tenant-a", key.getId(), version.getId()))
                .thenReturn(List.of(certificate));
        var controller = new DevelopmentKeyController(keys, seed);

        var response = controller.create("credential", version.getId(), "tenant-a",
                new DevelopmentKeyController.DevelopmentCertificate(
                        SigningCertificateProfile.CREDENTIAL_SIGNING, IssuerTrustSystem.EUDI));

        assertEquals(Map.of("certificateId", certificate.getId()), response);
        verifyNoInteractions(seed);
        verify(keys, never()).setCertificateChain(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void csrIsDownloadableText() throws Exception {
        var jwt = Jwt.withTokenValue("test").header("alg", "none")
                .subject("operator").claim("companyId", "tenant-a").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        var keys = mock(SigningKeyService.class);
        var key = new SigningKeyEntity();
        key.setId(UUID.randomUUID());
        key.setLogicalKeyId("credential");
        when(keys.keys("tenant-a")).thenReturn(List.of(key));
        var version = UUID.randomUUID();
        var pem = "-----BEGIN CERTIFICATE REQUEST-----\nrequest\n-----END CERTIFICATE REQUEST-----\n";
        when(keys.createCsr(eq("tenant-a"), eq(key.getId()), eq(version), any())).thenReturn(pem);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new KeyManagementCoordinatorController(keys, mock(IssuerService.class))).build();

        // PEM is text; certificate MIME handlers must not intercept a browser fetch.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/management/v1/keys/credential/versions/{version}/csr", version)
                .param("tenantId", "tenant-a")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .accept(org.springframework.http.MediaType.TEXT_PLAIN)
                .content("{\"subject\":\"CN=credential\",\"dnsNames\":[],\"uriNames\":[]}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(org.springframework.http.MediaType.TEXT_PLAIN))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(pem));
    }

    @Test
    void csrDownloadUsesBrowserAttachment() throws Exception {
        var jwt = Jwt.withTokenValue("test").header("alg", "none")
                .subject("operator").claim("companyId", "tenant-a").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        var keys = mock(SigningKeyService.class);
        var key = new SigningKeyEntity();
        key.setId(UUID.randomUUID());
        key.setLogicalKeyId("credential");
        when(keys.keys("tenant-a")).thenReturn(List.of(key));
        var version = UUID.randomUUID();
        var pem = "-----BEGIN CERTIFICATE REQUEST-----\nrequest\n-----END CERTIFICATE REQUEST-----\n";
        when(keys.createCsr(eq("tenant-a"), eq(key.getId()), eq(version), any())).thenReturn(pem);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new KeyManagementCoordinatorController(keys, mock(IssuerService.class))).build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/management/v1/keys/credential/versions/{version}/csr", version)
                .param("tenantId", "tenant-a")
                .param("subject", "CN=credential"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", "attachment; filename=\"credential.csr.pem\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(pem));
    }

    @Test
    void activationPublishesGrants() {
        var jwt = Jwt.withTokenValue("test").header("alg", "none")
                .subject("operator").claim("companyId", "tenant-a").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        var keys = mock(SigningKeyService.class);
        var identities = mock(IssuerService.class);
        var key = new SigningKeyEntity();
        key.setId(UUID.randomUUID());
        key.setLogicalKeyId("credential");
        when(keys.keys("tenant-a")).thenReturn(List.of(key));
        var versionId = UUID.randomUUID();
        var controller = new KeyManagementCoordinatorController(keys, identities);

        controller.activate("credential", versionId, "tenant-a", null);

        // Returning an active version must not leave its consumer waiting for the scheduler.
        var order = inOrder(keys, identities);
        order.verify(keys).activate("tenant-a", key.getId(), versionId, java.util.Map.of());
        order.verify(identities).reconcileSigningGrants();
    }
}
