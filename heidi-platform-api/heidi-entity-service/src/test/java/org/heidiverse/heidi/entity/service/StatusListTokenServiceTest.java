// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyEntity;
import org.heidiverse.heidi.entity.model.entity.StatusListEntity;
import org.heidiverse.heidi.shared.signing.SigningKeyProvider;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

class StatusListTokenServiceTest {
    @Test
    void createsStatusListJwt() throws Exception {
        var keyService = mock(SigningKeyService.class);
        var providerService = mock(SigningProviderService.class);
        var provider = mock(SigningKeyProvider.class);
        var keyId = UUID.randomUUID();
        var key = new SigningKeyEntity();
        key.setProviderId(7);
        var version = new SigningKeyVersionEntity();
        version.setKeyUri("software://status-key");
        version.setAlgorithm("ES256");
        version.setPublicJwk("{\"kty\":\"EC\",\"kid\":\"status-key-v1\"}");
        when(keyService.owned("tenant", keyId)).thenReturn(key);
        when(keyService.activeVersion("tenant", keyId)).thenReturn(version);
        when(providerService.provider("tenant", 7)).thenReturn(provider);
        when(provider.sign(any(), any())).thenReturn(new byte[] {1, 2, 3});

        var entity = new StatusListEntity();
        entity.setTenantId("tenant");
        entity.setSigningKeyId(keyId);
        entity.setBits(1);
        entity.setStatusData(new byte[] {0});
        entity.setTtl(60L);
        var objectMapper = new ObjectMapper();
        var service = new StatusListTokenService(
                keyService, providerService, new StatusListCodec(), objectMapper);

        var token = service.create(entity, "https://example.com/status/1");

        var parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        var header = objectMapper.readTree(decode(parts[0]));
        var claims = objectMapper.readTree(decode(parts[1]));
        assertThat(header.path("typ").asText()).isEqualTo("statuslist+jwt");
        assertThat(header.path("kid").asText()).isEqualTo("status-key-v1");
        assertThat(claims.path("sub").asText()).isEqualTo("https://example.com/status/1");
        assertThat(claims.path("ttl").asLong()).isEqualTo(60L);
        assertThat(claims.path("exp").asLong())
                .isEqualTo(claims.path("iat").asLong() + claims.path("ttl").asLong());
        assertThat(claims.path("status_list").path("bits").asInt()).isEqualTo(1);
        assertThat(parts[2]).isEqualTo("AQID");
    }

    @Test
    void usesSwissDidFragmentAsKeyId() throws Exception {
        var keyService = mock(SigningKeyService.class);
        var providerService = mock(SigningProviderService.class);
        var provider = mock(SigningKeyProvider.class);
        var keyId = UUID.randomUUID();
        var key = new SigningKeyEntity();
        key.setProviderId(7);
        var version = new SigningKeyVersionEntity();
        version.setKeyUri("software://status-key");
        version.setAlgorithm("ES256");
        version.setPublicJwk("{\"kty\":\"EC\",\"kid\":\"assert-key-01\"}");
        when(keyService.owned("tenant", keyId)).thenReturn(key);
        when(keyService.activeVersion("tenant", keyId)).thenReturn(version);
        when(providerService.provider("tenant", 7)).thenReturn(provider);
        when(provider.sign(any(), any())).thenReturn(new byte[] {1, 2, 3});

        var entity = new StatusListEntity();
        entity.setTenantId("tenant");
        entity.setSigningKeyId(keyId);
        entity.setBits(1);
        entity.setStatusData(new byte[] {0});
        entity.setTtl(60L);
        var objectMapper = new ObjectMapper();
        var service = new StatusListTokenService(
                keyService, providerService, new StatusListCodec(), objectMapper);

        var token = service.create(entity, "https://example.com/status/1", "did:webvh:issuer");

        var header = objectMapper.readTree(decode(token.split("\\.")[0]));
        assertThat(header.path("kid").asText()).isEqualTo("did:webvh:issuer#assert-key-01");
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value.getBytes(StandardCharsets.US_ASCII));
    }
}
