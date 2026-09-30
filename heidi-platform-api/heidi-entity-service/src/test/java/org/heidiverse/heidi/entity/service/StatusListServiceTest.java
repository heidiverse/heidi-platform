// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.heidiverse.heidi.entity.data.repository.StatusListAllocationRepository;
import org.heidiverse.heidi.entity.data.repository.StatusListRepository;
import org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService;
import org.heidiverse.heidi.entity.data.service.IssuerDataService;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialType;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeEntity;
import org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.entity.StatusListAllocationEntity;
import org.heidiverse.heidi.entity.model.entity.StatusListEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyEntity;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustConfigurationRequest;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileId;
import org.heidiverse.heidi.entity.model.statuslist.StatusListPublishMode;
import org.heidiverse.heidi.entity.model.statuslist.StatusListRequest;
import org.heidiverse.heidi.entity.model.statuslist.StatusListType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

class StatusListServiceTest {
    @Test
    void defaultsTtlToOneYear() {
        var lists = mock(StatusListRepository.class);
        var keyService = mock(SigningKeyService.class);
        var saved = new StatusListEntity();
        when(lists.save(any())).thenAnswer(invocation -> {
            var list = invocation.<StatusListEntity>getArgument(0);
            saved.setTtl(list.getTtl());
            saved.setId(list.getId());
            saved.setTenantId(list.getTenantId());
            saved.setName(list.getName());
            saved.setType(list.getType());
            saved.setBits(list.getBits());
            saved.setEntryCount(list.getEntryCount());
            saved.setPublishMode(list.getPublishMode());
            saved.setSigningKeyId(list.getSigningKeyId());
            saved.setStatusData(list.getStatusData());
            saved.setCreatedAt(list.getCreatedAt());
            saved.setUpdatedAt(list.getUpdatedAt());
            return list;
        });

        var keyId = UUID.randomUUID();
        var key = new SigningKeyEntity();
        key.setLogicalKeyId("status-list-key");
        when(keyService.owned("tenant", keyId)).thenReturn(key);
        var service = new StatusListService(
                lists,
                mock(StatusListAllocationRepository.class),
                mock(CredentialSchemeDataService.class),
                keyService,
                mock(StatusListCodec.class),
                mock(StatusListTokenService.class),
                RestClient.builder(),
                "https://platform.example");

        service.create("tenant", new StatusListRequest(
                "revocations", StatusListType.SD_JWT_VC, 1, 32,
                StatusListPublishMode.LOCAL, null, keyId, null));

        assertThat(saved.getTtl()).isEqualTo(StatusListService.DEFAULT_TTL_SECONDS);
    }

    @Test
    void issuesFreshTokenForEveryLocalRequest() {
        var listId = UUID.randomUUID();
        var list = new StatusListEntity();
        list.setId(listId);
        list.setTenantId("tenant");
        list.setPublishMode(StatusListPublishMode.LOCAL);
        list.setPublishedAt(Instant.now());
        list.setPublishedToken("old-token");

        var lists = mock(StatusListRepository.class);
        var tokenService = mock(StatusListTokenService.class);
        when(lists.findById(listId)).thenReturn(Optional.of(list));
        when(tokenService.create(list,
                "https://platform.example/public/v1/status-lists/" + listId))
                .thenReturn("fresh-token");

        var service = new StatusListService(
                lists,
                mock(StatusListAllocationRepository.class),
                mock(CredentialSchemeDataService.class),
                mock(SigningKeyService.class),
                mock(StatusListCodec.class),
                tokenService,
                RestClient.builder(),
                "https://platform.example");

        assertThat(service.publicToken(listId)).isEqualTo("fresh-token");
        verify(tokenService).create(list,
                "https://platform.example/public/v1/status-lists/" + listId);
    }

