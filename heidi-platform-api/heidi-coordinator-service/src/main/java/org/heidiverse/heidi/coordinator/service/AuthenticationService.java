// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service;

import org.heidiverse.heidi.coordinator.model.CredentialSchemeIssuerResponse;
import org.heidiverse.heidi.coordinator.model.CredentialSchemeTenantResponse;
import org.heidiverse.heidi.coordinator.model.ProofSchemeResponse;
import org.heidiverse.heidi.coordinator.model.api.InitializeProcessRequest;
import org.heidiverse.heidi.coordinator.model.integration.IntegrationAuthorizedCredentialsResponse;
import org.heidiverse.heidi.coordinator.model.integration.IntegrationScope;
import org.heidiverse.heidi.coordinator.model.jwt.JwtUserProfile;
import org.heidiverse.heidi.coordinator.model.jwt.UserRole;
import org.heidiverse.heidi.coordinator.model.oid4vci.Action;
import org.heidiverse.heidi.coordinator.service.utils.JwtUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

@Service
public class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);

    private final CoordinatorEntityGateway entityGateway;
    private final List<InitializeProcessAuthenticationProvider> authenticationProviders;

    @Value("${heidi.platform.server.api.basic-auth:#{null}}")
    private String serverBasicAuth;

    public AuthenticationService(
            CoordinatorEntityGateway entityGateway,
            List<InitializeProcessAuthenticationProvider> authenticationProviders) {
        this.entityGateway = entityGateway;
        this.authenticationProviders = authenticationProviders;
    }

    /**
     * This method throws an error if the user is not allowed to issue the requested credential or
     * if the required scope is not authorized.
     *
     * @param authorizationHeader
     * @param request
     * @param requiredScope
     */
    public CredentialSchemeIssuerResponse performAuthenticationForInitializeProcess(
            String authorizationHeader,
            InitializeProcessRequest request,
            IntegrationScope requiredScope) {
        return performAuthenticationForInitializeProcess(
                authorizationHeader, request, extractCredentialIdentifier(request), requiredScope);
    }

    public CredentialSchemeIssuerResponse performAuthenticationForInitializeProcess(
            String authorizationHeader,
            InitializeProcessRequest request,
            String credentialIdentifier,
            IntegrationScope requiredScope) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new BadCredentialsException("Authorization header is required");
        }

        for (InitializeProcessAuthenticationProvider provider : authenticationProviders) {
            if (provider.supports(authorizationHeader)) {
                provider.authenticate(
                        authorizationHeader, request, credentialIdentifier, requiredScope);
                return null;
            }
        }

        // Extract authentication method
        if (authorizationHeader.startsWith("Bearer")) {
            var version = issuerVersion(request);
            if (version != null) {
                return authenticateJwt(credentialIdentifier, version);
            }
            performJwtAuthentication(credentialIdentifier);
        } else if (authorizationHeader.startsWith("ApiKey")) {
            String apiKey = authorizationHeader.substring("ApiKey ".length());
            performApiKeyAuthentication(apiKey, credentialIdentifier, requiredScope);
        } else if (authorizationHeader.startsWith("Basic")) {
            String basicCredentials = authorizationHeader.substring("Basic ".length());
            performBasicAuthAuthentication(basicCredentials);
        } else {
            throw new BadCredentialsException("Invalid authorization header format");
        }

        return null;
    }

    /**
     * Resolves the tenant owning the resource used by a process. This is deliberately based on
     * the credential schema or proof scheme rather than on the caller's JWT: super-admins may
     * operate on a selected tenant without carrying that tenant in {@code companyId}, and shared
     * service credentials do not identify one tenant themselves.
     */
    public String resolveProcessTenantId(InitializeProcessRequest request) {
        if (Action.PRE_AUTH_ISSUANCE.equals(request.action())) {
            if (request.preAuthIssuanceData() == null
                    || request.preAuthIssuanceData().schemaIdentifier() == null) {
                throw new BadCredentialsException("Pre-authorized issuance data is required");
            }
            String credentialIdentifier =
                    request.preAuthIssuanceData().schemaIdentifier().credentialIdentifier();
            CredentialSchemeTenantResponse response =
                    entityGateway.getCredentialSchemeTenant(credentialIdentifier);
            return requireProcessTenantId(
                    response == null ? null : response.tenantId(),
                    "credential schema " + credentialIdentifier);
        }

        if (Action.PRESENTATION.equals(request.action())) {
            if (request.presentationData() == null
                    || request.presentationData().proofSchemeId() == null) {
                throw new BadCredentialsException("Presentation data is required");
            }
            String proofSchemeId = request.presentationData().proofSchemeId();
            ProofSchemeResponse response = entityGateway.getProofScheme(proofSchemeId);
            return requireProcessTenantId(
                    response == null ? null : response.tenantId(),
                    "proof scheme " + proofSchemeId);
        }

        throw new BadCredentialsException(
                "Unsupported action for process tenant resolution: " + request.action());
    }

    /** Returns the public client configuration belonging to an organisation. */
    public JsonNode getTenantClientConfiguration(String tenantId) {
        var tenant = entityGateway.getTenantInformation(tenantId);
        return tenant == null ? null : tenant.clientConfiguration();
    }

    private String requireProcessTenantId(String tenantId, String resourceDescription) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException(
                    "The " + resourceDescription + " is not linked to a tenant");
        }
        return tenantId;
    }

    public void performBasicAuthAuthentication(String basicAuth) {
        if (serverBasicAuth == null) {
            throw new BadCredentialsException(
                    "Invalid authorization header format [Not configured]");
        }
        if (!MessageDigest.isEqual(
                basicAuth.getBytes(StandardCharsets.UTF_8),
                serverBasicAuth.getBytes(StandardCharsets.UTF_8))) {
            // Deliberately without the supplied value: it is a shared secret, and this
            // message reaches logs.
            throw new BadCredentialsException("Invalid credentials");
        }
    }

    public void performJwtAuthentication(String credentialIdentifier) {
        authenticateJwt(credentialIdentifier, null);
    }

    private CredentialSchemeIssuerResponse authenticateJwt(
            String credentialIdentifier, String version) {
        JwtUserProfile userProfile = JwtUtils.getUserProfile();

        List<UserRole> allowedRoles =
                List.of(UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR);
        if (userProfile.permissions().stream().noneMatch(allowedRoles::contains)) {
            throw new BadCredentialsException("User lacks necessary permissions");
        }

        if (userProfile.permissions().contains(UserRole.SUPER_ADMIN)) {
            return null;
        }

        if (version == null) {
            CredentialSchemeTenantResponse tenantInfo =
                    entityGateway.getCredentialSchemeTenant(credentialIdentifier);

            if (userProfile.tenantId() == null
                    || userProfile.tenantId().isBlank()
                    || tenantInfo == null
                    || tenantInfo.tenantId() == null
                    || !userProfile.tenantId().equals(tenantInfo.tenantId())) {
                throw new BadCredentialsException(
                        "User is not authorized to operate on this credential because of tenant ID"
                            + " mismatch");
            }

            return null;
        }

        CredentialSchemeIssuerResponse issuerInfo;
        try {
            issuerInfo =
                    entityGateway.getCredentialSchemeIssuer(
                            credentialIdentifier, version);
        } catch (EntityNotFoundException exception) {
            throw unavailableSchema(credentialIdentifier, version);
        }

        if (issuerInfo.issuerSlug() == null || issuerInfo.issuerSlug().isBlank()) {
            throw unavailableSchema(credentialIdentifier, version);
        }

        if (userProfile.tenantId() == null
                || userProfile.tenantId().isBlank()
                || issuerInfo.tenantId() == null
                || !userProfile.tenantId().equals(issuerInfo.tenantId())) {
            throw new BadCredentialsException(
                    "User is not authorized to operate on this credential because of tenant ID"
                            + " mismatch");
        }
        return issuerInfo;
    }

    private String issuerVersion(InitializeProcessRequest request) {
        if (!Action.PRE_AUTH_ISSUANCE.equals(request.action())) {
            return null;
        }

        var data = request.preAuthIssuanceData();
        if (data == null || hasText(data.issuerSlug()) || data.schemaIdentifier() == null) {
            return null;
        }
        return data.schemaIdentifier().version();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private IllegalArgumentException unavailableSchema(String credentialIdentifier, String version) {
        return new IllegalArgumentException(
                "Issuance schema is not available for issuance: "
                        + credentialIdentifier
                        + " / "
                        + version);
    }

    private void performApiKeyAuthentication(
            String apiKey, String credentialIdentifier, IntegrationScope requiredScope) {
        try {
            // Check authorized credentials via API key
            IntegrationAuthorizedCredentialsResponse response =
                    entityGateway.getAuthorizedCredentials(apiKey);

            // Validate credential identifier
            List<String> authorizedCredentials = response.credentialIdentifiers();
            if (authorizedCredentials == null
                    || !authorizedCredentials.contains(credentialIdentifier)) {
                throw new BadCredentialsException(
                        "API key is not authorized for this schema identifier");
            }

            // Validate scope
            List<IntegrationScope> authorizedScopes = response.scopes();
            if (authorizedScopes == null || !authorizedScopes.contains(requiredScope)) {
                throw new BadCredentialsException(
                        "API key lacks required scope: " + requiredScope.getValue());
            }
        } catch (EntityNotFoundException e) {
            throw new BadCredentialsException(
                    "API key not authorized - No credentials found for this key", e);
        }
    }

    private String extractCredentialIdentifier(InitializeProcessRequest request) {
        if (Action.PRE_AUTH_ISSUANCE.equals(request.action())) {
            return Objects.requireNonNull(request.preAuthIssuanceData())
                    .schemaIdentifier()
                    .credentialIdentifier();
        }
        if (Action.PRESENTATION.equals(request.action())) {
            try {
                ProofSchemeResponse proofScheme =
                        entityGateway.getProofScheme(request.presentationData().proofSchemeId());
                List<ProofSchemeResponse.CredentialScheme> credentialSchemes =
                        proofScheme.credentialSchemes();
                if (credentialSchemes == null || credentialSchemes.isEmpty()) {
                    throw new BadCredentialsException(
                            "No credential schemas found for proofSchemeId: "
                                    + request.presentationData().proofSchemeId());
                }
                return credentialSchemes.getFirst().credentialIdentifier();
            } catch (EntityNotFoundException e) {
                logger.error(
                        "Proof scheme not found for proofSchemeId: {}",
                        request.presentationData().proofSchemeId(),
                        e);
                throw new BadCredentialsException(
                        "Proof scheme not found for proofSchemeId: "
                                + request.presentationData().proofSchemeId(),
                        e);
            } catch (IllegalArgumentException e) {
                logger.error(
                        "Error fetching proof scheme for proofSchemeId: {}",
                        request.presentationData().proofSchemeId(),
                        e);
                throw new BadCredentialsException(
                        "Failed to fetch proof scheme due to service error for proofSchemeId: "
                                + request.presentationData().proofSchemeId(),
                        e);
            }
        }
        throw new BadCredentialsException(
                "Unsupported action for credential-based authentication: " + request.action());
    }
}
