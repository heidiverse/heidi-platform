// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.heidiverse.heidi.coordinator.model.CredentialSchemeIssuerResponse;
import org.heidiverse.heidi.coordinator.model.issuance.IssuanceData;
import org.heidiverse.heidi.coordinator.model.oid4vci.PreAuthIssuanceData;
import org.heidiverse.heidi.coordinator.model.oid4vci.CredentialOfferType;
import org.junit.jupiter.api.Test;

class IssuanceProcessServiceTest {

    @Test
    void resolvesAssignedIssuerWhenNoOverrideIsGiven() {
        var entityClient = mock(CoordinatorEntityGateway.class);
        when(entityClient.getCredentialSchemeIssuer("test-credential", "1.0"))
                .thenReturn(new CredentialSchemeIssuerResponse(
                        "assigned-issuer", "tenant-1", CredentialOfferType.VALUE,
                        "EUDI_ISSUANCE_2026_1"));

        var service = new IssuanceProcessService(entityClient);

        var normalized = service.normalize(data(null, "EUDI_ISSUANCE_2026_1"));

        assertEquals("assigned-issuer", normalized.issuerSlug());
        assertEquals(data(null, "EUDI_ISSUANCE_2026_1").values(), normalized.values());
    }

    @Test
    void preservesExplicitIssuerOverride() {
        var entityClient = mock(CoordinatorEntityGateway.class);
        when(entityClient.getCredentialSchemeIssuer("test-credential", "1.0"))
                .thenReturn(new CredentialSchemeIssuerResponse(
                        "configured-issuer", "tenant-1", CredentialOfferType.URI,
                        "SWISS_ISSUANCE_2026_1"));
        var service = new IssuanceProcessService(entityClient);

        var normalized = service.normalize(data("partner-issuer", "SWISS_ISSUANCE_2026_1"));

        assertEquals("partner-issuer", normalized.issuerSlug());
        assertEquals(CredentialOfferType.URI, normalized.credentialOfferType());
        assertEquals("SWISS_ISSUANCE_2026_1", normalized.issuanceProfileId());
    }

    @Test
    void usesIssuerResolvedDuringAuthentication() {
        var entityClient = mock(CoordinatorEntityGateway.class);
        var service = new IssuanceProcessService(entityClient);
        var issuer = new CredentialSchemeIssuerResponse(
                "assigned-issuer", "tenant-1", CredentialOfferType.VALUE,
                "EUDI_ISSUANCE_2026_1");

        var normalized = service.normalize(data(null, "EUDI_ISSUANCE_2026_1"), issuer);

        assertEquals("assigned-issuer", normalized.issuerSlug());
        verifyNoInteractions(entityClient);
    }

    @Test
    void carriesCredentialOfferTypeFromTheSchema() {
        var entityClient = mock(CoordinatorEntityGateway.class);
        when(entityClient.getCredentialSchemeIssuer("test-credential", "1.0"))
                .thenReturn(new CredentialSchemeIssuerResponse(
                        "assigned-issuer", "tenant-1", CredentialOfferType.URI,
                        "EUDI_ISSUANCE_2026_1"));

        var normalized = new IssuanceProcessService(entityClient).normalize(
                data(null, "EUDI_ISSUANCE_2026_1"));

        assertEquals(CredentialOfferType.URI, normalized.credentialOfferType());
    }

    @Test
    void carriesIssuanceProfileFromTheSchema() {
        var entityClient = mock(CoordinatorEntityGateway.class);
        when(entityClient.getCredentialSchemeIssuer("test-credential", "1.0"))
                .thenReturn(new CredentialSchemeIssuerResponse(
                        "assigned-issuer", "tenant-1", CredentialOfferType.VALUE,
                        "SWISS_ISSUANCE_2026_1"));

        var normalized = new IssuanceProcessService(entityClient).normalize(
                data(null, "SWISS_ISSUANCE_2026_1"));

        assertEquals("SWISS_ISSUANCE_2026_1", normalized.issuanceProfileId());
    }

    @Test
    void reportsUnavailableSchemaWhenEntityReturnsNotFound() {
        var entityClient = mock(CoordinatorEntityGateway.class);
        when(entityClient.getCredentialSchemeIssuer("test-credential", "1.0"))
                .thenThrow(new EntityNotFoundException("Credential scheme not found"));

        var service = new IssuanceProcessService(entityClient);

        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.normalize(data(null, "EUDI_ISSUANCE_2026_1")));

        assertEquals(
                "Issuance schema is not available for issuance: test-credential / 1.0",
                exception.getMessage());
    }

    private PreAuthIssuanceData data(String issuerSlug, String profile) {
        return new PreAuthIssuanceData(
                new IssuanceData.SchemaIdentifier("test-credential", "1.0"),
                Map.of("given_name", "Ada"),
                null,
                issuerSlug,
                false,
                CredentialOfferType.VALUE,
                profile);
    }
}
