// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.heidiverse.heidi.coordinator.ws.controller.KeyManagementCoordinatorController;
import org.heidiverse.heidi.coordinator.ws.controller.SigningProviderCoordinatorController;
import org.heidiverse.heidi.entity.model.catalog.AttributeCatalog;
import org.heidiverse.heidi.entity.model.catalog.CatalogAttribute;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeDetail;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeDetailResponse;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeResponse;
import org.heidiverse.heidi.entity.model.entity.LibrarySourceEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyEntity;
import org.heidiverse.heidi.entity.model.entity.TenantEntity;
import org.heidiverse.heidi.entity.model.integration.IntegrationDetail;
import org.heidiverse.heidi.entity.model.integration.IntegrationPayload;
import org.heidiverse.heidi.entity.model.integration.IntegrationScope;
import org.heidiverse.heidi.entity.model.issuer.IssuerDefinitionRequest;
import org.heidiverse.heidi.entity.model.issuer.IssuerDefinitionResponse;
import org.heidiverse.heidi.entity.model.issuer.KeyRotationMode;
import org.heidiverse.heidi.entity.model.issuer.KeyRotationPolicy;
import org.heidiverse.heidi.entity.model.issuer.PlatformKeyRequest;
import org.heidiverse.heidi.entity.model.issuer.PlatformKeyResponse;
import org.heidiverse.heidi.entity.model.proofscheme.ProofSchemeDetail;
import org.heidiverse.heidi.entity.model.proofscheme.ProofSchemePayload;
import org.heidiverse.heidi.entity.model.proofscheme.ProofSchemeResponse;
import org.heidiverse.heidi.entity.model.signing.SigningProviderRequest;
import org.heidiverse.heidi.entity.model.signing.SigningProviderResponse;
import org.heidiverse.heidi.entity.model.statuslist.StatusListPublishMode;
import org.heidiverse.heidi.entity.model.statuslist.StatusListRequest;
import org.heidiverse.heidi.entity.model.statuslist.StatusListResponse;
import org.heidiverse.heidi.entity.model.statuslist.StatusListType;
import org.heidiverse.heidi.entity.model.tenant.TenantRequest;
import org.heidiverse.heidi.entity.model.template.I14yTemplateImportRequest;
import org.heidiverse.heidi.entity.model.template.I14yTemplateImportResponse;
import org.heidiverse.heidi.entity.service.AttributeCatalogService;
import org.heidiverse.heidi.entity.service.CredentialSchemeService;
import org.heidiverse.heidi.entity.service.IdentityFederationService;
import org.heidiverse.heidi.entity.service.IntegrationService;
import org.heidiverse.heidi.entity.service.IssuerService;
import org.heidiverse.heidi.entity.service.PrivateKeyImportService;
import org.heidiverse.heidi.entity.service.ProofSchemeService;
import org.heidiverse.heidi.entity.service.SigningKeyService;
import org.heidiverse.heidi.entity.service.SigningProviderService;
import org.heidiverse.heidi.entity.service.StatusListService;
import org.heidiverse.heidi.entity.service.TemplateService;
import org.heidiverse.heidi.entity.service.TenantService;
import org.heidiverse.heidi.entity.service.WalletCatalogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import org.heidiverse.heidi.shared.signing.SigningKeyUsage;

class CockpitCrudControllerTest {

    private static final String TENANT_ID = "tenant-a";

