// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService;
import org.heidiverse.heidi.entity.data.service.IssuerDataService;
import org.heidiverse.heidi.entity.data.service.ProofSchemeDataService;
import org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class IssuerServiceTest {
    @Test
    void requestDecryptionKeysAreFilteredByIssuanceTrustProfile() {
        var identity = new IssuerDefinitionEntity();
        identity.setId(7);
        identity.setTenantId("tenant-a");
        identity.setSlug("issuer");

        var globalKey = requestKey("global", 1);
        var eudiKey = requestKey("eudi", 2);
        var swissKey = requestKey("swiss", 3);
        var customKey = requestKey("custom", 4);
        var oidfKey = requestKey("oidf", 5);
        var slots = mock(IdentityKeySlotService.class);
        when(slots.slots(7)).thenReturn(List.of(
                requestSlot(IssuerTrustSystem.Default, globalKey.getId()),
                requestSlot(IssuerTrustSystem.EUDI, eudiKey.getId()),
                requestSlot(IssuerTrustSystem.Switzerland, swissKey.getId()),
                requestSlot(IssuerTrustSystem.Custom, customKey.getId()),
                requestSlot(IssuerTrustSystem.OIDF, oidfKey.getId())));

        var versions = Map.of(
                globalKey.getId(), requestVersion(globalKey.getId(), "global"),
                eudiKey.getId(), requestVersion(eudiKey.getId(), "eudi"),
                swissKey.getId(), requestVersion(swissKey.getId(), "swiss"),
                customKey.getId(), requestVersion(customKey.getId(), "custom"),
                oidfKey.getId(), requestVersion(oidfKey.getId(), "oidf"));
        var signingKeys = mock(SigningKeyService.class);
        when(signingKeys.owned(eq("tenant-a"), any(UUID.class)))
                .thenAnswer(invocation -> Map.of(
                        globalKey.getId(), globalKey,
                        eudiKey.getId(), eudiKey,
                        swissKey.getId(), swissKey,
                        customKey.getId(), customKey,
                        oidfKey.getId(), oidfKey).get(invocation.getArgument(1)));
        when(signingKeys.allVersions(any(UUID.class)))
                .thenAnswer(invocation -> List.of(versions.get(invocation.getArgument(0))));

        var providers = mock(SigningProviderService.class);
        when(providers.runtimeConfiguration(eq("tenant-a"), anyInt())).thenReturn(
                new org.heidiverse.heidi.entity.model.signing.SigningProviderConfiguration(
                        "https://signer.example", null, "NONE", null,
                        List.of(), List.of(), List.of(), List.of(), false, false, false,
                        List.of("ECDH-ES")));
        var issuerData = mock(IssuerDataService.class);
        when(issuerData.findBySlug("issuer")).thenReturn(Optional.of(identity));
        var service = new IssuerService(issuerData, mock(CredentialSchemeDataService.class),
                mock(SwissTrustStatementRefreshService.class), providers, signingKeys,
                mock(SigningGrantService.class), slots, mock(IdentityKeyIntegrityService.class),
                mock(ProofSchemeDataService.class),
                mock(org.heidiverse.heidi.entity.data.repository.SigningFlowRepository.class));

        var eudiKeys = service.getCredentialEncryption(
                "issuer", EcosystemProfileId.EUDI_ISSUANCE_2026_1).orElseThrow().requestKeys();
        var swissKeys = service.getCredentialEncryption(
                "issuer", EcosystemProfileId.SWISS_ISSUANCE_2026_1).orElseThrow().requestKeys();
        var customKeys = service.getCredentialEncryption(
                "issuer", EcosystemProfileId.CUSTOM_ISSUANCE_2026_1).orElseThrow().requestKeys();
        var oidfKeys = service.getCredentialEncryption(
                "issuer", EcosystemProfileId.OIDF_ISSUANCE_2026_1).orElseThrow().requestKeys();

        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(null, IssuerTrustSystem.EUDI)),
                eudiKeys.stream().map(org.heidiverse.heidi.entity.model.issuer.CredentialEncryptionKey::trustSystem)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(null, IssuerTrustSystem.Switzerland)),
                swissKeys.stream().map(org.heidiverse.heidi.entity.model.issuer.CredentialEncryptionKey::trustSystem)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(null, IssuerTrustSystem.Custom)),
                customKeys.stream().map(org.heidiverse.heidi.entity.model.issuer.CredentialEncryptionKey::trustSystem)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(null, IssuerTrustSystem.OIDF)),
                oidfKeys.stream().map(org.heidiverse.heidi.entity.model.issuer.CredentialEncryptionKey::trustSystem)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    private static IdentityKeySlotEntity requestSlot(IssuerTrustSystem trustSystem, UUID keyId) {
        var slot = new IdentityKeySlotEntity();
        slot.setType(IdentityKeySlotType.DECRYPTION);
        slot.setTrustSystem(trustSystem);
        slot.setKeyId(keyId);
        return slot;
    }

    private static org.heidiverse.heidi.entity.model.entity.SigningKeyEntity requestKey(
            String logicalKeyId, int providerId) {
        var key = new org.heidiverse.heidi.entity.model.entity.SigningKeyEntity();
        key.setId(UUID.randomUUID());
        key.setLogicalKeyId(logicalKeyId);
        key.setProviderId(providerId);
        return key;
    }

    private static org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity requestVersion(
            UUID keyId, String keyIdSuffix) {
        var version = new org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity();
        version.setId(UUID.randomUUID());
        version.setKeyId(keyId);
        version.setVersion(1);
        version.setKeyUri("software://request/" + keyIdSuffix);
        version.setAlgorithm("EC");
        version.setPublicJwk("{\"kty\":\"EC\",\"crv\":\"P-256\","
                + "\"x\":\"Gym2Ga9-WGyE3T5EJGftqE-MpMSJAD-uzGFNkzN5VW8\","
                + "\"y\":\"Blxn144RNJNFKxKy4TuQFfpFoRJjC9mnraZ-I-GMpJU\","
                + "\"kid\":\"" + keyIdSuffix + "\"}");
        version.setStatus(SigningKeyService.ACTIVE);
        return version;
    }

    @Test
    void requestDecryptionScopesUseDefaultAsGlobalFallback() {
        assertTrue(IssuerService.requestDecryptionSlotApplies(
                IssuerTrustSystem.Default, IssuerTrustSystem.EUDI));
        assertTrue(IssuerService.requestDecryptionSlotApplies(
                null, IssuerTrustSystem.Switzerland));
        assertTrue(IssuerService.requestDecryptionSlotApplies(
                IssuerTrustSystem.EUDI, IssuerTrustSystem.EUDI));
        assertTrue(!IssuerService.requestDecryptionSlotApplies(
                IssuerTrustSystem.EUDI, IssuerTrustSystem.Switzerland));
        assertTrue(!IssuerService.requestDecryptionSlotApplies(
                IssuerTrustSystem.Switzerland, IssuerTrustSystem.Default));
        assertTrue(IssuerService.requestDecryptionSlotApplies(
                IssuerTrustSystem.OIDF, IssuerTrustSystem.OIDF));
        assertTrue(!IssuerService.requestDecryptionSlotApplies(
                IssuerTrustSystem.OIDF, IssuerTrustSystem.Custom));
    }

    @Test
    void startupAddsMissingVerifierSlotWithoutReplacingExistingBindings() {
        var identity = new IssuerDefinitionEntity();
        identity.setId(7);
        identity.setTenantId("tenant-a");
        identity.setSlug("issuer");
        var existing = new java.util.ArrayList<IdentityKeySlotEntity>();
        var customKeyId = java.util.UUID.randomUUID();
        for (var trust : List.of(IssuerTrustSystem.EUDI, IssuerTrustSystem.Custom)) {
            for (var type : List.of(IdentityKeySlotType.CREDENTIAL_SIGNING, IdentityKeySlotType.IDENTITY_STATEMENT)) {
                var slot = new IdentityKeySlotEntity();
                slot.setType(type);
                slot.setTrustSystem(trust);
                slot.setKeyId(trust == IssuerTrustSystem.Custom ? customKeyId : java.util.UUID.randomUUID());
                existing.add(slot);
            }
        }
        var data = mock(IssuerDataService.class);
        when(data.findBySlug("issuer")).thenReturn(Optional.of(identity));
        var slots = mock(IdentityKeySlotService.class);
        when(slots.slots(7)).thenReturn(existing);
        var keys = mock(SigningKeyService.class);
        var key = mock(org.heidiverse.heidi.entity.model.entity.SigningKeyEntity.class);
        when(key.getProviderId()).thenReturn(3);
        when(keys.owned("tenant-a", customKeyId)).thenReturn(key);
        var service = new IssuerService(data, mock(CredentialSchemeDataService.class),
                mock(SwissTrustStatementRefreshService.class), mock(SigningProviderService.class), keys,
                mock(SigningGrantService.class), slots, mock(IdentityKeyIntegrityService.class),
                mock(ProofSchemeDataService.class), mock(org.heidiverse.heidi.entity.data.repository.SigningFlowRepository.class));

        service.ensureLocalDevelopmentIssuer("tenant-a", "issuer", "Issuer");

        verify(slots).ensureKeySlot("tenant-a", 7, IdentityKeySlotType.PRESENTATION_SIGNING,
                IssuerTrustSystem.Custom, null, customKeyId, 3);
        verify(keys, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void startupReusesExistingLocalKeyWhenSlotsAreMissing() {
        var identity = new IssuerDefinitionEntity();
        identity.setId(7);
        identity.setTenantId("tenant-a");
        identity.setSlug("issuer");
        var keyId = java.util.UUID.randomUUID();
        var key = mock(org.heidiverse.heidi.entity.model.entity.SigningKeyEntity.class);
        when(key.getId()).thenReturn(keyId);
        when(key.getProviderId()).thenReturn(3);

        var data = mock(IssuerDataService.class);
        when(data.findBySlug("issuer")).thenReturn(Optional.of(identity));
        var slots = mock(IdentityKeySlotService.class);
        when(slots.slots(7)).thenReturn(List.of());
        var keys = mock(SigningKeyService.class);
        when(keys.findOwnedByLogicalKeyId("tenant-a", "issuer"))
                .thenReturn(Optional.of(key));
        when(keys.owned("tenant-a", keyId)).thenReturn(key);
        var service = new IssuerService(data, mock(CredentialSchemeDataService.class),
                mock(SwissTrustStatementRefreshService.class), mock(SigningProviderService.class), keys,
                mock(SigningGrantService.class), slots, mock(IdentityKeyIntegrityService.class),
                mock(ProofSchemeDataService.class), mock(org.heidiverse.heidi.entity.data.repository.SigningFlowRepository.class));

        service.ensureLocalDevelopmentIssuer("tenant-a", "issuer", "Issuer");

        verify(keys, never()).create(any(), any(), any(), any(), any());
        verify(slots).ensureKeySlot("tenant-a", 7, IdentityKeySlotType.CREDENTIAL_SIGNING,
                IssuerTrustSystem.Custom, null, keyId, 3);
        verify(slots).ensureKeySlot("tenant-a", 7, IdentityKeySlotType.IDENTITY_STATEMENT,
                IssuerTrustSystem.Custom, null, keyId, 3);
        verify(slots).ensureKeySlot("tenant-a", 7, IdentityKeySlotType.PRESENTATION_SIGNING,
                IssuerTrustSystem.Custom, null, keyId, 3);
    }

    @ParameterizedTest
    @EnumSource(value = IssuerTrustSystem.class, names = {"Default", "Switzerland"})
    void preservesVersionPublicationId(IssuerTrustSystem trustSystem) {
        var identity = new IssuerDefinitionEntity();
        identity.setId(7);
        identity.setTenantId("tenant-a");
        identity.setSlug("issuer");
        var slot = new IdentityKeySlotEntity();
        slot.setType(IdentityKeySlotType.CREDENTIAL_SIGNING);
        slot.setTrustSystem(trustSystem);
        if (trustSystem == IssuerTrustSystem.Switzerland) {
            slot.setType(IdentityKeySlotType.IDENTITY_STATEMENT);
            slot.setConfiguration(new tools.jackson.databind.ObjectMapper().createObjectNode()
                    .put("swissDid", "did:webvh:scid:example.org"));
        }
        var key = new org.heidiverse.heidi.entity.model.entity.SigningKeyEntity();
        key.setId(java.util.UUID.randomUUID());
        key.setLogicalKeyId("credential");
        key.setProviderId(1);
        var version = new org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity();
        version.setId(java.util.UUID.randomUUID());
        version.setAlgorithm("ES256");
        version.setPublicJwk("{\"kty\":\"EC\",\"kid\":\"credential-v2\"}");
        var issuerData = mock(IssuerDataService.class);
        when(issuerData.findBySlug("issuer")).thenReturn(Optional.of(identity));
        var slots = mock(IdentityKeySlotService.class);
        when(slots.resolve("tenant-a", 7, slot.getType(),
                trustSystem, null, null,
                org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile.CREDENTIAL_SIGNING))
                .thenReturn(Optional.of(new IdentityKeySlotService.ResolvedKey(slot, key, version, List.of())));
        var providers = mock(SigningProviderService.class);
        when(providers.runtimeConfiguration("tenant-a", 1)).thenReturn(mock(
                org.heidiverse.heidi.entity.model.signing.SigningProviderConfiguration.class));
        var credentials = mock(CredentialSchemeDataService.class);
        var scheme = new org.heidiverse.heidi.entity.model.entity.CredentialSchemeEntity();
        scheme.setSigningKeyIds(Map.of(trustSystem, "other-credential-key"));
        when(credentials.findByCredentialIdentifierAndVersion("credential", "1")).thenReturn(Optional.of(scheme));
        var service = new IssuerService(issuerData, credentials,
                mock(SwissTrustStatementRefreshService.class), providers, mock(SigningKeyService.class),
                mock(SigningGrantService.class), slots, mock(IdentityKeyIntegrityService.class),
                mock(ProofSchemeDataService.class),
                mock(org.heidiverse.heidi.entity.data.repository.SigningFlowRepository.class));

        var config = (trustSystem == IssuerTrustSystem.Switzerland
                ? service.getTrustSigningConfiguration("issuer", trustSystem, null, null)
                : service.getSigningConfiguration("issuer", trustSystem)).orElseThrow();

        assertEquals("credential-v2", config.keyId());
        var publishedId = trustSystem == IssuerTrustSystem.Switzerland
                ? "did:webvh:scid:example.org#credential-v2" : "credential-v2";
        assertEquals(publishedId, new tools.jackson.databind.ObjectMapper().readTree(config.issuerJwk()).get("kid").asText());
        if (trustSystem == IssuerTrustSystem.Switzerland) {
            when(slots.slots(7)).thenReturn(List.of(slot));
            when(slots.resolve("tenant-a", 7, IdentityKeySlotType.CREDENTIAL_SIGNING,
                    trustSystem, null, null,
                    org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile.CREDENTIAL_SIGNING))
                    .thenReturn(Optional.of(new IdentityKeySlotService.ResolvedKey(slot, key, version, List.of())));
            var credential = service.getSigningConfiguration("issuer", trustSystem).orElseThrow();
            assertEquals("did:webvh:scid:example.org", credential.issuerClaim());
            assertEquals(publishedId, new tools.jackson.databind.ObjectMapper()
                    .readTree(credential.issuerJwk()).get("kid").asText());
            assertEquals(config, service.getTrustSigningConfiguration("issuer", trustSystem, "credential", "1").orElseThrow());
            assertEquals(config, service.getTrustSigningConfiguration("issuer", trustSystem, null, null, "other-credential-key").orElseThrow());
        }
    }

    @ParameterizedTest
    @EnumSource(value = IssuerTrustSystem.class, names = {"EUDI", "Switzerland"})
    void noCrossFrameworkFallback(IssuerTrustSystem trust) {
        var identity = new IssuerDefinitionEntity();
        identity.setId(7);
        identity.setTenantId("tenant-a");
        var data = mock(IssuerDataService.class);
        when(data.findBySlug("issuer")).thenReturn(Optional.of(identity));
        var slots = mock(IdentityKeySlotService.class);
        var service = new IssuerService(data, mock(CredentialSchemeDataService.class),
                mock(SwissTrustStatementRefreshService.class), mock(SigningProviderService.class),
                mock(SigningKeyService.class), mock(SigningGrantService.class), slots,
                mock(IdentityKeyIntegrityService.class), mock(ProofSchemeDataService.class),
                mock(org.heidiverse.heidi.entity.data.repository.SigningFlowRepository.class));

        service.getSigningConfiguration("issuer", trust);
        service.getTrustSigningConfiguration("issuer", trust, null, null);
        service.getOperationSigningConfiguration("issuer", trust, "test.operation");

        verify(slots, never()).resolve(any(), anyInt(), any(), eq(IssuerTrustSystem.Default),
                any(), any(), any());
    }

    @Test
    void identityResponseExposesTrustSystemsFromSlots() {
        var identity = new IssuerDefinitionEntity();
        identity.setId(7);
        identity.setSlug("issuer");
        identity.setLogo("");
        identity.setDisplayName(Map.of("en", "Issuer"));

        var slot = new IdentityKeySlotEntity();
        slot.setType(IdentityKeySlotType.IDENTITY_STATEMENT);
        slot.setTrustSystem(IssuerTrustSystem.EUDI);

        var issuerData = mock(IssuerDataService.class);
        when(issuerData.findById(7)).thenReturn(Optional.of(identity));
        var slots = mock(IdentityKeySlotService.class);
        when(slots.slots(7)).thenReturn(List.of(slot));

        var service = new IssuerService(
                issuerData, mock(CredentialSchemeDataService.class),
                mock(SwissTrustStatementRefreshService.class), mock(SigningProviderService.class),
                mock(SigningKeyService.class), mock(SigningGrantService.class), slots,
                mock(IdentityKeyIntegrityService.class), mock(ProofSchemeDataService.class),
                mock(org.heidiverse.heidi.entity.data.repository.SigningFlowRepository.class));

        var response = service.findIssuerById(7).orElseThrow();

        assertEquals(Set.of(IssuerTrustSystem.EUDI), response.trustSystems());
    }

    @Test
    void rejectsSigningAlgorithmOutsideProfile() {
        var identity = new IssuerDefinitionEntity();
        identity.setId(7);
        identity.setTenantId("tenant-a");
        identity.setSlug("issuer");

        var slot = new IdentityKeySlotEntity();
        slot.setType(IdentityKeySlotType.CREDENTIAL_SIGNING);
        slot.setTrustSystem(IssuerTrustSystem.Custom);
        slot.setProviderId(1);

        var key = new org.heidiverse.heidi.entity.model.entity.SigningKeyEntity();
        key.setId(java.util.UUID.randomUUID());
        key.setProviderId(1);
        var version = new org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity();
        version.setId(java.util.UUID.randomUUID());
        version.setAlgorithm("RS256");
        version.setPublicJwk("{\"kty\":\"RSA\",\"kid\":\"credential\"}");

        var issuerData = mock(IssuerDataService.class);
        when(issuerData.findBySlug("issuer")).thenReturn(Optional.of(identity));
        var slots = mock(IdentityKeySlotService.class);
        when(slots.resolve("tenant-a", 7, IdentityKeySlotType.CREDENTIAL_SIGNING,
                IssuerTrustSystem.Custom, null, null, SigningCertificateProfile.CREDENTIAL_SIGNING))
                .thenReturn(Optional.of(new IdentityKeySlotService.ResolvedKey(
                        slot, key, version, List.of())));
        var providers = mock(SigningProviderService.class);
        when(providers.runtimeConfiguration("tenant-a", 1)).thenReturn(
                new org.heidiverse.heidi.entity.model.signing.SigningProviderConfiguration(
                        "https://signer.example", null, null, null,
                        List.of("RS256"), List.of(), List.of()));
        var service = new IssuerService(issuerData, mock(CredentialSchemeDataService.class),
                mock(SwissTrustStatementRefreshService.class), providers, mock(SigningKeyService.class),
                mock(SigningGrantService.class), slots, mock(IdentityKeyIntegrityService.class),
                mock(ProofSchemeDataService.class),
                mock(org.heidiverse.heidi.entity.data.repository.SigningFlowRepository.class));

        assertThrows(IllegalArgumentException.class, () -> service.getSigningConfiguration(
                "issuer", IssuerTrustSystem.Default, null, null, null,
                EcosystemProfileId.CUSTOM_ISSUANCE_2026_1));
    }

    @Test
    void presentationDoesNotUseCredentialSigningMaterial() {
        var identity = new IssuerDefinitionEntity();
        identity.setId(7);
        identity.setTenantId("tenant-a");
        identity.setSlug("issuer");

        var issuerData = mock(IssuerDataService.class);
        when(issuerData.findBySlug("issuer")).thenReturn(Optional.of(identity));
        var slots = mock(IdentityKeySlotService.class);
        when(slots.resolve("tenant-a", 7, IdentityKeySlotType.PRESENTATION_SIGNING,
                IssuerTrustSystem.Default, null, null,
                SigningCertificateProfile.CREDENTIAL_SIGNING))
                .thenReturn(Optional.empty());
        var providers = mock(SigningProviderService.class);
        var service = new IssuerService(issuerData, mock(CredentialSchemeDataService.class),
                mock(SwissTrustStatementRefreshService.class), providers, mock(SigningKeyService.class),
                mock(SigningGrantService.class), slots, mock(IdentityKeyIntegrityService.class),
                mock(ProofSchemeDataService.class),
                mock(org.heidiverse.heidi.entity.data.repository.SigningFlowRepository.class));

        var config = service.getPresentationSigningConfiguration(
                "issuer", IssuerTrustSystem.Default, null,
                EcosystemProfileId.CUSTOM_PRESENTATION_2026_1);

        assertTrue(config.isEmpty());
    }

    @Test
    void swissPresentationUsesDidFromTrustSettings() {
        var identity = new IssuerDefinitionEntity();
        identity.setId(7);
        identity.setTenantId("tenant-a");
        identity.setSlug("issuer");

        var trustSlot = new IdentityKeySlotEntity();
        trustSlot.setType(IdentityKeySlotType.IDENTITY_STATEMENT);
        trustSlot.setTrustSystem(IssuerTrustSystem.Switzerland);
        trustSlot.setConfiguration(new tools.jackson.databind.ObjectMapper().createObjectNode()
                .put("swissDid", "did:webvh:scid:example.org"));

        var presentationSlot = new IdentityKeySlotEntity();
        presentationSlot.setType(IdentityKeySlotType.PRESENTATION_SIGNING);
        presentationSlot.setTrustSystem(IssuerTrustSystem.Switzerland);
        presentationSlot.setProviderId(1);

        var key = new org.heidiverse.heidi.entity.model.entity.SigningKeyEntity();
        key.setId(UUID.randomUUID());
        key.setProviderId(1);
        var version = requestVersion(key.getId(), "presentation");
        version.setAlgorithm("ES256");
        var issuerData = mock(IssuerDataService.class);
        when(issuerData.findBySlug("issuer")).thenReturn(Optional.of(identity));
        var slots = mock(IdentityKeySlotService.class);
        when(slots.slots(7)).thenReturn(List.of(trustSlot, presentationSlot));
        when(slots.resolve("tenant-a", 7, IdentityKeySlotType.PRESENTATION_SIGNING,
                IssuerTrustSystem.Switzerland, null, null,
                SigningCertificateProfile.CREDENTIAL_SIGNING))
                .thenReturn(Optional.of(new IdentityKeySlotService.ResolvedKey(
                        presentationSlot, key, version, List.of())));
        var providers = mock(SigningProviderService.class);
        when(providers.runtimeConfiguration("tenant-a", 1)).thenReturn(
                new org.heidiverse.heidi.entity.model.signing.SigningProviderConfiguration(
                        "https://signer.example", null, "NONE", null,
                        List.of("ES256"), List.of(), List.of(), List.of(), false, false, false));
        var service = new IssuerService(issuerData, mock(CredentialSchemeDataService.class),
                mock(SwissTrustStatementRefreshService.class), providers, mock(SigningKeyService.class),
                mock(SigningGrantService.class), slots, mock(IdentityKeyIntegrityService.class),
                mock(ProofSchemeDataService.class),
                mock(org.heidiverse.heidi.entity.data.repository.SigningFlowRepository.class));

        var config = service.getPresentationSigningConfiguration(
                "issuer", IssuerTrustSystem.Switzerland, null,
                EcosystemProfileId.SWISS_PRESENTATION_2026_1).orElseThrow();

        assertEquals("did:webvh:scid:example.org", config.issuerClaim());
        assertEquals("did:webvh:scid:example.org#presentation",
                new tools.jackson.databind.ObjectMapper().readTree(config.issuerJwk()).get("kid").asText());
    }

    @Test
    void oidfPresentationUsesOidfVerifierMaterial() {
        var identity = new IssuerDefinitionEntity();
        identity.setId(7);
        identity.setTenantId("tenant-a");
        identity.setSlug("issuer");

        var issuerData = mock(IssuerDataService.class);
        when(issuerData.findBySlug("issuer")).thenReturn(Optional.of(identity));
        var slots = mock(IdentityKeySlotService.class);
        when(slots.resolve("tenant-a", 7, IdentityKeySlotType.PRESENTATION_SIGNING,
                IssuerTrustSystem.OIDF, null, null, SigningCertificateProfile.CREDENTIAL_SIGNING))
                .thenReturn(Optional.empty());
        var service = new IssuerService(issuerData, mock(CredentialSchemeDataService.class),
                mock(SwissTrustStatementRefreshService.class), mock(SigningProviderService.class),
                mock(SigningKeyService.class), mock(SigningGrantService.class), slots,
                mock(IdentityKeyIntegrityService.class), mock(ProofSchemeDataService.class),
                mock(org.heidiverse.heidi.entity.data.repository.SigningFlowRepository.class));

        assertTrue(service.getPresentationSigningConfiguration(
                "issuer", IssuerTrustSystem.OIDF, null,
                EcosystemProfileId.OIDF_PRESENTATION_2026_1).isEmpty());
        verify(slots).resolve("tenant-a", 7, IdentityKeySlotType.PRESENTATION_SIGNING,
                IssuerTrustSystem.OIDF, null, null, SigningCertificateProfile.CREDENTIAL_SIGNING);
    }

    @Test
    void credentialEncryptionKeepsNullRequiredFlagsForUnrestrictedProfile() {
        var identity = new IssuerDefinitionEntity();
        identity.setId(7);
        identity.setTenantId("tenant-a");
        identity.setSlug("issuer");

        var issuerData = mock(IssuerDataService.class);
        when(issuerData.findBySlug("issuer")).thenReturn(Optional.of(identity));
        var service = new IssuerService(issuerData, mock(CredentialSchemeDataService.class),
                mock(SwissTrustStatementRefreshService.class), mock(SigningProviderService.class),
                mock(SigningKeyService.class), mock(SigningGrantService.class), mock(IdentityKeySlotService.class),
                mock(IdentityKeyIntegrityService.class), mock(ProofSchemeDataService.class),
                mock(org.heidiverse.heidi.entity.data.repository.SigningFlowRepository.class));

        var policy = service.getCredentialEncryption("issuer", EcosystemProfileId.CUSTOM_ISSUANCE_2026_1)
                .orElseThrow();

        assertEquals(null, policy.requestEncryptionRequired());
        assertEquals(null, policy.responseEncryptionRequired());
    }
}