    @Test
    void uploadsStatusListToSwissRegistry() {
        var listId = UUID.randomUUID();
        var swissEntryId = UUID.randomUUID();
        var swissUrl = "https://status.example/status-list";
        var list = new StatusListEntity();
        list.setId(listId);
        list.setTenantId("tenant");
        list.setName("revocations");
        list.setType(StatusListType.SD_JWT_VC);
        list.setBits(1);
        list.setEntryCount(8);
        list.setPublishMode(StatusListPublishMode.LOCAL);
        list.setSigningKeyId(UUID.randomUUID());
        list.setTtl(60L);
        list.setStatusData(new byte[] {0});
        list.setCreatedAt(Instant.now());
        list.setUpdatedAt(Instant.now());

        var identity = new IssuerDefinitionEntity();
        identity.setId(7);
        identity.setTenantId("tenant");
        var settings = new IssuerTrustConfigurationRequest(
                "did:webvh:issuer", "did:webvh:issuer", "https://trust.example",
                "https://status.example", "partner-id", "https://authoring.example",
                "https://auth.example/token", "client-id", "client-secret", "refresh-token",
                null, null, null, null);
        var slot = new IdentityKeySlotEntity();
        slot.setType(IdentityKeySlotType.IDENTITY_STATEMENT);
        slot.setTrustSystem(IssuerTrustSystem.Switzerland);
        slot.setConfiguration(new ObjectMapper().valueToTree(settings));

        var lists = mock(StatusListRepository.class);
        var tokenService = mock(StatusListTokenService.class);
        var issuerData = mock(IssuerDataService.class);
        var slots = mock(IdentityKeySlotService.class);
        var refresh = mock(SwissTrustStatementRefreshService.class);
        var keyService = mock(SigningKeyService.class);
        var key = new SigningKeyEntity();
        key.setLogicalKeyId("status-list-key");
        when(lists.findByIdForUpdate(listId)).thenReturn(Optional.of(list));
        when(lists.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(issuerData.findAll()).thenReturn(List.of(identity));
        when(slots.slots(identity.getId())).thenReturn(List.of(slot));
        when(refresh.retrieveAccessToken(identity, settings))
                .thenReturn(Optional.of("access-token"));
        when(tokenService.create(list, swissUrl, "did:webvh:issuer")).thenReturn("status-list-jwt");
        when(keyService.owned("tenant", list.getSigningKeyId())).thenReturn(key);

        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://status.example/api/v1/status/business-entities/partner-id/status-list-entries/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andRespond(withSuccess(
                        "{\"id\":\"" + swissEntryId + "\",\"statusRegistryUrl\":\""
                                + swissUrl + "\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "https://status.example/api/v1/status/business-entities/partner-id/status-list-entries/"
                                + swissEntryId))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andExpect(header("Content-Type", StatusListTokenService.MEDIA_TYPE))
                .andExpect(content().string("status-list-jwt"))
                .andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        var service = new StatusListService(
                lists,
                mock(StatusListAllocationRepository.class),
                mock(CredentialSchemeDataService.class),
                issuerData,
                keyService,
                slots,
                null,
                mock(StatusListCodec.class),
                tokenService,
                refresh,
                builder,
                "https://platform.example");

        var response = service.publishSwiss("tenant", listId);

