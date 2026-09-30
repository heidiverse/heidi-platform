// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.service;

import org.heidiverse.heidi.coordinator.model.CredentialContextResponse;
import org.heidiverse.heidi.coordinator.model.CredentialSchemeIssuerResponse;
import org.heidiverse.heidi.coordinator.model.CredentialSchemeTenantResponse;
import org.heidiverse.heidi.coordinator.model.ProofSchemeResponse;
import org.heidiverse.heidi.coordinator.model.integration.IntegrationAuthorizedCredentialsResponse;
import org.heidiverse.heidi.coordinator.service.CoordinatorEntityGateway;
import org.heidiverse.heidi.coordinator.service.EntityNotFoundException;
import org.heidiverse.heidi.entity.model.exceptions.ProofSchemeNotFoundException;
import org.heidiverse.heidi.entity.model.exceptions.SchemaNotFoundException;
import org.heidiverse.heidi.entity.model.tenant.TenantResponse;
import org.heidiverse.heidi.entity.service.CredentialSchemeService;
import org.heidiverse.heidi.entity.service.IntegrationService;
import org.heidiverse.heidi.entity.service.ProofSchemeService;
import org.heidiverse.heidi.entity.service.TenantService;

import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LocalCoordinatorEntityGateway implements CoordinatorEntityGateway {

    private final CredentialSchemeService credentialSchemeService;
    private final ProofSchemeService proofSchemeService;
    private final TenantService tenantService;
    private final IntegrationService integrationService;
    private final ObjectMapper objectMapper;

    public LocalCoordinatorEntityGateway(
            CredentialSchemeService credentialSchemeService,
            ProofSchemeService proofSchemeService,
            TenantService tenantService,
            IntegrationService integrationService,
            ObjectMapper objectMapper) {
        this.credentialSchemeService = credentialSchemeService;
        this.proofSchemeService = proofSchemeService;
        this.tenantService = tenantService;
        this.integrationService = integrationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public CredentialContextResponse getCredentialContext(String identifier, String version) {
        try {
            var context =
                    credentialSchemeService.getCredentialContextByCredentialIdentifierAndVersion(
                            identifier, version, false);
            return convert(context, CredentialContextResponse.class);
        } catch (SchemaNotFoundException exception) {
            throw new EntityNotFoundException(exception.getMessage(), exception);
        }
    }

    @Override
    public ProofSchemeResponse getProofScheme(String proofSchemeId) {
        try {
            var proofScheme = proofSchemeService.findById(UUID.fromString(proofSchemeId), false);
            return convert(proofScheme, ProofSchemeResponse.class);
        } catch (ProofSchemeNotFoundException exception) {
            throw new EntityNotFoundException(exception.getMessage(), exception);
        }
    }

    @Override
    public CredentialSchemeTenantResponse getCredentialSchemeTenant(String credentialIdentifier) {
        return credentialSchemeService
                .findTenantIdByCredentialIdentifier(credentialIdentifier)
                .map(tenantId -> new CredentialSchemeTenantResponse(tenantId, credentialIdentifier))
                .orElseThrow(() -> new EntityNotFoundException("Credential scheme not found"));
    }

    @Override
    public CredentialSchemeIssuerResponse getCredentialSchemeIssuer(
            String identifier, String version) {
        return credentialSchemeService
                .findIssuerSlugByCredentialIdentifierAndVersion(identifier, version)
                .map(response -> convert(response, CredentialSchemeIssuerResponse.class))
                .orElseThrow(() -> new EntityNotFoundException("Credential scheme not found"));
    }

    @Override
    public org.heidiverse.heidi.coordinator.model.TenantResponse getTenantInformation(
            String tenantId) {
        return tenantService
                .getTenant(tenantId)
                .map(TenantResponse::from)
                .map(response -> convert(
                        response, org.heidiverse.heidi.coordinator.model.TenantResponse.class))
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
    }

    @Override
    public IntegrationAuthorizedCredentialsResponse getAuthorizedCredentials(String apiKey) {
        var credentialIdentifiers = integrationService.findCredentialsByApiKey(apiKey);
        if (credentialIdentifiers.isEmpty()) {
            throw new EntityNotFoundException("Integration not found");
        }

        var response =
                new org.heidiverse.heidi.entity.model.integration.IntegrationAuthorizedCredentialsResponse(
                        credentialIdentifiers,
                        integrationService.findScopesByApiKey(apiKey),
                        integrationService.findTenantIdByApiKey(apiKey));
        return convert(response, IntegrationAuthorizedCredentialsResponse.class);
    }

    private <T> T convert(Object value, Class<T> targetType) {
        return objectMapper.convertValue(value, targetType);
    }
}
