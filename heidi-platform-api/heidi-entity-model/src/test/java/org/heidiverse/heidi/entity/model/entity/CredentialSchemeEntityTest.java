// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import jakarta.persistence.JoinColumn;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;

class CredentialSchemeEntityTest {
    @Test
    void issuerDefinitionIsRequired() throws NoSuchFieldException {
        var field = CredentialSchemeEntity.class.getDeclaredField("issuerDefinition");

        assertTrue(field.isAnnotationPresent(NotNull.class));
        assertFalse(field.getAnnotation(JoinColumn.class).nullable());
    }

    @Test
    void defaultRoutingSentinelIsNotExposedAsCredentialOverride() {
        var entity = new CredentialSchemeEntity();

        entity.setDefaultTrustSystem(IssuerTrustSystem.Default);

        assertNull(entity.getDefaultTrustSystem());
    }
}
