// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.heidiverse.heidi.entity.data.repository.IssuerDefinitionRepository;
import org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService;
import org.heidiverse.heidi.entity.data.service.ProofSchemeDataService;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProofSchemeTrustAnchorTest {
    private static final String TENANT = "tenant-a";
    private static final String MANUAL = "manual-anchor";
    private static final String SUBCA = "subca-anchor";

    private final IssuingPkiService pki = mock(IssuingPkiService.class);
    private final ProofSchemeService service = new ProofSchemeService(
            mock(ProofSchemeDataService.class),
            mock(CredentialSchemeDataService.class),
            mock(RPRegistrarService.class),
            mock(IssuerDefinitionRepository.class),
            mock(SigningProviderService.class),
            mock(IssuerService.class),
            mock(IssuerOperationConfigurationService.class));
    private final IssuerDefinitionEntity identity = new IssuerDefinitionEntity();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "issuingPki", pki);
        identity.setTenantId(TENANT);
        identity.setEudiVerificationTrustAnchors(List.of(MANUAL));
    }

    @Test
    void addsOwnPkiWhenTrusted() {
        identity.setEudiTrustOwnPki(true);
        when(pki.trustAnchors(TENANT)).thenReturn(List.of(SUBCA, MANUAL));

        assertEquals(List.of(MANUAL, SUBCA), service.eudiTrustAnchors(identity));
    }

    @Test
    void keepsManualAnchorsOnly() {
        assertEquals(List.of(MANUAL), service.eudiTrustAnchors(identity));
        verifyNoInteractions(pki);
    }
}
