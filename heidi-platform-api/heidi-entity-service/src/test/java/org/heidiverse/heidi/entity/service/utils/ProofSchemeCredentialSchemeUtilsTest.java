// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeState;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeEntity;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.entity.ProofSchemeCredentialSchemeEntity;
import org.junit.jupiter.api.Test;

class ProofSchemeCredentialSchemeUtilsTest {

    @Test
    void mapsRelationWithoutRequestedAttributes() {
        var issuer = new IssuerDefinitionEntity();
        issuer.setId(1);

        var credentialScheme = mock(CredentialSchemeEntity.class);
        when(credentialScheme.getUuid()).thenReturn(UUID.randomUUID());
        when(credentialScheme.getIssuerDefinition()).thenReturn(issuer);
        when(credentialScheme.getState()).thenReturn(CredentialSchemeState.PUBLISHED);

        var relation = new ProofSchemeCredentialSchemeEntity();
        relation.setCredentialSchemeEntity(credentialScheme);

        var result = ProofSchemeCredentialSchemeUtils.toCredentialScheme(relation);

        assertTrue(result.attributes().isEmpty());
    }
}
