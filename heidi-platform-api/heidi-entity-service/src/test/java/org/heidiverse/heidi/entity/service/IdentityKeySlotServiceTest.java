// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.heidiverse.heidi.entity.data.repository.IdentityKeySlotRepository;
import org.heidiverse.heidi.entity.data.repository.IssuerDefinitionRepository;
import org.heidiverse.heidi.entity.data.repository.SigningCertificateRepository;
import org.heidiverse.heidi.entity.data.repository.SigningKeyRepository;
import org.heidiverse.heidi.entity.data.repository.SigningKeyVersionRepository;
import org.heidiverse.heidi.entity.data.repository.SigningProviderRepository;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity;
import org.heidiverse.heidi.entity.model.entity.SigningProviderEntity;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotRequest;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.shared.signing.SigningKeyCapabilities;
import org.heidiverse.heidi.shared.signing.SigningKeyProvider;
import org.heidiverse.heidi.shared.signing.SigningKeyRef;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;
import org.junit.jupiter.api.Test;

class IdentityKeySlotServiceTest {
    private final IdentityKeySlotRepository slots = mock(IdentityKeySlotRepository.class);
    private final IssuerDefinitionRepository identities = mock(IssuerDefinitionRepository.class);
    private final SigningKeyRepository keys = mock(SigningKeyRepository.class);
    private final SigningKeyVersionRepository versions = mock(SigningKeyVersionRepository.class);
    private final SigningCertificateRepository certificates = mock(SigningCertificateRepository.class);
    private final SigningProviderRepository providers = mock(SigningProviderRepository.class);
    private final SigningProviderService providerService = mock(SigningProviderService.class);
    private final SwissKeyPublication swissKeys = mock(SwissKeyPublication.class);
    private final org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService credentials = mock(org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService.class);
    private final org.heidiverse.heidi.entity.data.service.ProofSchemeDataService proofs = mock(org.heidiverse.heidi.entity.data.service.ProofSchemeDataService.class);
    private final org.heidiverse.heidi.entity.data.repository.IdentityKeyPublicationRepository publications = mock(org.heidiverse.heidi.entity.data.repository.IdentityKeyPublicationRepository.class);
    private final IdentityKeySlotService service = new IdentityKeySlotService(
            slots, identities, keys, versions, certificates, providers, providerService, credentials, proofs, publications,
            swissKeys);

    @Test
    void mapsEudiCertificateRolesToProtocolPurposes() {
        assertEquals(
                org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile.CREDENTIAL_SIGNING,
                IdentityKeySlotType.CREDENTIAL_SIGNING.certificateProfile(IssuerTrustSystem.EUDI));
        assertEquals(
                org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile.ACCESS,
                IdentityKeySlotType.IDENTITY_STATEMENT.certificateProfile(IssuerTrustSystem.EUDI));
        assertEquals(
                org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile.ACCESS,
                IdentityKeySlotType.PRESENTATION_SIGNING.certificateProfile(IssuerTrustSystem.EUDI));
    }

    @Test
    void sourceIsNotPartOfPersistentSlotModel() {
        assertThrows(
                NoSuchFieldException.class,
                () -> IdentityKeySlotEntity.class.getDeclaredField("source"));
    }

    @Test
    void rejectsUnpublishedSwissKey() {
        var key = configuredKey();
        var configuration = new tools.jackson.databind.ObjectMapper().createObjectNode()
                .put("swissDid", "did:webvh:scid:example.org");
        doThrow(new IllegalArgumentException("Key not published")).when(swissKeys).requirePublished(any(), any());

        assertThrows(IllegalArgumentException.class, () -> service.save("tenant-a", 1,
                new IdentityKeySlotRequest(null, IdentityKeySlotType.IDENTITY_STATEMENT,
                        IssuerTrustSystem.Switzerland, null, key.getId(), null, null, null, configuration)));
        verify(swissKeys).requirePublished(eq("did:webvh:scid:example.org"), any());
        verify(slots, never()).saveAndFlush(any());
    }

