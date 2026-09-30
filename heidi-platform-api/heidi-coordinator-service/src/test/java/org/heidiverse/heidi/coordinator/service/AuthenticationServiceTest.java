// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.heidiverse.heidi.coordinator.model.CredentialSchemeIssuerResponse;
import org.heidiverse.heidi.coordinator.model.api.InitializeProcessRequest;
import org.heidiverse.heidi.coordinator.model.integration.IntegrationScope;
import org.heidiverse.heidi.coordinator.model.jwt.JwtUserProfile;
import org.heidiverse.heidi.coordinator.model.jwt.UserRole;
import org.heidiverse.heidi.coordinator.model.oid4vci.Action;
import org.heidiverse.heidi.coordinator.model.oid4vci.CredentialOfferType;
import org.heidiverse.heidi.coordinator.model.oid4vci.PreAuthIssuanceData;
import org.heidiverse.heidi.coordinator.model.issuance.IssuanceData;
import org.heidiverse.heidi.coordinator.service.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class AuthenticationServiceTest {

    @Test
    void reusesIssuerLookupForJwtAuthentication() {
        var entityClient = mock(CoordinatorEntityGateway.class);
        var issuer = new CredentialSchemeIssuerResponse(
                "assigned-issuer", "tenant-1", CredentialOfferType.VALUE,
                "EUDI_ISSUANCE_2026_1");
        when(entityClient.getCredentialSchemeIssuer("test-credential", "1.0"))
                .thenReturn(issuer);

        var service = new AuthenticationService(entityClient, List.of());
        var request =
                new InitializeProcessRequest(
                        Action.PRE_AUTH_ISSUANCE,
                        new PreAuthIssuanceData(
                                new IssuanceData.SchemaIdentifier("test-credential", "1.0"),
                                Map.of(),
                                null,
                                null,
                                false,
                                org.heidiverse.heidi.coordinator.model.oid4vci.CredentialOfferType.VALUE,
                                "EUDI_ISSUANCE_2026_1"),
                        null);
        var profile =
                new JwtUserProfile(
                        "subject", "user", List.of(UserRole.OPERATOR), "tenant-1", "User");

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(JwtUtils::getUserProfile).thenReturn(profile);

            var resolved =
                    service.performAuthenticationForInitializeProcess(
                            "Bearer token", request, IntegrationScope.ISSUE);

            assertEquals(issuer, resolved);
        }

        verify(entityClient).getCredentialSchemeIssuer("test-credential", "1.0");
        verify(entityClient, never()).getCredentialSchemeTenant(anyString());
    }
}
