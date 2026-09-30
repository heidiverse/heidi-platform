// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;

import org.junit.jupiter.api.Test;

class EcosystemProfileCatalogTest {
    @Test
    void catalogContainsVersionedAndCustomProfiles() {
        assertEquals(8, EcosystemProfileCatalog.all().size());
        assertTrue(EcosystemProfileCatalog.all().stream()
                .allMatch(profile -> profile.id().matches("[A-Z0-9_]+(_2026_1)?")));
        assertTrue(EcosystemProfileCatalog.all().stream()
                .noneMatch(profile -> Set.of("Default", "General").contains(profile.displayName())));
    }

    @Test
    void roleMismatchIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                EcosystemProfileCatalog.require(
                        EcosystemProfileId.EUDI_PRESENTATION_2026_1,
                        EcosystemProfileRole.ISSUANCE));
    }

    @Test
    void manifestsExposePinnedPolicy() {
        final var profile = EcosystemProfileCatalog.require(
                EcosystemProfileId.SWISS_PRESENTATION_2026_1,
                EcosystemProfileRole.PRESENTATION);

        assertEquals("direct_post.jwt", profile.policy().responseMode());
        assertEquals("ES256", profile.policy().signingAlgorithms().required().getFirst());
        assertEquals("ECDH-ES", profile.policy().encryptionAlgorithms().alg().supported().getFirst());
        assertEquals("A256GCM", profile.policy().encryptionAlgorithms().enc().preferred().getFirst());
        assertTrue(profile.policy().encryptionAlgorithms().zip().supported().isEmpty());
        final var eudi = EcosystemProfileCatalog.require(
                EcosystemProfileId.EUDI_PRESENTATION_2026_1,
                EcosystemProfileRole.PRESENTATION);
        assertTrue(eudi.policy().encryptionAlgorithms().enc().supported().contains("A128GCM"));
        assertEquals("Swiss Profile 1.0", profile.displayName());
        assertEquals("swiss-profile-verification:1.0.0", profile.version());
        assertEquals(IssuerTrustSystem.Custom, EcosystemProfileCatalog.require(
                EcosystemProfileId.CUSTOM_ISSUANCE_2026_1,
                EcosystemProfileRole.ISSUANCE).policy().trustSystem());
        assertEquals(IssuerTrustSystem.Custom, EcosystemProfileCatalog.require(
                EcosystemProfileId.CUSTOM_PRESENTATION_2026_1,
                EcosystemProfileRole.PRESENTATION).policy().trustSystem());
        assertEquals(IssuerTrustSystem.OIDF, EcosystemProfileCatalog.require(
                EcosystemProfileId.OIDF_ISSUANCE_2026_1,
                EcosystemProfileRole.ISSUANCE).policy().trustSystem());
        assertEquals(IssuerTrustSystem.OIDF, EcosystemProfileCatalog.require(
                EcosystemProfileId.OIDF_PRESENTATION_2026_1,
                EcosystemProfileRole.PRESENTATION).policy().trustSystem());
    }
}
