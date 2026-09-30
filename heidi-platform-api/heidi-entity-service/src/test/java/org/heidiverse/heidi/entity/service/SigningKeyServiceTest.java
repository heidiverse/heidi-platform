// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.heidiverse.heidi.entity.data.repository.SigningKeyVersionRepository;
import org.heidiverse.heidi.entity.data.repository.SigningKeyRepository;
import org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyEntity;
import org.heidiverse.heidi.entity.model.entity.SigningProviderEntity;
import org.heidiverse.heidi.shared.signing.ProviderHealth;
import org.heidiverse.heidi.shared.signing.SigningKeyCreator;
import org.heidiverse.heidi.shared.signing.SigningKeyDeleter;
import org.heidiverse.heidi.shared.signing.SigningKeyProvider;
import org.heidiverse.heidi.shared.signing.SigningKeyRef;
import org.heidiverse.heidi.shared.signing.SigningKeyImporter;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SigningKeyServiceTest {
    private static final String TENANT_ID = "tenant-a";

    @Mock private SigningKeyRepository keyRepository;
    @Mock private SigningKeyVersionRepository versionRepository;
    @Mock private SigningProviderService providerService;
    @Mock private IdentityKeySlotService identitySlots;
    @Mock private org.heidiverse.heidi.entity.data.repository.SigningFlowRepository signingFlows;
    @Mock private org.heidiverse.heidi.entity.data.repository.IdentityKeySlotRepository slotRepository;
    @Mock private org.heidiverse.heidi.entity.data.repository.SigningCertificateRepository certificateRepository;

    private final List<SigningKeyVersionEntity> versions = new ArrayList<>();
    private final List<SigningKeyEntity> keys = new ArrayList<>();
    private final SigningProviderEntity providerEntity = mock(SigningProviderEntity.class);
    private final TestProvider provider = new TestProvider();
    private SigningKeyService service;

    @Test
    void unpublishedVersionStaysPrepared() {
        var first = service.create(TENANT_ID, "issuer", "ES256", 7, null);
        var prepared = service.prepareRotation(TENANT_ID, first.keyId());
        var slot = new org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity();
        when(slotRepository.findAllByKeyId(first.keyId())).thenReturn(List.of(slot));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Key not published"))
                .when(identitySlots).requirePublication(any(), any());

        assertThrows(IllegalArgumentException.class, () -> service.activate(TENANT_ID, first.keyId(), prepared.keyVersionId()));
        assertEquals(first.keyVersionId(), keys.getFirst().getActiveVersionId());
        assertEquals(SigningKeyService.PREPARED, service.version(TENANT_ID, first.keyId(), prepared.keyVersionId()).getStatus());
    }

    @Test
    void retirementKeepsGrace() {
        var first = service.create(TENANT_ID, "issuer", "ES256", 7, null);
        service.retire(TENANT_ID, first.keyId(), first.keyVersionId());
        var version = versions.getFirst();
        var grace = version.getPreviousUntil();
        service.retire(TENANT_ID, first.keyId(), first.keyVersionId());

        assertEquals(SigningKeyService.PREVIOUS, version.getStatus());
        assertTrue(grace.isAfter(Instant.now()));
        assertEquals(grace, version.getPreviousUntil());
        assertEquals(null, keys.getFirst().getActiveVersionId());
        assertTrue(provider.deletedKeyUris.isEmpty());
        var next = service.prepareRotation(TENANT_ID, first.keyId());
        service.activate(TENANT_ID, first.keyId(), next.keyVersionId());
        assertEquals(next.keyVersionId(), keys.getFirst().getActiveVersionId());
    }

    @Test
    void revocationKeepsPublicMaterial() {
        var first = service.create(TENANT_ID, "issuer", "ES256", 7, null);
        var publicJwk = versions.getFirst().getPublicJwk();
        service.revoke(TENANT_ID, first.keyId(), first.keyVersionId());
        service.revoke(TENANT_ID, first.keyId(), first.keyVersionId());

        assertEquals(List.of(first.keyUri()), provider.revokedKeyUris);
        assertEquals(SigningKeyService.REVOKED, versions.getFirst().getStatus());
        assertEquals(publicJwk, versions.getFirst().getPublicJwk());
        assertEquals(null, keys.getFirst().getActiveVersionId());
        assertTrue(provider.deletedKeyUris.isEmpty());
    }

    @Test
    void failedRevokeKeepsActiveVersion() {
        var first = service.create(TENANT_ID, "issuer", "ES256", 7, null);
        provider.failRevoke = true;

        assertThrows(IllegalStateException.class, () -> service.revoke(TENANT_ID, first.keyId(), first.keyVersionId()));
        assertEquals(SigningKeyService.ACTIVE, versions.getFirst().getStatus());
        assertEquals(first.keyVersionId(), keys.getFirst().getActiveVersionId());
    }

    @BeforeEach
    void setUp() {
        lenient().when(providerEntity.getId()).thenReturn(7);
        lenient().when(providerService.resolve(TENANT_ID, 7)).thenReturn(providerEntity);
        lenient().when(providerService.provider(TENANT_ID, 7)).thenReturn(provider);
        lenient().when(keyRepository.save(any(SigningKeyEntity.class)))
                .thenAnswer(invocation -> {
                    var key = invocation.<SigningKeyEntity>getArgument(0);
                    keys.removeIf(existing -> existing.getId().equals(key.getId()));
                    keys.add(key);
                    return key;
                });
        lenient().when(keyRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> keys.stream()
                        .filter(key -> key.getId().equals(invocation.getArgument(0)))
                        .findFirst());
        lenient().when(keyRepository.findByIdForUpdate(any(UUID.class)))
                .thenAnswer(invocation -> keys.stream()
                        .filter(key -> key.getId().equals(invocation.getArgument(0)))
                        .findFirst());
        lenient().when(versionRepository.save(any(SigningKeyVersionEntity.class)))
                .thenAnswer(invocation -> {
                    var version = invocation.<SigningKeyVersionEntity>getArgument(0);
                    versions.removeIf(existing -> existing.getId().equals(version.getId()));
                    versions.add(version);
                    return version;
                });
        lenient().doAnswer(invocation -> {
            versions.remove(invocation.<SigningKeyVersionEntity>getArgument(0));
            return null;
        }).when(versionRepository).delete(any(SigningKeyVersionEntity.class));
        lenient().when(versionRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> versions.stream()
                        .filter(version -> version.getId().equals(invocation.getArgument(0)))
                        .findFirst());
        lenient().when(versionRepository.findAllByKeyIdOrderByVersion(any(UUID.class)))
                .thenAnswer(invocation -> versions.stream()
                        .filter(version -> version.getKeyId().equals(invocation.getArgument(0)))
                        .sorted(java.util.Comparator.comparingInt(SigningKeyVersionEntity::getVersion))
                        .toList());
        service = new SigningKeyService(
                keyRepository, versionRepository, providerService, new ObjectMapper(),
                certificateRepository, slotRepository, signingFlows, identitySlots);
    }

    @Test
    void refusesReferencedKeyDeletion() {
        var key = service.create(TENANT_ID, "retained", "ES256", 7, null);
        // Provider deletion cannot be rolled back when a database reference rejects the delete.
        lenient().when(signingFlows.referencesKey(key.keyId())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.delete(TENANT_ID, key.keyId()));
        assertTrue(provider.deletedKeyUris.isEmpty());
        org.mockito.Mockito.verify(keyRepository, never()).delete(any());
    }

    @Test
    void createsUuidNamespacedProviderReferenceAndLogicalKid() {
        var provisioned = service.create(
                TENANT_ID, "issuer-signing", "ES256", 7, null);

        assertTrue(provisioned.keyUri().matches(
                "software://kc/[0-9a-f-]{36}/[0-9a-f-]{36}"));
        assertEquals("issuer-signing-v1", new ObjectMapper()
                .readTree(provisioned.publicJwk()).path("kid").asText());
        assertEquals(provisioned.keyVersionId(), capturedKey().getActiveVersionId());
    }

    @Test
    void activeVersionUsesNonLockingKeyLookup() {
        var provisioned = service.create(TENANT_ID, "issuer-signing", "ES256", 7, null);

        assertEquals(
                provisioned.keyVersionId(),
                service.activeVersion(TENANT_ID, provisioned.keyId()).getId());
        org.mockito.Mockito.verify(keyRepository).findById(provisioned.keyId());
        org.mockito.Mockito.verify(keyRepository, never())
                .findByIdForUpdate(provisioned.keyId());
    }

    @Test
    void activeVersionRejectsVersionWithNonActiveStatus() {
        var provisioned = service.create(TENANT_ID, "issuer-signing", "ES256", 7, null);
        versions.getFirst().setStatus(SigningKeyService.PREVIOUS);

        assertThrows(
                IllegalStateException.class,
                () -> service.activeVersion(TENANT_ID, provisioned.keyId()));
    }

    @Test
    void rotationPreparesANewVersionWithoutChangingTheActiveVersion() throws Exception {
        var first = service.create(TENANT_ID, "issuer-signing", "ES256", 7, null);
        var second = service.prepareRotation(TENANT_ID, first.keyId());

        assertEquals(2, second.version());
        assertEquals("issuer-signing-v2", new ObjectMapper()
                .readTree(second.publicJwk()).path("kid").asText());
        assertFalse(first.keyUri().equals(second.keyUri()));
        assertEquals(first.keyVersionId(), capturedKey().getActiveVersionId());
        assertEquals(SigningKeyService.ACTIVE, versions.getFirst().getStatus());
        assertEquals(SigningKeyService.PREPARED, versions.getLast().getStatus());
        assertTrue(service.previousPublicJwks(TENANT_ID, first.keyId(), first.keyVersionId()).isEmpty());
    }

    @Test
    void activationSwitchesVersionsAndPublishesOnlyThePreviousKey() {
        var first = service.create(TENANT_ID, "issuer-signing", "ES256", 7, null);
        var prepared = service.prepareRotation(TENANT_ID, first.keyId());

        var second = service.activate(TENANT_ID, first.keyId(), prepared.keyVersionId());

        assertEquals(prepared.keyVersionId(), capturedKey().getActiveVersionId());
        assertEquals(SigningKeyService.PREVIOUS, versions.getFirst().getStatus());
        assertEquals(SigningKeyService.ACTIVE, versions.getLast().getStatus());
        assertEquals(List.of(first.publicJwk()), service.previousPublicJwks(
                TENANT_ID, first.keyId(), second.keyVersionId()));
        assertTrue(versions.getFirst().getPreviousUntil().isAfter(Instant.now()));
    }

    @Test
    void activationUsesTheConfiguredGracePeriod() {
        var first = service.create(TENANT_ID, "issuer-signing", "ES256", 7, null);
        capturedKey().setRotationGracePeriodSeconds(0L);
        var prepared = service.prepareRotation(TENANT_ID, first.keyId());

        service.activate(TENANT_ID, first.keyId(), prepared.keyVersionId());

        assertFalse(versions.getFirst().getPreviousUntil().isAfter(Instant.now()));
    }

    @Test
    void retainsVerificationMaterial() {
        var first = service.create(TENANT_ID, "issuer", "ES256", 7, null);
        capturedKey().setRotationGracePeriodSeconds(0L);
        var prepared = service.prepareRotation(TENANT_ID, first.keyId());
        service.activate(TENANT_ID, first.keyId(), prepared.keyVersionId());

        assertEquals(List.of(first.publicJwk()), service.previousPublicJwks(
                TENANT_ID, first.keyId(), prepared.keyVersionId()));

        versions.getFirst().setStatus(SigningKeyService.REVOKED);
        assertEquals(List.of(first.publicJwk()), service.previousPublicJwks(
                TENANT_ID, first.keyId(), prepared.keyVersionId()));
    }

    @Test
    void preparedVersionCanBeDeletedWithoutChangingTheActiveVersion() {
        var first = service.create(TENANT_ID, "issuer-signing", "ES256", 7, null);
        var prepared = service.prepareRotation(TENANT_ID, first.keyId());

        service.deletePreparedVersion(TENANT_ID, first.keyId(), prepared.keyVersionId());

        assertEquals(first.keyVersionId(), capturedKey().getActiveVersionId());
        assertEquals(List.of(first.keyVersionId(), prepared.keyVersionId()), versions.stream()
                .map(SigningKeyVersionEntity::getId)
                .toList());
        assertEquals(SigningKeyService.REVOKED, versions.getLast().getStatus());
        assertThrows(IllegalArgumentException.class, () -> service.deletePreparedVersion(
                TENANT_ID, first.keyId(), first.keyVersionId()));

        var replacement = service.prepareRotation(TENANT_ID, first.keyId());

        assertEquals(3, replacement.version());
        assertEquals("issuer-signing-v3", new ObjectMapper()
                .readTree(replacement.publicJwk()).path("kid").asText());
    }

    @Test
    void renewalKeepsOldCertificate() throws Exception {
        var provisioned = service.create(TENANT_ID, "issuer", "ES256", 7, null);
        var certificates = mock(org.heidiverse.heidi.entity.data.repository.SigningCertificateRepository.class);
        var old = new org.heidiverse.heidi.entity.model.entity.SigningCertificateEntity();
        old.setId(UUID.randomUUID());
        old.setKeyVersionId(provisioned.keyVersionId());
        when(certificates.forProfile(
                any(), any(), any())).thenReturn(List.of(old));
        var generator = java.security.KeyPairGenerator.getInstance("EC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        versions.getFirst().setPublicJwk(new com.nimbusds.jose.jwk.ECKey.Builder(
                com.nimbusds.jose.jwk.Curve.P_256, (java.security.interfaces.ECPublicKey) pair.getPublic())
                .build().toJSONString());
        var name = new org.bouncycastle.asn1.x500.X500Name("CN=Test");
        var now = Instant.now();
        var certificate = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(name,
                java.math.BigInteger.ONE, java.util.Date.from(now.minusSeconds(60)),
                java.util.Date.from(now.plusSeconds(3600)), name, pair.getPublic())
                .build(new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withECDSA")
                        .build(pair.getPrivate()));
        service = new SigningKeyService(keyRepository, versionRepository, providerService,
                new ObjectMapper(), certificates, slotRepository, signingFlows, identitySlots);

        service.setCertificateChain(TENANT_ID, provisioned.keyId(), provisioned.keyVersionId(),
                List.of(java.util.Base64.getEncoder().encodeToString(certificate.getEncoded())));

        org.mockito.Mockito.verify(certificates, never()).deleteAll(any());
    }

    @Test
    void refusesDeletingPublishedKey() {
        var key = service.create(TENANT_ID, "issuer", "ES256", 7, null);
        when(identitySlots.published(key.keyId())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.delete(TENANT_ID, key.keyId()));
        assertTrue(provider.deletedKeyUris.isEmpty());
    }

    @Test
    void activationSelectsNewChain() {
        var first = service.create(TENANT_ID, "issuer", "ES256", 7, null);
        var prepared = service.prepareRotation(TENANT_ID, first.keyId());
        var slot = new org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity();
        slot.setId(UUID.randomUUID());
        slot.setType(org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.CREDENTIAL_SIGNING);
        slot.setTrustSystem(org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem.EUDI);
        slot.setCertificateId(UUID.randomUUID());
        var certificate = new org.heidiverse.heidi.entity.model.entity.SigningCertificateEntity();
        certificate.setId(UUID.randomUUID());
        certificate.setKeyVersionId(prepared.keyVersionId());
        certificate.setProfile(org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile.CREDENTIAL_SIGNING);
        certificate.setTrustSystem(org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem.EUDI);
        certificate.setCertificateChain(List.of("new-leaf", "root"));
        certificate.setNotBefore(Instant.now().minusSeconds(60));
        certificate.setNotAfter(Instant.now().plusSeconds(3600));
        when(slotRepository.findAllByKeyId(first.keyId())).thenReturn(List.of(slot));
        when(certificateRepository.forProfile(prepared.keyVersionId(), certificate.getProfile(), certificate.getTrustSystem()))
                .thenReturn(List.of(certificate));
        when(certificateRepository.findById(certificate.getId())).thenReturn(Optional.of(certificate));

        service.activate(TENANT_ID, first.keyId(), prepared.keyVersionId());

        assertEquals(certificate.getId(), slot.getCertificateId());
        assertNotNull(certificate.getFirstUsedAt());
        org.mockito.Mockito.verify(certificateRepository).save(certificate);
        org.mockito.Mockito.verify(identitySlots).rememberPublicKey(slot);
    }

    @Test
    void activationNeedsAcceptedClient() {
        var first = service.create(TENANT_ID, "issuer", "ES256", 7, null);
        var prepared = service.prepareRotation(TENANT_ID, first.keyId());
        var slot = new org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity();
        when(slotRepository.findAllByKeyId(first.keyId())).thenReturn(List.of(slot));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("issuer client missing"))
                .when(identitySlots).requireClients(slot);

        assertThrows(IllegalArgumentException.class,
                () -> service.activate(TENANT_ID, first.keyId(), prepared.keyVersionId()));
        assertEquals(first.keyVersionId(), keys.getFirst().getActiveVersionId());
    }

    @Test
    void activationChoosesBetweenChains() {
        var first = service.create(TENANT_ID, "issuer", "ES256", 7, null);
        var prepared = service.prepareRotation(TENANT_ID, first.keyId());
        var slot = new org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity();
        slot.setId(UUID.randomUUID());
        slot.setType(org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.CREDENTIAL_SIGNING);
        slot.setTrustSystem(org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem.EUDI);
        var originalCertificate = UUID.randomUUID();
        slot.setCertificateId(originalCertificate);
        var certificates = java.util.stream.IntStream.range(0, 2).mapToObj(index -> {
            var certificate = new org.heidiverse.heidi.entity.model.entity.SigningCertificateEntity();
            certificate.setId(UUID.randomUUID());
            certificate.setCertificateChain(List.of("leaf", "root"));
            certificate.setNotBefore(Instant.now().minusSeconds(60));
            certificate.setNotAfter(Instant.now().plusSeconds(3600));
            return certificate;
        }).toList();
        when(slotRepository.findAllByKeyId(first.keyId())).thenReturn(List.of(slot));
        when(certificateRepository.forProfile(prepared.keyVersionId(),
                slot.getType().certificateProfile(slot.getTrustSystem()), slot.getTrustSystem()))
                .thenReturn(certificates);
        when(certificateRepository.findById(certificates.getLast().getId()))
                .thenReturn(Optional.of(certificates.getLast()));

        assertThrows(IllegalArgumentException.class,
                () -> service.activate(TENANT_ID, first.keyId(), prepared.keyVersionId()));
        assertThrows(IllegalArgumentException.class,
                () -> service.activate(TENANT_ID, first.keyId(), prepared.keyVersionId(),
                        java.util.Map.of(slot.getId(), originalCertificate)));
        assertThrows(IllegalArgumentException.class,
                () -> service.activate(TENANT_ID, first.keyId(), prepared.keyVersionId(),
                        java.util.Map.of(UUID.randomUUID(), certificates.getFirst().getId())));
        assertEquals(first.keyVersionId(), keys.getFirst().getActiveVersionId());
        assertEquals(originalCertificate, slot.getCertificateId());

        service.activate(TENANT_ID, first.keyId(), prepared.keyVersionId(),
                java.util.Map.of(slot.getId(), certificates.getLast().getId()));

        assertEquals(certificates.getLast().getId(), slot.getCertificateId());
        assertEquals(prepared.keyVersionId(), keys.getFirst().getActiveVersionId());
    }

    @Test
    void activationNeedsCertificate() {
        var first = service.create(TENANT_ID, "issuer", "ES256", 7, null);
        var prepared = service.prepareRotation(TENANT_ID, first.keyId());
        var slots = mock(org.heidiverse.heidi.entity.data.repository.IdentityKeySlotRepository.class);
        var certificates = mock(org.heidiverse.heidi.entity.data.repository.SigningCertificateRepository.class);
        var slot = new org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity();
        slot.setId(UUID.randomUUID());
        slot.setType(org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.IDENTITY_STATEMENT);
        slot.setTrustSystem(org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem.EUDI);
        when(slots.findAllByKeyId(first.keyId())).thenReturn(List.of(slot));
        service = new SigningKeyService(keyRepository, versionRepository, providerService,
                new ObjectMapper(), certificates, slots, signingFlows, identitySlots);

        assertThrows(IllegalArgumentException.class,
                () -> service.activate(TENANT_ID, first.keyId(), prepared.keyVersionId()));
        assertEquals(first.keyVersionId(), capturedKey().getActiveVersionId());
    }

    @Test
    void tenantCannotAccessAnotherTenantsKey() {
        var key = new SigningKeyEntity();
        key.setId(UUID.randomUUID());
        key.setTenantId(TENANT_ID);
        when(keyRepository.findById(key.getId())).thenReturn(Optional.of(key));

        assertThrows(SecurityException.class, () -> service.owned("tenant-b", key.getId()));
    }

    @Test
    void failedProvisioningDoesNotLeaveAnEmptyKey() {
        provider.failCreate = true;

        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(TENANT_ID, "failed", "ES256", 7, null));

        assertTrue(keys.isEmpty());
        org.mockito.Mockito.verify(keyRepository, never()).save(any(SigningKeyEntity.class));
    }

    @Test
    void transactionRollbackDeletesTheCreatedProviderKey() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            var provisioned = service.create(
                    TENANT_ID, "rolled-back", "ES256", 7, null);

            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(
                            TransactionSynchronization.STATUS_ROLLED_BACK));

            assertEquals(List.of(provisioned.keyUri()), provider.deletedKeyUris);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rotatesDecryptionWithGrace() {
        var first = service.create(TENANT_ID, "requests",
                Set.of(SigningKeyUsage.KEY_AGREEMENT), "ES256", 7, null);
        var slot = new org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity();
        slot.setId(UUID.randomUUID());
        slot.setType(org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.DECRYPTION);
        when(slotRepository.findAllByKeyId(first.keyId())).thenReturn(List.of(slot));
        var now = Instant.now();
        var old = versions.getFirst();
        old.setCreatedAt(now.minusSeconds(SigningKeyEntity.DEFAULT_ROTATION_SECONDS));
        service.updateRotationPolicy(TENANT_ID, first.keyId(),
                new org.heidiverse.heidi.entity.model.issuer.KeyRotationPolicy(
                        org.heidiverse.heidi.entity.model.issuer.KeyRotationMode.AUTOMATIC,
                        SigningKeyEntity.DEFAULT_ROTATION_SECONDS, java.time.Duration.ofDays(2).toSeconds()));

        var rotated = service.rotateDue(TENANT_ID, first.keyId(), now).orElseThrow();

        assertEquals(2, versions.size());
        assertEquals(rotated.keyVersionId(), keys.getFirst().getActiveVersionId());
        assertEquals(SigningKeyService.PREVIOUS, old.getStatus());
        assertTrue(old.getPreviousUntil().isAfter(now.plusSeconds(java.time.Duration.ofDays(1).toSeconds())));
        assertTrue(provider.deletedKeyUris.isEmpty());
        assertTrue(service.rotateDue(TENANT_ID, first.keyId(), now).isEmpty());
        assertEquals(2, versions.size());
    }

    @Test
    void manualKeysNeverRotateAutomatically() {
        var first = service.create(TENANT_ID, "requests",
                Set.of(SigningKeyUsage.KEY_AGREEMENT), "ES256", 7, null);
        var now = Instant.now();
        versions.getFirst().setCreatedAt(now.minusSeconds(SigningKeyEntity.DEFAULT_ROTATION_SECONDS));

        assertTrue(service.rotateDue(TENANT_ID, first.keyId(), now).isEmpty());
        assertEquals(1, versions.size());
    }

    @Test
    void rejectsAutomaticRotationWithoutDecryptionSlot() {
        var first = service.create(TENANT_ID, "requests",
                Set.of(SigningKeyUsage.KEY_AGREEMENT), "ES256", 7, null);

        var exception = assertThrows(IllegalArgumentException.class,
                () -> service.updateRotationPolicy(
                        TENANT_ID, first.keyId(), automaticPolicy()));

        assertTrue(exception.getMessage().contains("request decryption"));
    }

    @Test
    void rejectsAutomaticRotationForSigningSlot() {
        var first = service.create(TENANT_ID, "issuer", "ES256", 7, null);
        var slot = new org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity();
        slot.setType(org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.CREDENTIAL_SIGNING);
        when(slotRepository.findAllByKeyId(first.keyId())).thenReturn(List.of(slot));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateRotationPolicy(
                        TENANT_ID, first.keyId(), automaticPolicy()));
    }

    @Test
    void rejectsAutomaticRotationWhenProviderCannotCreate() {
        var first = service.create(TENANT_ID, "requests",
                Set.of(SigningKeyUsage.KEY_AGREEMENT), "ES256", 7, null);
        var slot = new org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity();
        slot.setType(org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.DECRYPTION);
        when(slotRepository.findAllByKeyId(first.keyId())).thenReturn(List.of(slot));
        when(providerService.provider(TENANT_ID, 7)).thenReturn(mock(SigningKeyProvider.class));

        var exception = assertThrows(IllegalArgumentException.class,
                () -> service.updateRotationPolicy(
                        TENANT_ID, first.keyId(), automaticPolicy()));

        assertTrue(exception.getMessage().contains("cannot create"));
    }

    @Test
    void acceptsAutomaticRotationForEligibleDecryptionKey() {
        var first = service.create(TENANT_ID, "requests",
                Set.of(SigningKeyUsage.KEY_AGREEMENT), "ES256", 7, null);
        var slot = new org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity();
        slot.setType(org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.DECRYPTION);
        when(slotRepository.findAllByKeyId(first.keyId())).thenReturn(List.of(slot));

        service.updateRotationPolicy(TENANT_ID, first.keyId(), automaticPolicy());

        assertEquals(org.heidiverse.heidi.entity.model.issuer.KeyRotationMode.AUTOMATIC,
                keys.getFirst().getRotationMode());
        assertEquals(SigningKeyEntity.DEFAULT_ROTATION_SECONDS,
                keys.getFirst().getRotationIntervalSeconds());
    }

    @Test
    void reusesPreparedDecryption() {
        var first = service.create(TENANT_ID, "requests",
                Set.of(SigningKeyUsage.KEY_AGREEMENT), "ES256", 7, null);
        var slot = new org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity();
        slot.setId(UUID.randomUUID());
        slot.setType(org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.DECRYPTION);
        when(slotRepository.findAllByKeyId(first.keyId())).thenReturn(List.of(slot));
        service.updateRotationPolicy(TENANT_ID, first.keyId(),
                new org.heidiverse.heidi.entity.model.issuer.KeyRotationPolicy(
                        org.heidiverse.heidi.entity.model.issuer.KeyRotationMode.AUTOMATIC,
                        SigningKeyEntity.DEFAULT_ROTATION_SECONDS, SigningKeyEntity.DEFAULT_GRACE_SECONDS));
        var prepared = service.prepareRotation(TENANT_ID, first.keyId());
        var now = Instant.now();
        versions.getFirst().setCreatedAt(now.minusSeconds(SigningKeyEntity.DEFAULT_ROTATION_SECONDS));

        assertEquals(prepared.keyVersionId(), service.rotateDue(TENANT_ID, first.keyId(), now).orElseThrow().keyVersionId());
        assertEquals(2, versions.size());
    }

    @Test
    void neverSchedulesSigningSlots() {
        var first = service.create(TENANT_ID, "issuer", "ES256", 7, null);
        var decryption = new org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity();
        decryption.setType(org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.DECRYPTION);
        when(slotRepository.findAllByKeyId(first.keyId())).thenReturn(List.of(decryption));
        service.updateRotationPolicy(TENANT_ID, first.keyId(),
                new org.heidiverse.heidi.entity.model.issuer.KeyRotationPolicy(
                        org.heidiverse.heidi.entity.model.issuer.KeyRotationMode.AUTOMATIC,
                        SigningKeyEntity.DEFAULT_ROTATION_SECONDS, SigningKeyEntity.DEFAULT_GRACE_SECONDS));
        var signing = new org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity();
        signing.setType(org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType.CREDENTIAL_SIGNING);
        when(slotRepository.findAllByKeyId(first.keyId())).thenReturn(List.of(signing));
        var now = Instant.now();
        versions.getFirst().setCreatedAt(now.minusSeconds(SigningKeyEntity.DEFAULT_ROTATION_SECONDS));

        assertTrue(service.rotateDue(TENANT_ID, first.keyId(), now).isEmpty());
        assertEquals(1, versions.size());
    }

    @Test
    void failedRotationCleansMaterial() {
        var first = service.create(TENANT_ID, "issuer", "ES256", 7, null);
        TransactionSynchronizationManager.initSynchronization();
        try {
            var prepared = service.prepareRotation(TENANT_ID, first.keyId());
            TransactionSynchronizationManager.getSynchronizations().forEach(callback ->
                    callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertEquals(List.of(prepared.keyUri()), provider.deletedKeyUris);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rejectsUnsafeLogicalKeyIdBeforeProvisioning() {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(TENANT_ID, "issuer signing", "ES256", 7, null));

        assertTrue(exception.getMessage().contains("Key ID"));
        assertTrue(keys.isEmpty());
        org.mockito.Mockito.verify(providerService, never()).provider(any(), anyInt());
    }

    private static org.heidiverse.heidi.entity.model.issuer.KeyRotationPolicy automaticPolicy() {
        return new org.heidiverse.heidi.entity.model.issuer.KeyRotationPolicy(
                org.heidiverse.heidi.entity.model.issuer.KeyRotationMode.AUTOMATIC,
                SigningKeyEntity.DEFAULT_ROTATION_SECONDS, SigningKeyEntity.DEFAULT_GRACE_SECONDS);
    }

    private SigningKeyEntity capturedKey() {
        var captor = org.mockito.ArgumentCaptor.forClass(SigningKeyEntity.class);
        org.mockito.Mockito.verify(keyRepository, org.mockito.Mockito.atLeastOnce())
                .save(captor.capture());
        return captor.getAllValues().getFirst();
    }

    private static final class TestProvider
            implements SigningKeyProvider, SigningKeyCreator, SigningKeyImporter, SigningKeyDeleter,
                    org.heidiverse.heidi.shared.signing.SigningKeyRevoker {
        private boolean failCreate;
        private boolean failRevoke;
        private final List<String> revokedKeyUris = new ArrayList<>();
        private final List<String> deletedKeyUris = new ArrayList<>();

        @Override public String scheme() { return "software"; }
        @Override public List<String> supportedAlgorithms() { return List.of("ES256"); }
        @Override public SigningKeyRef resolve(String keyUri) {
            return new SigningKeyRef(keyUri, "{\"kty\":\"EC\"}", "ES256");
        }
        @Override public SigningKeyRef createKey(String keyId, String algorithm) {
            if (failCreate) throw new IllegalArgumentException("provider create failed");
            return new SigningKeyRef(
                    "software://" + keyId, "{\"kty\":\"EC\"}", algorithm);
        }
        @Override public SigningKeyRef createKey(String keyId, String algorithm,
                java.util.Set<org.heidiverse.heidi.shared.signing.SigningKeyUsage> usages) {
            var ref = createKey(keyId, algorithm);
            return new SigningKeyRef(ref.uri(), ref.publicJwk(), ref.algorithm(), usages);
        }
        @Override public SigningKeyRef importKey(String keyId, String privateJwk, String algorithm) {
            return new SigningKeyRef(
                    "software://" + keyId, "{\"kty\":\"EC\"}", algorithm);
        }
        @Override public void deleteKey(SigningKeyRef ref) {
            deletedKeyUris.add(ref.uri());
        }
        @Override public void revokeKey(SigningKeyRef ref) {
            if (failRevoke) throw new IllegalStateException("Provider unavailable");
            revokedKeyUris.add(ref.uri());
        }
        @Override public byte[] sign(SigningKeyRef ref, byte[] message) { return new byte[0]; }
        @Override public ProviderHealth health() { return ProviderHealth.up(0); }
    }
}
