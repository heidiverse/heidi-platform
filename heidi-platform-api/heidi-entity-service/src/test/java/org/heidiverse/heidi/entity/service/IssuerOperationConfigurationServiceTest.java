// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.heidiverse.heidi.entity.data.service.IssuerDataService;
import org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.issuer.IssuerOperationConfigurationRequest;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileId;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class IssuerOperationConfigurationServiceTest {
    private static final String OPERATION = "etsi.pades-qes";

    @Test
    void storesConfigurationOnTheOperationSlot() {
        var identity = identity(7, "tenant-a");
        var slot = slot(IssuerTrustSystem.Switzerland);
        var identities = mock(IssuerDataService.class);
        when(identities.findById(7)).thenReturn(Optional.of(identity));
        var slots = mock(IdentityKeySlotService.class);
        when(slots.slots(7)).thenReturn(List.of(slot));
        var service = new IssuerOperationConfigurationService(identities, slots);
        var response = service.updateTenant(
                "tenant-a", 7, IssuerTrustSystem.Switzerland, OPERATION,
                new IssuerOperationConfigurationRequest(
                        new ObjectMapper().createObjectNode().put("policy", "qualified"), null));

        assertEquals(7, response.issuerId());
        assertEquals(IssuerTrustSystem.Switzerland, response.trustSystem());
        assertEquals(OPERATION, response.operation());
        assertEquals("qualified", response.configuration().path("policy").asString());
    }

    @Test
    void profileBoundLookupDoesNotUseIdentityDefaultTrustRouting() {
        var identity = identity(7, "tenant-a");
        identity.setDefaultTrustSystem(IssuerTrustSystem.EUDI);
        var defaultSlot = slot(IssuerTrustSystem.Default);
        defaultSlot.setConfiguration(new ObjectMapper().createObjectNode()
                .set("configuration", new ObjectMapper().createObjectNode().put("policy", "custom")));
        var eudiSlot = slot(IssuerTrustSystem.EUDI);
        eudiSlot.setConfiguration(new ObjectMapper().createObjectNode()
                .set("configuration", new ObjectMapper().createObjectNode().put("policy", "eudi")));
        var identities = mock(IssuerDataService.class);
        when(identities.findBySlug("issuer")).thenReturn(Optional.of(identity));
        var slots = mock(IdentityKeySlotService.class);
        when(slots.slots(7)).thenReturn(List.of(defaultSlot, eudiSlot));
        var service = new IssuerOperationConfigurationService(identities, slots);

        var response = service.getForIssuer(
                "issuer", IssuerTrustSystem.Default, OPERATION,
                EcosystemProfileId.CUSTOM_ISSUANCE_2026_1).orElseThrow();

        assertEquals("custom", response.configuration().path("policy").asString());
    }

    private IssuerDefinitionEntity identity(int id, String tenantId) {
        var identity = new IssuerDefinitionEntity();
        identity.setId(id);
        identity.setTenantId(tenantId);
        return identity;
    }

    private IdentityKeySlotEntity slot(IssuerTrustSystem trustSystem) {
        var slot = new IdentityKeySlotEntity();
        slot.setIdentityId(7);
        slot.setType(IdentityKeySlotType.OPERATION);
        slot.setTrustSystem(trustSystem);
        slot.setOperation(OPERATION);
        return slot;
    }
}
