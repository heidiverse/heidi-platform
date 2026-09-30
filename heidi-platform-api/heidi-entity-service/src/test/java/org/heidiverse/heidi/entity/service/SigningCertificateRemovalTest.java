// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.heidiverse.heidi.entity.data.repository.IdentityKeySlotRepository;
import org.heidiverse.heidi.entity.data.repository.SigningCertificateRepository;
import org.heidiverse.heidi.entity.data.repository.SigningFlowRepository;
import org.heidiverse.heidi.entity.data.repository.SigningKeyRepository;
import org.heidiverse.heidi.entity.data.repository.SigningKeyVersionRepository;
import org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity;
import org.heidiverse.heidi.entity.model.entity.SigningCertificateEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity;
import org.heidiverse.heidi.entity.model.exceptions.CertificateInUseException;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SigningCertificateRemovalTest {
    private static final String TENANT_ID = "tenant-a";

    private final SigningKeyRepository keys = mock(SigningKeyRepository.class);
    private final SigningKeyVersionRepository versions = mock(SigningKeyVersionRepository.class);
    private final SigningCertificateRepository certificates = mock(SigningCertificateRepository.class);
    private final IdentityKeySlotRepository slots = mock(IdentityKeySlotRepository.class);
    private final UUID keyId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private SigningKeyService service;

    @BeforeEach
    void setUp() {
        var key = new SigningKeyEntity();
        key.setId(keyId);
        key.setTenantId(TENANT_ID);
        var version = new SigningKeyVersionEntity();
        version.setId(versionId);
        version.setKeyId(keyId);
        when(keys.findByIdForUpdate(keyId)).thenReturn(Optional.of(key));
        when(versions.findById(versionId)).thenReturn(Optional.of(version));

        service = new SigningKeyService(keys, versions, mock(SigningProviderService.class),
                new ObjectMapper(), certificates, slots, mock(SigningFlowRepository.class),
                mock(IdentityKeySlotService.class));
    }

    @Test
    void refusesAssignedCertificateRemoval() {
        var certificate = certificate(SigningCertificateSource.DEVELOPMENT);
        when(slots.findAllByCertificateId(certificate.getId()))
                .thenReturn(List.of(new IdentityKeySlotEntity()));

        assertThrows(CertificateInUseException.class, () -> service.removeCertificate(
                TENANT_ID, keyId, versionId, certificate.getId()));

        verify(certificates, never()).delete(certificate);
    }

    @Test
    void deletesUnusedDevelopmentCertificate() {
        var certificate = certificate(SigningCertificateSource.DEVELOPMENT);

        service.removeCertificate(TENANT_ID, keyId, versionId, certificate.getId());

        verify(certificates).delete(certificate);
    }

    @Test
    void retiresPreviouslyUsedDevelopmentCertificate() {
        var certificate = certificate(SigningCertificateSource.DEVELOPMENT);
        certificate.setFirstUsedAt(java.time.Instant.now());

        service.removeCertificate(TENANT_ID, keyId, versionId, certificate.getId());

        assertNotNull(certificate.getRetiredAt());
        verify(certificates).save(certificate);
        verify(certificates, never()).delete(certificate);
    }

    @Test
    void retiresImportedCertificateForAuditHistory() {
        var certificate = certificate(SigningCertificateSource.IMPORTED);
        certificate.setFirstUsedAt(java.time.Instant.now());

        service.removeCertificate(TENANT_ID, keyId, versionId, certificate.getId());

        assertNotNull(certificate.getRetiredAt());
        verify(certificates).save(certificate);
        verify(certificates, never()).delete(certificate);
    }

    @Test
    void deletesNeverUsedImportedCertificate() {
        var certificate = certificate(SigningCertificateSource.IMPORTED);

        service.removeCertificate(TENANT_ID, keyId, versionId, certificate.getId());

        verify(certificates).delete(certificate);
        verify(certificates, never()).save(certificate);
    }

    private SigningCertificateEntity certificate(SigningCertificateSource source) {
        var certificate = new SigningCertificateEntity();
        certificate.setId(UUID.randomUUID());
        certificate.setKeyVersionId(versionId);
        certificate.setSource(source);
        when(certificates.findById(certificate.getId())).thenReturn(Optional.of(certificate));
        when(slots.findAllByCertificateId(certificate.getId())).thenReturn(List.of());
        return certificate;
    }
}
