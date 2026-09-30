// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import org.heidiverse.heidi.entity.data.repository.SigningProviderRepository;
import org.heidiverse.heidi.entity.model.entity.SigningProviderEntity;
import org.heidiverse.heidi.entity.model.signing.SigningProviderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SigningProviderServiceTest {
    private static final String TENANT_ID = "tenant-a";
    private static final int GLOBAL_ID = 1;
    private static final int TENANT_ID_VALUE = 2;

    @Mock private SigningProviderRepository repository;
    @Mock private RestClient.Builder restClientBuilder;

    private SigningProviderService service;
    private SigningProviderEntity global;
    private SigningProviderEntity tenant;

    /** No backend publishes a client key here; the client status has its own test. */
    private static SigningClientStatusService noBackendKeys() {
        return new SigningClientStatusService(scheme -> java.util.Map.of());
    }

    @BeforeEach
    void setUp() {
        service = new SigningProviderService(
                repository,
                new ObjectMapper(),
                restClientBuilder,
                "01".repeat(32),
                "http://signing.example",
                "none",
                "",
                noBackendKeys());
        global = provider(null, true);
        tenant = provider(TENANT_ID, true);
    }

    @Test
    void fallsBackToGlobalDefaultWhenTenantHasNoOverride() {
        when(repository.findFirstByTenantIdAndDefaultProviderTrue(TENANT_ID))
                .thenReturn(Optional.empty());
        when(repository.findFirstByTenantIdIsNullAndDefaultProviderTrue())
                .thenReturn(Optional.of(global));

        assertEquals(global, service.defaultProvider(TENANT_ID));
    }

    @Test
    void prefersTenantDefaultOverGlobalDefault() {
        when(repository.findFirstByTenantIdAndDefaultProviderTrue(TENANT_ID))
                .thenReturn(Optional.of(tenant));

        assertEquals(tenant, service.defaultProvider(TENANT_ID));
    }

    @Test
    void allowsGlobalProviderButRejectsAnotherTenantsProvider() {
        when(repository.findById(GLOBAL_ID)).thenReturn(Optional.of(global));
        when(repository.findById(TENANT_ID_VALUE)).thenReturn(Optional.of(tenant));

        assertEquals(global, service.resolve("tenant-b", GLOBAL_ID));
        assertThrows(SecurityException.class, () -> service.resolve("tenant-b", TENANT_ID_VALUE));
    }

    @Test
    void rejectsTenantRegistrationAgainstGlobalProvider() {
        when(repository.findById(GLOBAL_ID)).thenReturn(Optional.of(global));

        assertThrows(SecurityException.class,
                () -> service.registerClient(TENANT_ID, GLOBAL_ID, "issuer"));
    }

    @Test
    void checksConnectionAndDiscoversTheServiceSchemeWithoutPersisting() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> {
            assertEquals("GET", request.getMethod().name());
            assertEquals("/v1/capabilities", request.getURI().getPath());
            assertEquals("Bearer secret", request.getHeaders().getFirst("Authorization"));
        }).andRespond(MockRestResponseCreators.withSuccess(
                "{\"scheme\":\"vault\",\"supportedAlgorithms\":[\"ES256\"],"
                        + "\"digestSigningAlgorithms\":[\"ES256\"],\"supportedOperations\":[],"
                        + "\"canCreate\":false,"
                        + "\"canImport\":false,\"canDelete\":true}",
                MediaType.APPLICATION_JSON));

        var checkService = new SigningProviderService(
                repository,
                new ObjectMapper(),
                builder,
                "01".repeat(32),
                "http://signing.example",
                "none",
                "",
                noBackendKeys());
        var result = checkService.checkConnection(new SigningProviderRequest(
                "Production Vault", "http://provider.example", "secret", false, "bearer"));

        assertEquals("vault", result.scheme());
        assertEquals("http://provider.example", result.endpoint());
        assertEquals(java.util.List.of("ES256"), result.supportedAlgorithms());
        assertEquals(java.util.List.of(), result.supportedOperations());
        assertEquals(true, result.canDelete());
        verifyNoInteractions(repository);
        server.verify();
    }

    @Test
    void storesCapabilitiesWithTheProviderConfiguration() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var capabilities = "{\"scheme\":\"next-gen\",\"supportedAlgorithms\":[\"BBS\"],"
                + "\"digestSigningAlgorithms\":[],"
                + "\"supportedOperations\":[\"w3c.bbs-data-integrity-credential-issuance\","
                + "\"w3c.bbs-data-integrity-presentation-setup\"],"
                + "\"keylessOperations\":[\"w3c.bbs-data-integrity-presentation-setup\"],"
                + "\"canCreate\":true,\"canImport\":false,\"canDelete\":true}";
        server.expect(request ->
                assertEquals("/v1/capabilities", request.getURI().getPath()))
                .andRespond(MockRestResponseCreators.withSuccess(capabilities, MediaType.APPLICATION_JSON));
        when(repository.findAllByTenantIdIsNullOrderByName()).thenReturn(List.of());
        when(repository.save(any(SigningProviderEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var createService = new SigningProviderService(
                repository,
                new ObjectMapper(),
                builder,
                "01".repeat(32),
                "http://signing.example",
                "none",
                "",
                noBackendKeys());
        createService.createGlobal(new SigningProviderRequest(
                "BBS provider", "http://provider.example", null, true, "none"));

        var captor = org.mockito.ArgumentCaptor.forClass(SigningProviderEntity.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        var configuration = createService.configuration(captor.getValue());
        assertEquals("next-gen", configuration.scheme());
        assertEquals(List.of("BBS"), configuration.supportedAlgorithms());
        assertEquals(
                List.of(
                        "w3c.bbs-data-integrity-credential-issuance",
                        "w3c.bbs-data-integrity-presentation-setup"),
                configuration.supportedOperations());
        assertEquals(
                List.of("w3c.bbs-data-integrity-presentation-setup"),
                configuration.keylessOperations());
        assertEquals(true, configuration.canCreate());
        assertEquals(true, configuration.canDelete());
        server.verify();
    }

    @Test
    void restoresManagedGlobalProviderAsDefaultAfterRestart() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertEquals("/v1/capabilities", request.getURI().getPath()))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"scheme\":\"software\",\"supportedAlgorithms\":[\"ES256\"]}",
                        MediaType.APPLICATION_JSON));
        when(repository.findAllByTenantIdIsNullOrderByName()).thenReturn(List.of());
        when(repository.save(any(SigningProviderEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var configured = new SigningProviderService(
                repository,
                new ObjectMapper(),
                builder,
                "01".repeat(32),
                "http://provider.example",
                "none",
                "",
                noBackendKeys());
        configured.createGlobal(new SigningProviderRequest(
                "Heidi Software Signing Service", "http://provider.example", "", false, "none"));

        var captor = org.mockito.ArgumentCaptor.forClass(SigningProviderEntity.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        var existing = captor.getValue();
        assertNull(existing.getTenantId());
        assertFalse(existing.isDefaultProvider());

        when(repository.findAllByTenantIdIsNullOrderByName()).thenReturn(List.of(existing));
        configured.ensureGlobalProvider();

        assertTrue(existing.isDefaultProvider());
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(2))
                .save(existing);
        server.verify();
    }

    @Test
    void refreshesAndPersistsCurrentCapabilities() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var initialCapabilities = "{\"scheme\":\"next-gen\",\"supportedAlgorithms\":[\"ES256\"],"
                + "\"digestSigningAlgorithms\":[\"ES256\"],\"supportedOperations\":[],"
                + "\"canCreate\":true,\"canImport\":false,\"canDelete\":true}";
        server.expect(request -> assertEquals("/v1/capabilities", request.getURI().getPath()))
                .andRespond(MockRestResponseCreators.withSuccess(initialCapabilities, MediaType.APPLICATION_JSON));
        when(repository.findAllByTenantIdIsNullOrderByName()).thenReturn(List.of());
        when(repository.save(any(SigningProviderEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var refreshService = new SigningProviderService(
                repository,
                new ObjectMapper(),
                builder,
                "01".repeat(32),
                "http://signing.example",
                "none",
                "",
                noBackendKeys());
        refreshService.createGlobal(new SigningProviderRequest(
                "Provider", "http://provider.example", null, true, "none"));

        var captor = org.mockito.ArgumentCaptor.forClass(SigningProviderEntity.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        var entity = captor.getValue();
        when(repository.findById(7)).thenReturn(Optional.of(entity));
        server.reset();
        var refreshedCapabilities = "{\"scheme\":\"next-gen\",\"supportedAlgorithms\":[\"BBS\"],"
                + "\"digestSigningAlgorithms\":[],"
                + "\"supportedOperations\":[\"w3c.bbs-data-integrity-credential-issuance\"],"
                + "\"canCreate\":true,\"canImport\":false,\"canDelete\":true}";
        server.expect(request -> assertEquals("/v1/capabilities", request.getURI().getPath()))
                .andRespond(MockRestResponseCreators.withSuccess(refreshedCapabilities, MediaType.APPLICATION_JSON));

        var result = refreshService.refresh(TENANT_ID, 7);

        assertEquals(List.of("BBS"), result.supportedAlgorithms());
        assertEquals(
                List.of("w3c.bbs-data-integrity-credential-issuance"),
                refreshService.configuration(entity).supportedOperations());
        assertEquals(true, result.canCreate());
        assertEquals(true, result.canDelete());
        server.verify();
    }

    @Test
    void detectsAStaleKeyOverTheWire() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertEquals("/v1/capabilities", request.getURI().getPath()))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"scheme\":\"local\",\"supportedAlgorithms\":[\"ES256\"],\"clientAcceptance\":\"allow-list\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(request -> assertEquals("/v1/auth/clients", request.getURI().getPath()))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "[{\"name\":\"issuer\",\"registered\":true,\"publicKey\":\"old-key\"}]",
                        MediaType.APPLICATION_JSON));
        var checked = new SigningProviderService(repository, new ObjectMapper(), builder,
                "01".repeat(32), "http://signing.example", "none", "",
                new SigningClientStatusService(scheme -> java.util.Map.of("issuer", "current-key")));

        var result = checked.checkConnection(new SigningProviderRequest(
                "Provider", "http://provider.example", null, true, "none"));

        var issuer = result.clients().getFirst();
        assertEquals("issuer", issuer.name());
        assertEquals(false, issuer.known());
        assertEquals(false, issuer.registered());
        assertEquals("current-key", issuer.publicKey());
        server.verify();
    }

    @Test
    void registersBackendWhenProviderAllowsIt() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var capabilities = "{\"scheme\":\"local\",\"supportedAlgorithms\":[\"ES256\"],"
                + "\"clientAcceptance\":\"platform-assisted\"}";
        server.expect(request -> assertEquals("/v1/capabilities", request.getURI().getPath()))
                .andRespond(MockRestResponseCreators.withSuccess(capabilities, MediaType.APPLICATION_JSON));
        server.expect(request -> assertEquals("/v1/auth/clients", request.getURI().getPath()))
                .andRespond(MockRestResponseCreators.withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(request -> {
            assertEquals("POST", request.getMethod().name());
            assertEquals("/v1/auth/clients", request.getURI().getPath());
        }).andRespond(MockRestResponseCreators.withNoContent());
        server.expect(request -> assertEquals("/v1/capabilities", request.getURI().getPath()))
                .andRespond(MockRestResponseCreators.withSuccess(capabilities, MediaType.APPLICATION_JSON));
        server.expect(request -> assertEquals("/v1/auth/clients", request.getURI().getPath()))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "[{\"name\":\"issuer\",\"registered\":false,\"publicKey\":\"current-key\"}]",
                        MediaType.APPLICATION_JSON));
        var checked = new SigningProviderService(repository, new ObjectMapper(), builder,
                "01".repeat(32), "http://signing.example", "none", "",
                new SigningClientStatusService(scheme -> java.util.Map.of("issuer", "current-key")));
        var request = new SigningProviderRequest(
                "Provider", "http://provider.example", null, true, "none");

        var result = checked.registerClient(request, "issuer");

        assertEquals(true, result.clients().getFirst().known());
        server.verify();
    }

    @Test
    void requiresTheAcceptedBackendKey() {
        var status = new SigningClientStatusService(scheme -> java.util.Map.of("issuer", "current-key"));
        var checked = org.mockito.Mockito.spy(new SigningProviderService(repository, new ObjectMapper(),
                restClientBuilder, "01".repeat(32), "http://signing.example", "none", "", status));
        var config = org.mockito.Mockito.mock(org.heidiverse.heidi.entity.model.signing.SigningProviderConfiguration.class);
        when(config.authenticationMode()).thenReturn("registered");
        when(config.scheme()).thenReturn("local");
        when(config.endpoint()).thenReturn("http://signing.example");
        when(repository.findById(GLOBAL_ID)).thenReturn(Optional.of(global));
        org.mockito.Mockito.doReturn(config).when(checked).configuration(global);
        var remote = org.mockito.Mockito.mock(org.heidiverse.heidi.signing.adapters.RemoteSigningKeyProvider.class);
        org.mockito.Mockito.doReturn(remote).when(checked).provider(TENANT_ID, GLOBAL_ID);
        when(remote.clientAcceptance()).thenReturn("allow-list");
        when(remote.clients()).thenReturn(List.of(new org.heidiverse.heidi.signing.adapters.RemoteSigningKeyProvider.ClientStatus(
                "issuer", true, null, "old-key")));
        var consumer = org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotConsumer.ISSUER;

        var error = assertThrows(IllegalArgumentException.class,
                () -> checked.requireClient(TENANT_ID, GLOBAL_ID, consumer));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("issuer"));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("http://signing.example"));

        when(remote.clients()).thenReturn(List.of(new org.heidiverse.heidi.signing.adapters.RemoteSigningKeyProvider.ClientStatus(
                "issuer", false, null, "current-key")));
        checked.requireClient(TENANT_ID, GLOBAL_ID, consumer);
    }

    @Test
    void reconcilesMissingBackendClientsForPlatformAssistedProviders() {
        var entity = org.mockito.Mockito.mock(SigningProviderEntity.class);
        when(entity.getId()).thenReturn(GLOBAL_ID);
        when(entity.getTenantId()).thenReturn(null);
        var config = org.mockito.Mockito.mock(
                org.heidiverse.heidi.entity.model.signing.SigningProviderConfiguration.class);
        when(config.authenticationMode()).thenReturn("registered");
        when(config.scheme()).thenReturn("local");
        var remote = org.mockito.Mockito.mock(
                org.heidiverse.heidi.signing.adapters.RemoteSigningKeyProvider.class);
        when(remote.clientAcceptance()).thenReturn("platform-assisted");
        when(remote.clients()).thenReturn(List.of(
                new org.heidiverse.heidi.signing.adapters.RemoteSigningKeyProvider.ClientStatus(
                        "verifier", true, null, "verifier-key")));

        var checked = org.mockito.Mockito.spy(new SigningProviderService(
                repository,
                new ObjectMapper(),
                restClientBuilder,
                "01".repeat(32),
                "http://signing.example",
                "registered",
                "",
                new SigningClientStatusService(scheme -> java.util.Map.of(
                        "issuer", "issuer-key", "verifier", "verifier-key"))));
        doReturn(config).when(checked).configuration(entity);
        doReturn(remote).when(checked).provider(null, GLOBAL_ID);

        checked.reconcileClients(entity);

        verify(remote).addClient("issuer", "issuer-key");
        org.mockito.Mockito.verify(remote, org.mockito.Mockito.never())
                .addClient("verifier", "verifier-key");
    }

    @Test
    void retriesAreBestEffortWhenSigningServiceIsUnavailable() {
        var entity = org.mockito.Mockito.mock(SigningProviderEntity.class);
        when(entity.getId()).thenReturn(GLOBAL_ID);
        when(entity.getTenantId()).thenReturn(null);
        var config = org.mockito.Mockito.mock(
                org.heidiverse.heidi.entity.model.signing.SigningProviderConfiguration.class);
        when(config.authenticationMode()).thenReturn("registered");
        when(config.scheme()).thenReturn("local");
        var remote = org.mockito.Mockito.mock(
                org.heidiverse.heidi.signing.adapters.RemoteSigningKeyProvider.class);
        when(remote.clientAcceptance()).thenReturn("platform-assisted");
        when(remote.clients()).thenReturn(List.of(
                new org.heidiverse.heidi.signing.adapters.RemoteSigningKeyProvider.ClientStatus(
                        "verifier", true, null, "verifier-key")));
        org.mockito.Mockito.doThrow(new IllegalStateException("temporarily unavailable"))
                .when(remote).addClient("issuer", "issuer-key");

        var checked = org.mockito.Mockito.spy(new SigningProviderService(
                repository,
                new ObjectMapper(),
                restClientBuilder,
                "01".repeat(32),
                "http://signing.example",
                "registered",
                "",
                new SigningClientStatusService(scheme -> java.util.Map.of(
                        "issuer", "issuer-key"))));
        doReturn(config).when(checked).configuration(entity);
        doReturn(remote).when(checked).provider(null, GLOBAL_ID);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> checked.reconcileClients(entity));
    }

    private static SigningProviderEntity provider(String tenantId, boolean isDefault) {
        var entity = new SigningProviderEntity();
        entity.setTenantId(tenantId);
        entity.setDefaultProvider(isDefault);
        return entity;
    }
}
