// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

class FederationServiceTest {
    private static final String PLATFORM = "http://platform.example";
    private static final String ENDPOINT =
            PLATFORM + "/internal/platform/v1/identities/member/federation/entity-configuration";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer platform = MockRestServiceServer.bindTo(builder).build();

    @Test
    void asksThePlatformToSignTheIdentitysEntityConfiguration() throws Exception {
        platform.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic c2VydmljZTpwYXNz"))
                .andExpect(jsonPath("$.entityId").value("https://verifier.example/member"))
                .andExpect(jsonPath("$.metadata.openid_credential_verifier").exists())
                .andRespond(withSuccess("signed.entity.configuration",
                        MediaType.valueOf("application/entity-statement+jwt")));

        var configuration = service().entityConfiguration("member");

        assertEquals("signed.entity.configuration", configuration.orElseThrow());
        platform.verify();
    }

    @Test
    void servesNothingForAnIdentityOutsideAnyFederation() throws Exception {
        platform.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertTrue(service().entityConfiguration("member").isEmpty());
    }

    @Test
    void omitsStandingResponseEncryptionWithoutAConfiguredKey() throws Exception {
        platform.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.metadata.openid_credential_verifier"
                        + ".authorization_encrypted_response_alg").doesNotExist())
                .andExpect(jsonPath("$.metadata.openid_credential_verifier"
                        + ".authorization_encrypted_response_enc").doesNotExist())
                .andExpect(jsonPath("$.metadata.openid_credential_verifier.jwks").doesNotExist())
                .andRespond(withSuccess("signed.entity.configuration",
                        MediaType.valueOf("application/entity-statement+jwt")));

        assertTrue(service().entityConfiguration("member").isPresent());
        platform.verify();
    }

    private FederationService service() {
        return new FederationService(
                builder,
                PLATFORM,
                "c2VydmljZTpwYXNz",
                "https://verifier.example/",
                List.of());
    }
}