    @BeforeEach
    void authenticateTenant() {
        var jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("cockpit-user")
                .claim("companyId", TENANT_ID)
                .claim("permissions", List.of("ADMIN"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void credentialSchemaCrudLifecycleDelegatesToService() throws Exception {
        var service = mock(CredentialSchemeService.class);
        var controller = new CredentialSchemaController(service);
        var request = mock(CredentialSchemeDetail.class);
        var id = UUID.randomUUID();
        when(service.create(request, TENANT_ID)).thenReturn(id);

        var created = controller.createCredentialScheme(request, TENANT_ID);

        assertEquals(new CredentialSchemeResponse(
                id, "Issuance schema successfully created."), created.getBody());
        verify(service).create(request, TENANT_ID);

        var detail = mock(CredentialSchemeDetailResponse.class);
        when(service.findById(id, true)).thenReturn(detail);
        assertSame(detail, controller.getCredentialScheme(id).getBody());
        verify(service).findById(id, true);

        controller.updateCredentialScheme(id, request);
        verify(service).update(id, request);

        controller.archiveCredentialScheme(id);
        verify(service).archive(id);
    }

    @Test
    void i14yTemplateImportDelegatesToService() {
        var service = mock(TemplateService.class);
        var controller = new TemplateController(service);
        var datasetId = UUID.randomUUID();
        var request = new I14yTemplateImportRequest(datasetId.toString());
        var response = mock(I14yTemplateImportResponse.class);
        when(service.importI14yDataset(datasetId.toString())).thenReturn(response);

        assertSame(response, controller.importI14yTemplate(request).getBody());
        verify(service).importI14yDataset(datasetId.toString());
    }

    @Test
    void proofSchemaCrudLifecycleDelegatesToService() throws Exception {
        var service = mock(ProofSchemeService.class);
        var controller = new ProofSchemeController(service);
        var request = mock(ProofSchemePayload.class);
        var detail = mock(ProofSchemeDetail.class);
        var id = UUID.randomUUID();
        when(service.insertProofScheme(request, TENANT_ID)).thenReturn(detail);
        when(detail.uuid()).thenReturn(id);

        var created = controller.createProofScheme(request, TENANT_ID);

        assertEquals(new ProofSchemeResponse(
                id, "Proof scheme successfully created."), created.getBody());
        verify(service).insertProofScheme(request, TENANT_ID);

        when(service.findById(id, true)).thenReturn(detail);
        assertSame(detail, controller.getProofScheme(id).getBody());
        verify(service).findById(id, true);

        controller.updateProofScheme(id, request);
        verify(service).updateProofScheme(id, request);

        controller.archiveProofScheme(id);
        verify(service).archiveProofScheme(id);
    }

    @Test
    void statusListCrudLifecycleDelegatesToService() {
        var service = mock(StatusListService.class);
        var controller = new StatusListController(service);
        var id = UUID.randomUUID();
        var request = new StatusListRequest(
                "revocations", StatusListType.SD_JWT_VC, 1, 32,
                StatusListPublishMode.LOCAL, null, UUID.randomUUID(), 3600L);
        var response = mock(StatusListResponse.class);
        when(service.create(TENANT_ID, request)).thenReturn(response);
        when(service.find(TENANT_ID, id)).thenReturn(response);
        when(service.findAll(TENANT_ID)).thenReturn(List.of(response));

        assertSame(response, controller.create(request, TENANT_ID));
        verify(service).create(TENANT_ID, request);
        assertSame(response, controller.find(id, TENANT_ID));
        verify(service).find(TENANT_ID, id);
        assertEquals(List.of(response), controller.findAll(TENANT_ID));
        verify(service).findAll(TENANT_ID);

        var deleted = controller.delete(id, TENANT_ID);
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());
        verify(service).delete(TENANT_ID, id);
    }

    @Test
    void integrationCrudLifecycleDelegatesToService() {
        var service = mock(IntegrationService.class);
        var controller = new IntegrationController(service);
        var id = UUID.randomUUID();
        var payload = new IntegrationPayload(
                "Cockpit integration", List.of("example.credential"),
                List.of(IntegrationScope.ISSUE), false);
        var detail = new IntegrationDetail(
                id, "Cockpit integration", payload.credentialIdentifiers(),
                payload.scopes(), "api-key", TENANT_ID, false);
        when(service.createIntegration(payload, TENANT_ID)).thenReturn(detail);
        when(service.updateIntegration(id, payload)).thenReturn(detail);
        when(service.findById(id)).thenReturn(detail);

        assertSame(detail, controller.createIntegration(payload, TENANT_ID).getBody());
        verify(service).createIntegration(payload, TENANT_ID);
        assertSame(detail, controller.getIntegration(id).getBody());
        verify(service).findById(id);
        assertSame(detail, controller.updateIntegration(id, payload).getBody());
        verify(service).updateIntegration(id, payload);
        assertEquals("Integration successfully deleted.",
                controller.deleteIntegration(id).getBody());
        verify(service).deleteIntegration(id);
    }

    @Test
    void tenantIdentityCrudLifecycleDelegatesToServices() throws Exception {
        var identities = mock(IssuerService.class);
        var tenants = mock(TenantService.class);
        var response = new IssuerDefinitionResponse(7, "issuer", null, null, TENANT_ID, null, Set.of());
        var request = new IssuerDefinitionRequest("issuer", null, null);
        var tenant = new TenantEntity();
        tenant.setTenantId(TENANT_ID);
        when(tenants.getTenant(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identities.createIssuer(request, TENANT_ID)).thenReturn(response);
        when(identities.updateTenantIdentity(TENANT_ID, 7, request)).thenReturn(response);

        var controller = new IssuerController(
                identities, mock(org.heidiverse.heidi.coordinator.service.AuthenticationService.class),
                tenants, mock(IdentityFederationService.class));

        assertSame(response, controller.createTenantIdentity(TENANT_ID, request).getBody());
        verify(identities).createIssuer(request, TENANT_ID);
        verify(tenants).addIdentity(TENANT_ID, 7);
        when(identities.findIssuerById(7)).thenReturn(Optional.of(response));
        assertSame(response, controller.getIssuerById(7).getBody());
        verify(identities).findIssuerById(7);

        assertSame(response, controller.updateTenantIdentity(TENANT_ID, 7, request).getBody());
        verify(identities).updateTenantIdentity(TENANT_ID, 7, request);
        assertEquals(HttpStatus.NO_CONTENT,
                controller.deleteTenantIdentity(TENANT_ID, 7).getStatusCode());
        verify(identities).deleteTenantIdentity(TENANT_ID, 7);
        verify(tenants).removeIdentity(TENANT_ID, 7);
    }

    @Test
    void signingKeyCrudLifecycleDelegatesToService() {
        var service = mock(SigningKeyService.class);
        var key = new SigningKeyEntity();
        var keyId = UUID.randomUUID();
        key.setId(keyId);
        key.setLogicalKeyId("issuer-signing");
        key.setProviderId(3);
        key.setCreatedAt(Instant.now());
        when(service.keys(TENANT_ID)).thenReturn(List.of(key));
        when(service.allVersions(keyId)).thenReturn(List.of());
        var controller = new KeyManagementCoordinatorController(
                service, mock(IssuerService.class), mock(PrivateKeyImportService.class));
        var request = new PlatformKeyRequest(
                "issuer-signing", "ES256", 3, SigningKeyUsage.SIGN, null);

        var created = controller.create(TENANT_ID, request);
        assertEquals("issuer-signing", created.keyId());
        verify(service).create(TENANT_ID, "issuer-signing", Set.of(SigningKeyUsage.SIGN), "ES256", 3, null);

        assertEquals(List.of("issuer-signing"),
                controller.findAll(TENANT_ID).stream().map(PlatformKeyResponse::keyId).toList());
        var policy = new KeyRotationPolicy(KeyRotationMode.MANUAL, null, 60);
        controller.updateRotationPolicy("issuer-signing", TENANT_ID, policy);
        verify(service).updateRotationPolicy(TENANT_ID, keyId, policy);

        assertEquals(HttpStatus.NO_CONTENT,
                controller.delete("issuer-signing", TENANT_ID).getStatusCode());
        verify(service).delete(TENANT_ID, keyId);
    }

    @Test
    void signingProviderCrudLifecycleDelegatesToService() {
        var service = mock(SigningProviderService.class);
        var controller = new SigningProviderCoordinatorController(service);
        var request = new SigningProviderRequest(
                "provider", "https://signer.example", null, false, "none");
        var response = new SigningProviderResponse(
                3, "provider", "https", "https://signer.example", "none", false,
                List.of(), List.of(), List.of(), List.of(), true, false, true,
                "tenant", TENANT_ID);
        when(service.create(TENANT_ID, request)).thenReturn(response);
        when(service.findAll(TENANT_ID)).thenReturn(List.of(response));

        assertSame(response, controller.create(TENANT_ID, request));
        verify(service).create(TENANT_ID, request);
        assertEquals(List.of(response), controller.findAll(TENANT_ID));
        verify(service).findAll(TENANT_ID);
        controller.delete(3, TENANT_ID);
        verify(service).delete(TENANT_ID, 3);
    }

    @Test
    void attributeCatalogAndAttributeCrudLifecycleDelegatesToService() {
        var service = mock(AttributeCatalogService.class);
        var controller = new AttributeCatalogController(service);
        var catalog = new AttributeCatalog(4, "Catalog", "Specification", "https://example");
        var attribute = new CatalogAttribute();
        when(service.createCatalog(catalog)).thenReturn(catalog);
        when(service.getAllCatalogs()).thenReturn(List.of(catalog));
        when(service.getCatalogById(4)).thenReturn(Optional.of(catalog));
        when(service.updateCatalog(4, catalog)).thenReturn(catalog);
        when(service.createAttribute(4, attribute)).thenReturn(attribute);
        when(service.getAttributesByCatalogId(4)).thenReturn(List.of(attribute));
        when(service.getAttributeById(5)).thenReturn(Optional.of(attribute));
        when(service.updateAttribute(5, attribute)).thenReturn(attribute);

        assertSame(catalog, controller.createCatalog(catalog).getBody());
        assertEquals(List.of(catalog), controller.getAllCatalogs().getBody());
        assertSame(catalog, controller.getCatalog(4).getBody());
        assertSame(catalog, controller.updateCatalog(4, catalog).getBody());
        controller.deleteCatalog(4);
        verify(service).deleteCatalog(4);

        assertSame(attribute, controller.createAttribute(4, attribute).getBody());
        assertEquals(List.of(attribute), controller.getAttributes(4).getBody());
        assertSame(attribute, controller.getAttribute(5).getBody());
        assertSame(attribute, controller.updateAttribute(5, attribute).getBody());
        controller.deleteAttribute(5);
        verify(service).deleteAttribute(5);
    }

    @Test
    void templateLibraryCrudLifecycleDelegatesToService() {
        var service = mock(TemplateService.class);
        var controller = new TemplateController(service);
        var id = UUID.randomUUID();
        var library = mock(LibrarySourceEntity.class);
        when(service.addLibrarySource("https://library.example")).thenReturn(library);
        when(service.updateLibrarySource(id, "https://updated.example")).thenReturn(library);
        when(service.getLibraries()).thenReturn(List.of());

        assertSame(library, controller.addLibrarySource("https://library.example").getBody());
        assertEquals(List.of(), controller.listLibraries().getBody());
        assertSame(library, controller.updateLibrarySource(id, "https://updated.example").getBody());
        controller.deleteLibrarySource(id);
        verify(service).deleteLibrary(id);
    }

    @Test
    void tenantCrudLifecycleDelegatesToService() {
        var tenants = mock(TenantService.class);
        var tenant = new TenantEntity();
        tenant.setTenantId(TENANT_ID);
        var request = new TenantRequest();
        when(tenants.getTenantIncludingDeleted(TENANT_ID)).thenReturn(Optional.of(tenant));
        var controller = new TenantController(tenants, mock(WalletCatalogService.class));

        assertEquals(TENANT_ID, controller.getTenant(TENANT_ID).getBody().tenantId());
        verify(tenants).getTenantIncludingDeleted(TENANT_ID);
        controller.updateTenant(TENANT_ID, request);
        verify(tenants).upsertTenant(TENANT_ID, request);
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteTenant(TENANT_ID).getStatusCode());
        verify(tenants).deleteTenant(TENANT_ID);
    }
}
