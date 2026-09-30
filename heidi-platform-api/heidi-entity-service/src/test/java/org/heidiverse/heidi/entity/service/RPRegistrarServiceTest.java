// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.heidiverse.heidi.coordinator.model.ProofSchemeResponse;
import org.heidiverse.heidi.coordinator.service.DcqlQueryService;
import org.heidiverse.heidi.entity.data.repository.TenantRepository;
import org.heidiverse.heidi.entity.model.entity.TenantEntity;
import org.heidiverse.heidi.entity.model.proofscheme.ProofSchemeDetail;
import org.heidiverse.heidi.entity.model.relyingpartyauthenticationauthorization.RegistrationCertificateCreationRequest;
import org.heidiverse.heidi.entity.model.relyingpartyauthenticationauthorization.RegistrationCertificateCreationResponse;
import org.heidiverse.heidi.entity.service.feign.RPRegistrarFeignClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.ObjectMapper;
import uniffi.kapun_dcql_rust.DcqlQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

class RPRegistrarServiceTest {

    @Test
    void generatesRegistrationDcqlInProcess() throws Exception {
        var registrar = mock(RPRegistrarFeignClient.class);
        var tenants = mock(TenantRepository.class);
        var dcql = mock(DcqlQueryService.class);
        var objectMapper = mock(ObjectMapper.class);
        var tenant = mock(TenantEntity.class);
        var proofScheme = mock(ProofSchemeDetail.class);
        var coordinatorProofScheme = mock(ProofSchemeResponse.class);
        var relyingPartyId = UUID.randomUUID();
        when(proofScheme.tenantId()).thenReturn("tenant");
        when(proofScheme.purpose()).thenReturn("purpose");
        when(tenant.getRegistrarRpId()).thenReturn(relyingPartyId);
        when(tenants.findByTenantIdAndDeletedFalse("tenant"))
                .thenReturn(Optional.of(tenant));
        when(objectMapper.convertValue(proofScheme, ProofSchemeResponse.class))
                .thenReturn(coordinatorProofScheme);
        when(dcql.generate(any(), any()))
                .thenReturn(new DcqlQuery(List.of(), List.of()));
        when(registrar.addNewRegistrationCertificate(any(), any()))
                .thenReturn(new RegistrationCertificateCreationResponse("id", "certificate"));
        when(objectMapper.readTree(any(String.class))).thenReturn(new ObjectMapper().readTree("{}"));
        var service = new RPRegistrarService(registrar, tenants, dcql, objectMapper);
        ReflectionTestUtils.setField(service, "registrationPrivacyPolicy", "policy");
        ReflectionTestUtils.setField(service, "registrationContactEmail", "mail@example.com");
        ReflectionTestUtils.setField(service, "registrationContactWebsite", "https://example.com");
        ReflectionTestUtils.setField(service, "registrationContactPhone", "+41000000000");

        assertEquals(
                "certificate",
                service.addNewRegistrationCertificate(proofScheme));

        verify(dcql)
                .generate(
                        coordinatorProofScheme,
                        org.kapunsdk.presentation.request.model.OID4VPVersion.DRAFT_28);
        var request = ArgumentCaptor.forClass(RegistrationCertificateCreationRequest.class);
        verify(registrar).addNewRegistrationCertificate(
                org.mockito.ArgumentMatchers.eq(relyingPartyId), request.capture());
        assertEquals(List.of(), request.getValue().credentials());
    }
}
