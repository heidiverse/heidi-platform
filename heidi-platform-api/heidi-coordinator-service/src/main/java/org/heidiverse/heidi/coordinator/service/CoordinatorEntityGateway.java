// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service;

import org.heidiverse.heidi.coordinator.model.CredentialContextResponse;
import org.heidiverse.heidi.coordinator.model.CredentialSchemeIssuerResponse;
import org.heidiverse.heidi.coordinator.model.CredentialSchemeTenantResponse;
import org.heidiverse.heidi.coordinator.model.ProofSchemeResponse;
import org.heidiverse.heidi.coordinator.model.TenantResponse;
import org.heidiverse.heidi.coordinator.model.integration.IntegrationAuthorizedCredentialsResponse;

/** In-process access to platform-owned entity data needed by the coordinator. */
public interface CoordinatorEntityGateway {

    CredentialContextResponse getCredentialContext(String identifier, String version);

    ProofSchemeResponse getProofScheme(String proofSchemeId);

    CredentialSchemeTenantResponse getCredentialSchemeTenant(String credentialIdentifier);

    CredentialSchemeIssuerResponse getCredentialSchemeIssuer(String identifier, String version);

    TenantResponse getTenantInformation(String tenantId);

    IntegrationAuthorizedCredentialsResponse getAuthorizedCredentials(String apiKey);
}
