// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.heidiverse.heidi.entity.data.repository.SigningFlowRepository;
import org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService;
import org.heidiverse.heidi.entity.data.service.IssuerDataService;
import org.heidiverse.heidi.entity.data.service.ProofSchemeDataService;
import org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustConfigurationRequest;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IssuerTrustOwnPkiTest {
    private static final int IDENTITY = 1;

    private final IssuerDataService identities = mock(IssuerDataService.class);
    private final IdentityKeySlotService slots = mock(IdentityKeySlotService.class);
    private final IssuerService service = new IssuerService(identities,
            mock(CredentialSchemeDataService.class), mock(SwissTrustStatementRefreshService.class),
            mock(SigningProviderService.class), mock(SigningKeyService.class),
            mock(SigningGrantService.class), slots, null, mock(ProofSchemeDataService.class),
            mock(SigningFlowRepository.class));
    private final IssuerDefinitionEntity identity = new IssuerDefinitionEntity();

    @BeforeEach
    void setUp() {
        identity.setId(IDENTITY);
        identity.setTenantId("tenant-a");
        var slot = new IdentityKeySlotEntity();
        slot.setType(IdentityKeySlotType.IDENTITY_STATEMENT);
        slot.setTrustSystem(IssuerTrustSystem.EUDI);
        when(identities.findById(IDENTITY)).thenReturn(Optional.of(identity));
        when(identities.save(any())).thenAnswer(call -> call.getArgument(0));
        when(slots.slots(IDENTITY)).thenReturn(List.of(slot));
        when(slots.resolve(any(), any(Integer.class), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void togglePersistsAndNullKeepsIt() {
        assertTrue(update(true).eudiTrustOwnPki());
        assertTrue(identity.isEudiTrustOwnPki());

        assertTrue(update(null).eudiTrustOwnPki());

        assertFalse(update(false).eudiTrustOwnPki());
        assertFalse(identity.isEudiTrustOwnPki());
    }

    private org.heidiverse.heidi.entity.model.issuer.IssuerTrustConfigurationResponse update(
            Boolean trustOwnPki) {
        var request = new IssuerTrustConfigurationRequest(null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, trustOwnPki);
        return service.updateIssuerTrustConfiguration(
                "tenant-a", IDENTITY, IssuerTrustSystem.EUDI, request, Set.of(IDENTITY));
    }
}
