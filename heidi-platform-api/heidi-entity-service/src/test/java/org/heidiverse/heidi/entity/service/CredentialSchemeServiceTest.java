// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.transaction.Transactional;

import org.heidiverse.heidi.coordinator.model.oid4vci.CredentialOfferType;
import org.heidiverse.heidi.entity.data.repository.StatusListRepository;
import org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService;
import org.heidiverse.heidi.entity.data.service.IssuerDataService;
import org.heidiverse.heidi.entity.data.service.MetadataDataService;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeDetail;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeMetadata;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeState;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialType;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeIssuerResponse;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeEntity;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.entity.StatusListEntity;
import org.heidiverse.heidi.entity.model.issuer.IssuerKeyType;
import org.heidiverse.heidi.entity.model.issuer.IssuerSettings;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileId;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class CredentialSchemeServiceTest {

    @Test
    void createsCredentialSchemeWithItsInitialRelations() throws Exception {
        var schemaDataService = mock(CredentialSchemeDataService.class);
        var issuerDataService = mock(IssuerDataService.class);
        var issuer = mock(IssuerDefinitionEntity.class);
        when(issuerDataService.findById(7)).thenReturn(Optional.of(issuer));
        when(schemaDataService.findByCredentialIdentifierAndVersion("example.credential", "1"))
                .thenReturn(Optional.empty());
        when(schemaDataService.findByCredentialIdentifier("example.credential"))
                .thenReturn(List.of());
        when(schemaDataService.insertScheme(any(CredentialSchemeEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(schemaDataService.insertCredentialSchemeAttributes(any(List.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var service = new CredentialSchemeService(
                schemaDataService,
                mock(TemplateService.class),
                issuerDataService,
                mock(MetadataDataService.class),
                new ObjectMapper());
        var request = new CredentialSchemeDetail(
                null,
                "example.credential",
                "1",
                "Example credential",
                List.of(),
                List.of(),
                new CredentialSchemeMetadata(List.of()),
                new IssuerSettings(
                        7,
                        IssuerKeyType.SOFTWARE_NO_AUTH,
                        null,
                        null,
                        null,
                        Set.of(CredentialType.SD_JWT),
                        null,
                        null,
                        null,
                        IssuerTrustSystem.Default,
                        Map.of(),
                        null,
                        CredentialOfferType.VALUE,
                        EcosystemProfileId.CUSTOM_ISSUANCE_2026_1),
                1,
                null);

        var id = service.create(request, "tenant-a");

        assertNotNull(id);
        var inserted = org.mockito.ArgumentCaptor.forClass(CredentialSchemeEntity.class);
        verify(schemaDataService).insertScheme(inserted.capture());
        assertEquals(id, inserted.getValue().getUuid());
        assertEquals("tenant-a", inserted.getValue().getTenantId());
        assertEquals(CredentialSchemeState.CREATED, inserted.getValue().getState());
        verify(schemaDataService).insertCredentialSchemeAttributes(any(List.class));
        verify(schemaDataService).insertStyles(any(List.class));
    }

    @Test
    void issuerLookupRunsInsideTransaction() throws NoSuchMethodException {
        var method = CredentialSchemeService.class.getMethod(
                "findIssuerSlugByCredentialIdentifierAndVersion", String.class, String.class);

        assertNotNull(method.getAnnotation(Transactional.class));
    }

    @Test
    void resolvesIssuerSlugFromVersionedCredentialSchema() {
        var schemaDataService = mock(CredentialSchemeDataService.class);
        var issuer = mock(IssuerDefinitionEntity.class);
        var schema = mock(CredentialSchemeEntity.class);
        when(schemaDataService.findByCredentialIdentifierAndVersion("test-credential", "1.0"))
                .thenReturn(Optional.of(schema));
        when(schema.getState()).thenReturn(CredentialSchemeState.PUBLISHED);
        when(schema.getTenantId()).thenReturn("tenant-1");
        when(schema.getIssuerDefinition()).thenReturn(issuer);
        when(issuer.getSlug()).thenReturn("assigned-issuer");
        when(schema.getCredentialOfferType()).thenReturn(CredentialOfferType.URI);

        var service = new CredentialSchemeService(
                schemaDataService,
                mock(TemplateService.class),
                mock(IssuerDataService.class),
                mock(MetadataDataService.class),
                new ObjectMapper());

        assertEquals(
                Optional.of(new CredentialSchemeIssuerResponse(
                        "assigned-issuer", "tenant-1", CredentialOfferType.URI)),
                service.findIssuerSlugByCredentialIdentifierAndVersion("test-credential", "1.0"));
    }

    @Test
    void doesNotResolveIssuerForUnpublishedCredentialSchema() {
        var schemaDataService = mock(CredentialSchemeDataService.class);
        var schema = mock(CredentialSchemeEntity.class);
        when(schemaDataService.findByCredentialIdentifierAndVersion("test-credential", "1.0"))
                .thenReturn(Optional.of(schema));

        var service = new CredentialSchemeService(
                schemaDataService,
                mock(TemplateService.class),
                mock(IssuerDataService.class),
                mock(MetadataDataService.class),
                new ObjectMapper());

        for (var state : new CredentialSchemeState[] {
            CredentialSchemeState.CREATED, CredentialSchemeState.ARCHIVED
        }) {
            when(schema.getState()).thenReturn(state);

            assertEquals(
                    Optional.empty(),
                    service.findIssuerSlugByCredentialIdentifierAndVersion(
                            "test-credential", "1.0"));
        }
    }

    @Test
    void customIssuanceUsesProfileRoutingWhenLegacyTrustIsAbsent() throws Exception {
        var service = new CredentialSchemeService(
                mock(CredentialSchemeDataService.class),
                mock(TemplateService.class),
                mock(IssuerDataService.class),
                mock(MetadataDataService.class),
                new ObjectMapper());
        var settings = new IssuerSettings(
                1, IssuerKeyType.SOFTWARE_NO_AUTH, null, null, null, null,
                null, null, null, null, null, null, CredentialOfferType.VALUE,
                EcosystemProfileId.CUSTOM_ISSUANCE_2026_1);
        var method = CredentialSchemeService.class.getDeclaredMethod(
                "effectiveTrustSystem", IssuerSettings.class);
        method.setAccessible(true);

        assertEquals(IssuerTrustSystem.Custom, method.invoke(service, settings));
    }

    @Test
    void creatingCredentialDoesNotRequireProviderClientRegistration() throws Exception {
        var slots = mock(IdentityKeySlotService.class);
        doThrow(new IllegalArgumentException("issuer client missing"))
                .when(slots).requireClient(org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anySet());
        var service = new CredentialSchemeService(
                mock(CredentialSchemeDataService.class),
                mock(TemplateService.class),
                mock(IssuerDataService.class),
                mock(MetadataDataService.class),
                new ObjectMapper(),
                null,
                slots,
                mock(IssuerService.class));
        var settings = new IssuerSettings(
                1, IssuerKeyType.SOFTWARE_NO_AUTH, null, null, null, null,
                null, null, null, null, null, null, CredentialOfferType.VALUE,
                EcosystemProfileId.CUSTOM_ISSUANCE_2026_1);
        var identity = new IssuerDefinitionEntity();
        identity.setId(1);
        var method = CredentialSchemeService.class.getDeclaredMethod(
                "validateSigningKeyAssignments", IssuerSettings.class, IssuerDefinitionEntity.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(service, settings, identity));
    }

    @Test
    void customIssuanceAcceptsLegacyDefaultCredentialSlot() throws Exception {
        var slots = mock(IdentityKeySlotService.class);
        var credentialSlot = new IdentityKeySlotEntity();
        credentialSlot.setType(IdentityKeySlotType.CREDENTIAL_SIGNING);
        credentialSlot.setTrustSystem(IssuerTrustSystem.Default);
        when(slots.slots(1)).thenReturn(List.of(credentialSlot));

        var service = new CredentialSchemeService(
                mock(CredentialSchemeDataService.class),
                mock(TemplateService.class),
                mock(IssuerDataService.class),
                mock(MetadataDataService.class),
                new ObjectMapper(),
                null,
                slots,
                mock(IssuerService.class));
        var settings = new IssuerSettings(
                1, IssuerKeyType.SOFTWARE_NO_AUTH, null, null, null, null,
                null, null, null, IssuerTrustSystem.Custom, null, null,
                CredentialOfferType.VALUE, EcosystemProfileId.CUSTOM_ISSUANCE_2026_1);
        var identity = new IssuerDefinitionEntity();
        identity.setId(1);
        var method = CredentialSchemeService.class.getDeclaredMethod(
                "validateSigningKeyAssignments", IssuerSettings.class, IssuerDefinitionEntity.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(service, settings, identity));
    }

    @Test
    void swissIssuanceRequiresPublishedSwissStatusList() throws Exception {
        var statusListRepository = mock(StatusListRepository.class);
        var statusList = new StatusListEntity();
        statusList.setTenantId("tenant-a");
        var statusListId = UUID.randomUUID();
        when(statusListRepository.findById(statusListId)).thenReturn(Optional.of(statusList));
        var service = new CredentialSchemeService(
                mock(CredentialSchemeDataService.class),
                mock(TemplateService.class),
                mock(IssuerDataService.class),
                mock(MetadataDataService.class),
                new ObjectMapper(),
                statusListRepository,
                null,
                null);
        var settings = new IssuerSettings(
                1, IssuerKeyType.SOFTWARE_NO_AUTH, null, null, null,
                Set.of(CredentialType.SD_JWT), null, null, null,
                IssuerTrustSystem.Switzerland, Map.of(), statusListId,
                CredentialOfferType.VALUE, EcosystemProfileId.SWISS_ISSUANCE_2026_1);
        var method = CredentialSchemeService.class.getDeclaredMethod(
                "validateStatusList", IssuerSettings.class, String.class);
        method.setAccessible(true);

        var exception = assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(service, settings, "tenant-a"));
        assertEquals(
                "Swiss issuance requires a status list published in the Swiss registry",
                exception.getCause().getMessage());

        statusList.setSwissStatusListUrl("https://status.example/status-list");
        statusList.setSwissPublishedAt(java.time.Instant.now());
        assertDoesNotThrow(() -> method.invoke(service, settings, "tenant-a"));
    }
}