        assertThat(list.getSwissStatusListId()).isEqualTo(swissEntryId);
        assertThat(list.getSwissStatusListUrl()).isEqualTo(swissUrl);
        assertThat(list.getSwissPublishedAt()).isNotNull();
        assertThat(response.swissStatusListUrl()).hasToString(swissUrl);
        verify(tokenService).create(list, swissUrl, "did:webvh:issuer");
        server.verify();
    }

    @Test
    void servesSwissStatusListWithDidFragment() {
        var listId = UUID.randomUUID();
        var list = new StatusListEntity();
        list.setId(listId);
        list.setTenantId("tenant");
        list.setPublishMode(StatusListPublishMode.LOCAL);
        list.setPublishedAt(Instant.now());

        var identity = new IssuerDefinitionEntity();
        identity.setId(7);
        var scheme = new CredentialSchemeEntity();
        scheme.setStatusListId(listId);
        scheme.setIssuanceProfileId(EcosystemProfileId.SWISS_ISSUANCE_2026_1);
        scheme.setIssuerDefinition(identity);
        var settings = new IssuerTrustConfigurationRequest(
                "did:webvh:issuer", "did:webvh:issuer", "https://trust.example",
                "https://status.example", "partner-id", "https://authoring.example",
                "https://auth.example/token", "client-id", "client-secret", "refresh-token",
                null, null, null, null);
        var slot = new IdentityKeySlotEntity();
        slot.setType(IdentityKeySlotType.IDENTITY_STATEMENT);
        slot.setTrustSystem(IssuerTrustSystem.Switzerland);
        slot.setConfiguration(new ObjectMapper().valueToTree(settings));

        var lists = mock(StatusListRepository.class);
        var schemes = mock(CredentialSchemeDataService.class);
        var slots = mock(IdentityKeySlotService.class);
        var tokenService = mock(StatusListTokenService.class);
        when(lists.findById(listId)).thenReturn(Optional.of(list));
        when(schemes.findAllByStatusListId(listId)).thenReturn(List.of(scheme));
        when(slots.slots(identity.getId())).thenReturn(List.of(slot));
        when(tokenService.create(list,
                "https://platform.example/public/v1/status-lists/" + listId,
                "did:webvh:issuer")).thenReturn("swiss-status-list-jwt");

        var service = new StatusListService(
                lists,
                mock(StatusListAllocationRepository.class),
                schemes,
                mock(IssuerDataService.class),
                mock(SigningKeyService.class),
                slots,
                null,
                mock(StatusListCodec.class),
                tokenService,
                null,
                RestClient.builder(),
                "https://platform.example");

        assertThat(service.publicToken(listId)).isEqualTo("swiss-status-list-jwt");
        verify(tokenService).create(list,
                "https://platform.example/public/v1/status-lists/" + listId,
                "did:webvh:issuer");
    }

    @Test
    void allocatesOneStableIndexPerBatchEntry() {
        var listId = UUID.randomUUID();
        var list = new StatusListEntity();
        list.setId(listId);
        list.setTenantId("tenant");
        list.setPublishMode(StatusListPublishMode.LOCAL);
        list.setEntryCount(8);
        list.setPublishedAt(Instant.now());

        var issuer = new IssuerDefinitionEntity();
        issuer.setSlug("issuer");
        var scheme = new CredentialSchemeEntity();
        scheme.setTenantId("tenant");
        scheme.setIssuerDefinition(issuer);
        scheme.setStatusListId(listId);
        scheme.setSupportedCredentialTypes(Set.of(CredentialType.SD_JWT));
        scheme.setMaxBatchSize(4);

        var lists = mock(StatusListRepository.class);
        var allocations = mock(StatusListAllocationRepository.class);
        var schemes = mock(CredentialSchemeDataService.class);
        when(schemes.findByCredentialIdentifierAndVersion("card", "1.0"))
                .thenReturn(Optional.of(scheme));
        when(lists.findByIdForUpdate(listId)).thenReturn(Optional.of(list));
        when(allocations.maxIndex(listId)).thenReturn(null);
        var saved = new HashMap<String, StatusListAllocationEntity>();
        when(allocations.findByStatusListIdAndAllocationKey(any(), any())).thenAnswer(invocation ->
                Optional.ofNullable(saved.get(invocation.getArgument(1))));
        when(allocations.save(any())).thenAnswer(invocation -> {
            var allocation = invocation.<StatusListAllocationEntity>getArgument(0);
            saved.put(allocation.getAllocationKey(), allocation);
            return allocation;
        });

        var service = new StatusListService(
                lists,
                allocations,
                schemes,
                mock(SigningKeyService.class),
                mock(StatusListCodec.class),
                mock(StatusListTokenService.class),
                RestClient.builder(),
                "https://platform.example");

        var result = service.allocate("issuer", "card", "1.0", "connection", 2);
        var retry = service.allocate("issuer", "card", "1.0", "connection", 2);

        assertThat(result.indices()).containsExactly(0, 1);
        assertThat(retry).isEqualTo(result);
        assertThat(result.uri()).hasToString(
                "https://platform.example/public/v1/status-lists/" + listId);
        var captor = ArgumentCaptor.forClass(StatusListAllocationEntity.class);
        org.mockito.Mockito.verify(allocations, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(StatusListAllocationEntity::getAllocationKey)
                .containsExactly("connection:0", "connection:1");
    }
}