    @Test
    void didChangeChecksCredentialKeys() {
        var identityKey = configuredKey();
        var credentialKey = configuredKey();
        var credential = new IdentityKeySlotEntity();
        credential.setIdentityId(1);
        credential.setType(IdentityKeySlotType.CREDENTIAL_SIGNING);
        credential.setTrustSystem(IssuerTrustSystem.Switzerland);
        credential.setKeyId(credentialKey.getId());
        when(slots.findAllByIdentityIdOrderByTypeAscOrderAsc(1)).thenReturn(List.of(credential));
        var version = versions.findById(credentialKey.getActiveVersionId()).orElseThrow();
        version.setPublicJwk("credential-public-key");
        var did = "did:webvh:new:example.org";
        doThrow(new IllegalArgumentException("Credential key not published"))
                .when(swissKeys).requirePublished(did, version.getPublicJwk());
        var configuration = new tools.jackson.databind.ObjectMapper().createObjectNode().put("swissDid", did);

        assertThrows(IllegalArgumentException.class, () -> service.save("tenant-a", 1,
                new IdentityKeySlotRequest(null, IdentityKeySlotType.IDENTITY_STATEMENT,
                        IssuerTrustSystem.Switzerland, null, identityKey.getId(), null, null, null, configuration)));
        verify(slots, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSwissWithoutDid() {
        var key = configuredKey();
        assertThrows(IllegalArgumentException.class, () -> service.save("tenant-a", 1,
                new IdentityKeySlotRequest(null, IdentityKeySlotType.IDENTITY_STATEMENT,
                        IssuerTrustSystem.Switzerland, null, key.getId(), null, null, null, null)));
        verify(slots, never()).saveAndFlush(any());
    }

    @Test
    void refusesUnacceptedIssuerClient() {
        var key = configuredKey();
        org.mockito.Mockito.doThrow(new IllegalArgumentException("issuer client missing"))
                .when(providerService).requireClient("tenant-a", 7,
                        org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotConsumer.ISSUER);

        assertThrows(IllegalArgumentException.class, () -> service.save("tenant-a", 1,
                new IdentityKeySlotRequest(null, IdentityKeySlotType.CREDENTIAL_SIGNING,
                        IssuerTrustSystem.Default, null, key.getId(), null, null, null, null)));
        verify(slots, never()).saveAndFlush(any());
    }

    @Test
    void checksTheIdentityConsumer() {
        var key = configuredKey();
        when(proofs.findActiveVerifierIdentityIds()).thenReturn(java.util.Set.of(1));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("verifier client missing"))
                .when(providerService).requireClient("tenant-a", 7,
                        org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotConsumer.VERIFIER);

        assertThrows(IllegalArgumentException.class, () -> service.save("tenant-a", 1,
                new IdentityKeySlotRequest(null, IdentityKeySlotType.IDENTITY_STATEMENT,
                        IssuerTrustSystem.Default, null, key.getId(), null, null, null, null)));
        verify(slots, never()).saveAndFlush(any());
        verify(providerService, never()).requireClient("tenant-a", 7,
                org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotConsumer.ISSUER);
    }

    @Test
    void requiresOnlySelectedSlotClients() {
        var identity = identity(1, "tenant-a");
        var credential = new IdentityKeySlotEntity();
        credential.setIdentityId(1);
        credential.setType(IdentityKeySlotType.CREDENTIAL_SIGNING);
        credential.setTrustSystem(IssuerTrustSystem.Default);
        credential.setProviderId(7);
        var presentation = new IdentityKeySlotEntity();
        presentation.setIdentityId(1);
        presentation.setType(IdentityKeySlotType.PRESENTATION_SIGNING);
        presentation.setTrustSystem(IssuerTrustSystem.Default);
        presentation.setProviderId(8);
        when(identities.findById(1)).thenReturn(Optional.of(identity));
        when(slots.findAllByIdentityIdOrderByTypeAscOrderAsc(1))
                .thenReturn(List.of(credential, presentation));
        doThrow(new IllegalArgumentException("verifier client missing"))
                .when(providerService).requireClient("tenant-a", 8,
                        org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotConsumer.ISSUER);

        assertDoesNotThrow(() -> service.requireClient(1,
                org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotConsumer.ISSUER,
                Set.of(IdentityKeySlotType.CREDENTIAL_SIGNING)));
    }

    @Test
    void rejectsEudiWithoutCertificate() {
        var key = configuredKey();
        assertThrows(IllegalArgumentException.class, () -> service.save("tenant-a", 1,
                new IdentityKeySlotRequest(null, IdentityKeySlotType.CREDENTIAL_SIGNING,
                        IssuerTrustSystem.EUDI, null, key.getId(), null, null, null, null)));
        verify(slots, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAnotherTrustFramework() {
        var key = configuredKey();
        var certificate = certificate(key);
        certificate.setTrustSystem(IssuerTrustSystem.Switzerland);
        assertThrows(IllegalArgumentException.class, () -> service.save("tenant-a", 1,
                new IdentityKeySlotRequest(null, IdentityKeySlotType.CREDENTIAL_SIGNING,
                        IssuerTrustSystem.EUDI, null, key.getId(), null, certificate.getId(), null, null)));
        verify(slots, never()).saveAndFlush(any());
    }

    @Test
    void rejectsExpiredSelectedChain() {
        var key = configuredKey();
        var certificate = certificate(key);
        certificate.setNotAfter(java.time.Instant.now().minusSeconds(1));
        var slot = new IdentityKeySlotEntity();
        slot.setType(IdentityKeySlotType.CREDENTIAL_SIGNING);
        slot.setTrustSystem(IssuerTrustSystem.EUDI);
        slot.setKeyId(key.getId());
        slot.setCertificateId(certificate.getId());
        when(slots.findAllByIdentityIdOrderByTypeAscOrderAsc(1)).thenReturn(List.of(slot));
        assertThrows(IllegalArgumentException.class, () -> service.resolve("tenant-a", 1,
                IdentityKeySlotType.CREDENTIAL_SIGNING, IssuerTrustSystem.EUDI, null, null));
    }

    private SigningKeyEntity configuredKey() {
        var key = key(UUID.randomUUID(), "tenant-a", 7);
        var version = activeVersion(key);
        when(identities.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(identity(1, "tenant-a")));
        when(keys.findById(key.getId())).thenReturn(Optional.of(key));
        when(keys.findByIdForUpdate(key.getId())).thenReturn(Optional.of(key));
        when(versions.findById(version.getId())).thenReturn(Optional.of(version));
        when(slots.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return key;
    }

    private org.heidiverse.heidi.entity.model.entity.SigningCertificateEntity certificate(SigningKeyEntity key) {
        var certificate = new org.heidiverse.heidi.entity.model.entity.SigningCertificateEntity();
        certificate.setId(UUID.randomUUID());
        certificate.setKeyVersionId(key.getActiveVersionId());
        certificate.setProfile(org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile.CREDENTIAL_SIGNING);
        certificate.setTrustSystem(IssuerTrustSystem.EUDI);
        certificate.setCertificateChain(List.of("leaf", "root"));
        certificate.setNotBefore(java.time.Instant.now().minusSeconds(60));
        certificate.setNotAfter(java.time.Instant.now().plusSeconds(3600));
        when(certificates.findById(certificate.getId())).thenReturn(Optional.of(certificate));
        return certificate;
    }

    @Test
    void globalIdentityMayUseGlobalKey() {
        var identity = identity(1, null);
        var key = key(UUID.randomUUID(), null, 7);
        var version = activeVersion(key);
        when(identities.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(identity));
        when(keys.findById(key.getId())).thenReturn(Optional.of(key));
        when(keys.findByIdForUpdate(key.getId())).thenReturn(Optional.of(key));
        when(versions.findById(version.getId())).thenReturn(Optional.of(version));
        when(slots.findAllByIdentityIdOrderByTypeAscOrderAsc(1)).thenReturn(List.of());
        when(slots.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.save(null, 1, new IdentityKeySlotRequest(
                null, IdentityKeySlotType.CREDENTIAL_SIGNING, IssuerTrustSystem.Default,
                null, key.getId(), null, null, null, null));

        verify(slots).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateRequestDecryptionKeyInSameTrustFramework() {
        var identity = identity(1, "tenant-a");
        var key = configuredKey();
        var version = versions.findById(key.getActiveVersionId()).orElseThrow();
        version.setPublicJwk("{\"kty\":\"EC\",\"crv\":\"P-256\","
                + "\"x\":\"Gym2Ga9-WGyE3T5EJGftqE-MpMSJAD-uzGFNkzN5VW8\","
                + "\"y\":\"Blxn144RNJNFKxKy4TuQFfpFoRJjC9mnraZ-I-GMpJU\"}");
        var existing = new IdentityKeySlotEntity();
        existing.setId(UUID.randomUUID());
        existing.setIdentityId(1);
        existing.setType(IdentityKeySlotType.DECRYPTION);
        existing.setTrustSystem(IssuerTrustSystem.Switzerland);
        existing.setKeyId(key.getId());
        when(identities.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(identity));
        when(slots.findAllByIdentityIdOrderByTypeAscOrderAsc(1)).thenReturn(List.of(existing));
        when(providerService.capabilities("tenant-a", 7)).thenReturn(new SigningKeyCapabilities(
                "test", false, false, false, List.of(), List.of(), List.of(), List.of(),
                List.of("ECDH-ES")));
        var provider = mock(SigningKeyProvider.class);
        when(providerService.provider("tenant-a", 7)).thenReturn(provider);
        when(provider.resolve(version.getKeyUri())).thenReturn(new SigningKeyRef(
                version.getKeyUri(), version.getPublicJwk(), version.getAlgorithm(),
                Set.of(SigningKeyUsage.KEY_AGREEMENT)));

        var exception = assertThrows(IllegalArgumentException.class, () -> service.save("tenant-a", 1,
                new IdentityKeySlotRequest(null, IdentityKeySlotType.DECRYPTION,
                        IssuerTrustSystem.Switzerland, null, key.getId(), null, null, null, null)));
        assertEquals("Identity already has this request decryption key for this trust framework",
                exception.getMessage());
        verify(slots, never()).saveAndFlush(any());
    }

    @Test
    void tenantCannotMutateGlobalIdentitySlots() {
        var identity = identity(1, null);
        when(identities.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(identity));

        assertThrows(SecurityException.class, () -> service.save("tenant-a", 1,
                new IdentityKeySlotRequest(
                        null, IdentityKeySlotType.OPERATION, IssuerTrustSystem.Default,
                        "operation", null, 7, null, null, null)));
        verifyNoInteractions(slots);
    }

    @Test
    void assignmentKeepsConfiguration() {
        var identity = identity(1, "tenant-a");
        var provider = new SigningProviderEntity();
        var slot = new IdentityKeySlotEntity();
        slot.setId(UUID.randomUUID());
        slot.setIdentityId(1);
        slot.setType(IdentityKeySlotType.OPERATION);
        slot.setTrustSystem(IssuerTrustSystem.Default);
        slot.setOperation("test.operation");
        slot.setProviderId(7);
        slot.setOrder(2);
        var configuration = new tools.jackson.databind.ObjectMapper().readTree("{\"policy\":\"retained\"}");
        slot.setConfiguration(configuration);
        when(identities.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(identity));
        when(providers.findById(7)).thenReturn(Optional.of(provider));
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));
        when(slots.findAllByIdentityIdOrderByTypeAscOrderAsc(1)).thenReturn(List.of(slot));
        when(slots.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.save("tenant-a", 1, new IdentityKeySlotRequest(slot.getId(), slot.getType(),
                slot.getTrustSystem(), slot.getOperation(), null, 7, null, null, null));

        assertEquals(configuration, slot.getConfiguration());
        assertEquals(2, slot.getOrder());
    }

    @Test
    void operationProviderMustBelongToIdentity() {
        var identity = identity(1, "tenant-a");
        var provider = new SigningProviderEntity();
        provider.setTenantId("tenant-b");
        when(identities.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(identity));
        when(providers.findById(7)).thenReturn(Optional.of(provider));

        assertThrows(SecurityException.class, () -> service.save("tenant-a", 1,
                new IdentityKeySlotRequest(
                        null, IdentityKeySlotType.OPERATION, IssuerTrustSystem.Default,
                        "operation", null, 7, null, null, null)));
    }

    @Test
    void tenantIdentityMayUseGlobalProvider() {
        var identity = identity(1, "tenant-a");
        var key = key(UUID.randomUUID(), "tenant-a", 7);
        var version = activeVersion(key);
        var provider = new SigningProviderEntity();
        provider.setTenantId(null);
        when(identities.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(identity));
        when(keys.findById(key.getId())).thenReturn(Optional.of(key));
        when(keys.findByIdForUpdate(key.getId())).thenReturn(Optional.of(key));
        when(versions.findById(version.getId())).thenReturn(Optional.of(version));
        when(providers.findById(7)).thenReturn(Optional.of(provider));
        when(slots.findAllByIdentityIdOrderByTypeAscOrderAsc(1)).thenReturn(List.of());
        when(slots.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.save("tenant-a", 1, new IdentityKeySlotRequest(
                null, IdentityKeySlotType.CREDENTIAL_SIGNING, IssuerTrustSystem.Default,
                null, key.getId(), 7, null, null, null));

        verify(slots).saveAndFlush(any());
    }

    @Test
    void resolvesOnlyTheActiveVersionOfAnOwnedLogicalKey() {
        var identity = identity(1, "tenant-a");
        var keyId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var key = key(keyId, "tenant-a", 7);
        key.setLogicalKeyId("credential");
        key.setActiveVersionId(versionId);
        var version = new SigningKeyVersionEntity();
        version.setId(versionId);
        version.setKeyId(keyId);
        version.setKeyUri("software://kc/" + keyId + "/" + versionId);
        version.setAlgorithm("ES256");
        version.setStatus(SigningKeyService.ACTIVE);
        var slot = new IdentityKeySlotEntity();
        slot.setIdentityId(1);
        slot.setType(IdentityKeySlotType.CREDENTIAL_SIGNING);
        slot.setTrustSystem(IssuerTrustSystem.Default);
        slot.setKeyId(keyId);

        when(identities.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(identity));
        when(slots.findAllByIdentityIdOrderByTypeAscOrderAsc(1)).thenReturn(List.of(slot));
        when(keys.findById(keyId)).thenReturn(Optional.of(key));
        when(keys.findByIdForUpdate(keyId)).thenReturn(Optional.of(key));
        when(versions.findById(versionId)).thenReturn(Optional.of(version));

        var resolved = service.resolve("tenant-a", 1, IdentityKeySlotType.CREDENTIAL_SIGNING,
                IssuerTrustSystem.Default, null, "credential");

        assertEquals(versionId, resolved.orElseThrow().version().getId());
        assertEquals("credential", resolved.orElseThrow().key().getLogicalKeyId());
    }

    @Test
    void resolvesAStatusKeyByItsKey() {
        var keyId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var key = key(keyId, "tenant-a", 7);
        key.setActiveVersionId(versionId);
        var version = new SigningKeyVersionEntity();
        version.setId(versionId);
        version.setKeyId(keyId);
        version.setStatus(SigningKeyService.ACTIVE);
        var slot = new IdentityKeySlotEntity();
        slot.setType(IdentityKeySlotType.STATUS_LIST);

        when(keys.findById(keyId)).thenReturn(Optional.of(key));
        when(keys.findByIdForUpdate(keyId)).thenReturn(Optional.of(key));
        when(slots.findAllByKeyId(keyId)).thenReturn(List.of(slot));
        when(versions.findById(versionId)).thenReturn(Optional.of(version));

        var resolved = service.resolveByKey(
                "tenant-a", keyId, IdentityKeySlotType.STATUS_LIST);

        assertEquals(versionId, resolved.orElseThrow().version().getId());
    }

    private static IssuerDefinitionEntity identity(int id, String tenantId) {
        var identity = new IssuerDefinitionEntity();
        identity.setId(id);
        identity.setTenantId(tenantId);
        return identity;
    }

    private static SigningKeyEntity key(UUID id, String tenantId, int providerId) {
        var key = new SigningKeyEntity();
        key.setId(id);
        key.setTenantId(tenantId);
        key.setProviderId(providerId);
        return key;
    }

    private static SigningKeyVersionEntity activeVersion(SigningKeyEntity key) {
        var version = new SigningKeyVersionEntity();
        version.setId(UUID.randomUUID());
        version.setKeyId(key.getId());
        version.setKeyUri("software://kc/" + key.getId() + "/" + version.getId());
        version.setAlgorithm("ES256");
        version.setStatus(SigningKeyService.ACTIVE);
        key.setActiveVersionId(version.getId());
        return version;
    }
}
