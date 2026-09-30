// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeDetail;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeDetailResponse;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeState;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeAttribute;
import org.heidiverse.heidi.entity.model.credentialscheme.AttributeType;
import org.heidiverse.heidi.entity.model.exceptions.SchemaNotFoundException;
import org.heidiverse.heidi.entity.model.proofscheme.ProofSchemeDetail;
import org.heidiverse.heidi.entity.model.proofscheme.ProofSchemeOverview;
import org.heidiverse.heidi.entity.model.proofscheme.ProofSchemePayload;
import org.heidiverse.heidi.entity.model.issuer.IssuerDefinitionResponse;
import org.heidiverse.heidi.entity.model.issuer.IssuerOverview;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.service.CredentialSchemeService;
import org.heidiverse.heidi.entity.service.IssuerService;
import org.heidiverse.heidi.entity.service.ProofSchemeService;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileId;
import org.heidiverse.heidi.shared.localized.LocalizedValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.mock.env.MockEnvironment;

import tools.jackson.databind.json.JsonMapper;

class PlatformApiSchemaSeedConfigTest {
    private static final UUID SEED_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID INSERTED_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID SECOND_INSERTED_ID =
            UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Test
    void staysOffByDefault() {
        new ApplicationContextRunner()
                .withUserConfiguration(PlatformApiSchemaSeedConfig.class)
                .run(context -> assertThat(context).doesNotHaveBean("schemaSeedRunner"));
    }

    @Test
    void localProfileSeedsLocalFixtureByDefault() throws Exception {
        var properties = new ResourcePropertySource(
                new ClassPathResource("application-local.properties"));

        assertThat(properties.getProperty("heidi.schema-seed.enabled"))
                .isEqualTo("${HEIDI_SCHEMA_SEED_ENABLED:true}");
        assertThat(properties.getProperty("heidi.schema-seed.manifest"))
                .isEqualTo("${HEIDI_SCHEMA_SEED_MANIFEST:tools/local-dev/schema-seed.json}");
        assertThat(properties.getProperty("heidi.platform.local.trust-systems"))
                .isEqualTo("${HEIDI_PLATFORM_LOCAL_TRUST_SYSTEMS:Custom,EUDI}");
    }

    @Test
    void resolvesRepositoryManifestFromApiModuleDirectory() {
        var environment = new MockEnvironment()
                .withProperty("heidi.schema-seed.manifest", "tools/conformance/oidf-schema-seed.json");

        assertThatCode(() -> PlatformApiSchemaSeedConfig.SeedConfig.from(
                        environment, JsonMapper.builder().build()))
                .doesNotThrowAnyException();
    }

    @Test
    void readsAllManifestEntries(@TempDir Path directory) throws Exception {
        var credential = directory.resolve("credential.json");
        var proof = directory.resolve("proof.json");
        var manifest = directory.resolve("seed.json");
        Files.writeString(credential, "{}");
        Files.writeString(proof, "{}");
        Files.writeString(manifest, """
                {
                  "entries": [
                    {
                      "tenantId": "tenant",
                      "issuerSlug": "issuer",
                      "credentialSchemas": ["credential.json"],
                      "proofSchemas": ["proof.json"]
                    },
                    {
                      "tenantId": "other-tenant",
                      "issuerSlug": "other-issuer",
                      "credentialSchemas": [],
                      "proofSchemas": []
                    }
                  ]
                }
                """);

        var config = PlatformApiSchemaSeedConfig.SeedConfig.from(
                new MockEnvironment().withProperty(
                        "heidi.schema-seed.manifest", manifest.toString()),
                JsonMapper.builder().build());

        assertThat(config.entries()).hasSize(2);
        assertThat(config.entries().getFirst().tenantId()).isEqualTo("tenant");
        assertThat(config.entries().getFirst().issuerSlug()).isEqualTo("issuer");
        assertThat(config.entries().getFirst().credentialSchemas())
                .containsExactly(credential.toString());
        assertThat(config.entries().getFirst().proofSchemas()).containsExactly(proof.toString());
        assertThat(config.entries().get(1).tenantId()).isEqualTo("other-tenant");
        assertThat(config.entries().get(1).issuerSlug()).isEqualTo("other-issuer");
    }

    @Test
    void seedsCredentialsBeforeProofsAndMapsSeedIds(@TempDir Path directory) throws Exception {
        var credentialFile = directory.resolve("credential.json");
        var proofFile = directory.resolve("proof.json");
        Files.writeString(credentialFile, """
                {
                  "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "credentialIdentifier": "org.example.credential",
                  "version": "1",
                  "attributes": [],
                  "credentialSchemeStylePayloads": [],
                  "metadata": {"metaAttributes": []},
                  "issuerSettings": {"id": 7, "issuerKeyType": "SOFTWARE"}
                }
                """);
        Files.writeString(proofFile, """
                {
                  "title": "Example proof",
                  "purpose": "Conformance",
                  "credentialSchemes": [{
                    "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "attributes": []
                  }]
                }
                """);

        var credentials = mock(CredentialSchemeService.class);
        var proofs = mock(ProofSchemeService.class);
        var issuers = issuerService(42);
        when(credentials.findByCredentialIdentifierAndVersion(
                        "org.example.credential", "1", false))
                .thenThrow(new SchemaNotFoundException("org.example.credential"));
        when(credentials.create(any(CredentialSchemeDetail.class), eq("acme")))
                .thenReturn(INSERTED_ID);
        when(proofs.findAll(false, List.of()))
                .thenReturn(new ProofSchemeOverview(List.of()));

        var runner = new PlatformApiSchemaSeedConfig.SchemaSeedRunner(
                JsonMapper.builder().build(), credentials, proofs, issuers,
                config("acme", "acme", List.of(credentialFile.toString()), List.of(proofFile.toString())));

        runner.run(null);

        var payload = org.mockito.ArgumentCaptor.forClass(ProofSchemePayload.class);
        var credential = org.mockito.ArgumentCaptor.forClass(CredentialSchemeDetail.class);
        verify(credentials).create(credential.capture(), eq("acme"));
        assertThat(credential.getValue().issuerSettings().id()).isEqualTo(42);
        assertThat(credential.getValue().issuerSettings().issuanceProfileId())
                .isEqualTo(EcosystemProfileId.EUDI_ISSUANCE_2026_1);
        verify(credentials).publishForTenant(INSERTED_ID, "1", "acme");
        verify(proofs).insertProofScheme(payload.capture(), eq("acme"));
        assertThat(payload.getValue().verifierIdentityId()).isEqualTo(42);
        assertThat(payload.getValue().presentationProfileId())
                .isEqualTo(EcosystemProfileId.EUDI_PRESENTATION_2026_1);
        assertThat(payload.getValue().verifierTrustSystem()).isEqualTo(IssuerTrustSystem.EUDI);
        assertThat(payload.getValue().credentialSchemes().getFirst().id()).isEqualTo(INSERTED_ID);
    }

    @Test
    void skipsExistingCredentialsAndProofs(@TempDir Path directory) throws Exception {
        var credentialFile = directory.resolve("credential.json");
        var proofFile = directory.resolve("proof.json");
        Files.writeString(credentialFile, """
                {
                  "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "credentialIdentifier": "org.example.credential",
                  "version": "1",
                  "attributes": [],
                  "credentialSchemeStylePayloads": [],
                  "metadata": {"metaAttributes": []},
                  "issuerSettings": {"id": 7, "issuerKeyType": "SOFTWARE"}
                }
                """);
        Files.writeString(proofFile, """
                {"title":"Example proof","purpose":"Conformance","credentialSchemes":[]}
                """);

        var credentials = mock(CredentialSchemeService.class);
        var proofs = mock(ProofSchemeService.class);
        var issuers = issuerService(42);
        when(credentials.findByCredentialIdentifierAndVersion(
                        "org.example.credential", "1", false))
                .thenReturn(mockCredentialResponse());
        var existingProof = mock(ProofSchemeDetail.class);
        when(existingProof.title()).thenReturn("Example proof");
        when(existingProof.tenantId()).thenReturn("acme");
        when(proofs.findAll(false, List.of()))
                .thenReturn(new ProofSchemeOverview(List.of(existingProof)));

        var runner = new PlatformApiSchemaSeedConfig.SchemaSeedRunner(
                JsonMapper.builder().build(), credentials, proofs, issuers,
                config("acme", "acme", List.of(credentialFile.toString()), List.of(proofFile.toString())));

        runner.run(null);

        verify(credentials, never()).create(any(), any());
        verify(credentials).publishForTenant(INSERTED_ID, "1", "acme");
        verify(proofs, never()).insertProofScheme(any(), any());
    }

    @Test
    void rejectsAnExistingCredentialOwnedByAnotherTenant(@TempDir Path directory) throws Exception {
        var credentialFile = directory.resolve("credential.json");
        Files.writeString(credentialFile, """
                {
                  "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "credentialIdentifier": "org.example.credential",
                  "version": "1",
                  "attributes": [],
                  "credentialSchemeStylePayloads": [],
                  "metadata": {"metaAttributes": []},
                  "issuerSettings": {"id": 7, "issuerKeyType": "SOFTWARE"}
                }
                """);

        var credentials = mock(CredentialSchemeService.class);
        when(credentials.findByCredentialIdentifierAndVersion(
                        "org.example.credential", "1", false))
                .thenReturn(mockCredentialResponse(CredentialSchemeState.PUBLISHED, "acme"));
        var proofs = mock(ProofSchemeService.class);
        var issuers = mock(IssuerService.class);
        when(issuers.findAllIssuers())
                .thenReturn(new IssuerOverview(List.of(
                        new IssuerDefinitionResponse(
                                42, "issuer", null, null, "tenant-b", null, Set.of()))));
        var runner = new PlatformApiSchemaSeedConfig.SchemaSeedRunner(
                JsonMapper.builder().build(), credentials, proofs, issuers,
                config("tenant-b", "issuer", List.of(credentialFile.toString()), List.of()));

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Schema seed credential org.example.credential belongs to tenant acme, not tenant-b");
        verify(credentials, never()).publishForTenant(any(), any(), any());
    }

    @Test
    void seedsEachManifestEntryIndependently(@TempDir Path directory) throws Exception {
        var credentialFile = directory.resolve("credential-a.json");
        var secondCredentialFile = directory.resolve("credential-b.json");
        var proofFile = directory.resolve("proof-a.json");
        var secondProofFile = directory.resolve("proof-b.json");
        Files.writeString(credentialFile, """
                {
                  "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "credentialIdentifier": "org.example.credential.a",
                  "version": "1",
                  "attributes": [],
                  "credentialSchemeStylePayloads": [],
                  "metadata": {"metaAttributes": []},
                  "issuerSettings": {"id": 7, "issuerKeyType": "SOFTWARE"}
                }
                """);
        Files.writeString(secondCredentialFile, """
                {
                  "id": "cccccccc-cccc-cccc-cccc-cccccccccccc",
                  "credentialIdentifier": "org.example.credential.b",
                  "version": "1",
                  "attributes": [],
                  "credentialSchemeStylePayloads": [],
                  "metadata": {"metaAttributes": []},
                  "issuerSettings": {"id": 7, "issuerKeyType": "SOFTWARE"}
                }
                """);
        Files.writeString(proofFile, """
                {
                  "title": "Example proof a",
                  "purpose": "Conformance",
                  "credentialSchemes": [{
                    "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "attributes": []
                  }]
                }
                """);
        Files.writeString(secondProofFile, """
                {
                  "title": "Example proof b",
                  "purpose": "Conformance",
                  "credentialSchemes": [{
                    "id": "cccccccc-cccc-cccc-cccc-cccccccccccc",
                    "attributes": []
                  }]
                }
                """);

        var credentials = mock(CredentialSchemeService.class);
        when(credentials.findByCredentialIdentifierAndVersion(
                        any(), any(), eq(false)))
                .thenThrow(new SchemaNotFoundException("missing"));
        when(credentials.create(any(CredentialSchemeDetail.class), eq("tenant-a")))
                .thenReturn(INSERTED_ID);
        when(credentials.create(any(CredentialSchemeDetail.class), eq("tenant-b")))
                .thenReturn(SECOND_INSERTED_ID);
        var proofs = mock(ProofSchemeService.class);
        when(proofs.findAll(false, List.of())).thenReturn(new ProofSchemeOverview(List.of()));
        var issuers = mock(IssuerService.class);
        when(issuers.findAllIssuers())
                .thenReturn(new IssuerOverview(List.of(
                        new IssuerDefinitionResponse(
                                42, "issuer-a", null, null, "tenant-a", null, Set.of()),
                        new IssuerDefinitionResponse(
                                43, "issuer-b", null, null, "tenant-b", null, Set.of()))));

        var entry = new PlatformApiSchemaSeedConfig.SeedEntry(
                "tenant-a",
                "issuer-a",
                List.of(credentialFile.toString()),
                List.of(proofFile.toString()));
        var secondEntry = new PlatformApiSchemaSeedConfig.SeedEntry(
                "tenant-b",
                "issuer-b",
                List.of(secondCredentialFile.toString()),
                List.of(secondProofFile.toString()));
        var runner = new PlatformApiSchemaSeedConfig.SchemaSeedRunner(
                JsonMapper.builder().build(), credentials, proofs, issuers,
                new PlatformApiSchemaSeedConfig.SeedConfig(List.of(entry, secondEntry)));

        runner.run(null);

        var credential = org.mockito.ArgumentCaptor.forClass(CredentialSchemeDetail.class);
        verify(credentials, times(2)).create(credential.capture(), any());
        assertThat(credential.getAllValues())
                .extracting(detail -> detail.issuerSettings().id())
                .containsExactly(42, 43);
        assertThat(credential.getAllValues())
                .extracting(CredentialSchemeDetail::credentialIdentifier)
                .containsExactly("org.example.credential.a", "org.example.credential.b");
        verify(credentials).publishForTenant(INSERTED_ID, "1", "tenant-a");
        verify(credentials).publishForTenant(SECOND_INSERTED_ID, "1", "tenant-b");

        var payload = org.mockito.ArgumentCaptor.forClass(ProofSchemePayload.class);
        verify(proofs, times(2)).insertProofScheme(payload.capture(), any());
        assertThat(payload.getAllValues())
                .extracting(ProofSchemePayload::title)
                .containsExactly("Example proof a", "Example proof b");
        assertThat(payload.getAllValues().get(0).credentialSchemes().getFirst().id())
                .isEqualTo(INSERTED_ID);
        assertThat(payload.getAllValues().get(1).credentialSchemes().getFirst().id())
                .isEqualTo(SECOND_INSERTED_ID);
        verify(proofs).insertProofScheme(any(), eq("tenant-a"));
        verify(proofs).insertProofScheme(any(), eq("tenant-b"));
        verify(proofs, times(2)).findAll(false, List.of());
    }

    @Test
    void mapsProofAttributeIdsToPersistedIds(@TempDir Path directory) throws Exception {
        var credentialFile = directory.resolve("credential.json");
        var secondCredentialFile = directory.resolve("credential-eudi.json");
        var proofFile = directory.resolve("proof.json");
        var secondProofFile = directory.resolve("proof-eudi.json");
        var credential = """
                {
                  "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "credentialIdentifier": "org.example.credential",
                  "version": "1",
                  "attributes": [
                    {"id": 1, "name": "given_name", "type": "STRING"},
                    {"id": 2, "name": "family_name", "type": "STRING"}
                  ],
                  "credentialSchemeStylePayloads": [],
                  "metadata": {"metaAttributes": []},
                  "issuerSettings": {"id": 7, "issuerKeyType": "SOFTWARE"}
                }
                """;
        var secondCredential = credential.replace(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "cccccccc-cccc-cccc-cccc-cccccccccccc")
                .replace("org.example.credential", "org.example.credential.eudi");
        Files.writeString(credentialFile, credential);
        Files.writeString(secondCredentialFile, secondCredential);
        Files.writeString(proofFile, """
                {
                  "title": "Example proof",
                  "purpose": "Local",
                  "credentialSchemes": [{
                    "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "attributes": [1, 2]
                  }]
                }
                """);
        Files.writeString(secondProofFile, """
                {
                  "title": "Example EUDI proof",
                  "purpose": "Local",
                  "credentialSchemes": [{
                    "id": "cccccccc-cccc-cccc-cccc-cccccccccccc",
                    "attributes": [1, 2]
                  }]
                }
                """);

        var credentials = mock(CredentialSchemeService.class);
        when(credentials.findByCredentialIdentifierAndVersion(any(), any(), eq(false)))
                .thenThrow(new SchemaNotFoundException("missing"));
        when(credentials.create(any(), eq("acme")))
                .thenReturn(INSERTED_ID, SECOND_INSERTED_ID);
        when(credentials.findById(INSERTED_ID, false)).thenReturn(
                persistedCredential(INSERTED_ID, 11, 12));
        when(credentials.findById(SECOND_INSERTED_ID, false)).thenReturn(
                persistedCredential(SECOND_INSERTED_ID, 21, 22));
        var proofs = mock(ProofSchemeService.class);
        when(proofs.findAll(false, List.of())).thenReturn(new ProofSchemeOverview(List.of()));

        var runner = new PlatformApiSchemaSeedConfig.SchemaSeedRunner(
                JsonMapper.builder().build(), credentials, proofs, issuerService(42),
                config("acme", "acme",
                        List.of(credentialFile.toString(), secondCredentialFile.toString()),
                        List.of(proofFile.toString(), secondProofFile.toString())));

        runner.run(null);

        var payload = org.mockito.ArgumentCaptor.forClass(ProofSchemePayload.class);
        verify(proofs, times(2)).insertProofScheme(payload.capture(), eq("acme"));
        assertThat(payload.getAllValues())
                .extracting(proof -> proof.credentialSchemes().getFirst().attributes())
                .containsExactly(List.of(11, 12), List.of(21, 22));
    }

    @Test
    void reportsUnknownCredentialReference(@TempDir Path directory) throws Exception {
        var credentialFile = directory.resolve("credential.json");
        var proofFile = directory.resolve("proof.json");
        Files.writeString(credentialFile, """
                {
                  "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "credentialIdentifier": "org.example.credential",
                  "version": "1",
                  "attributes": [],
                  "credentialSchemeStylePayloads": [],
                  "metadata": {"metaAttributes": []},
                  "issuerSettings": {"id": 7, "issuerKeyType": "SOFTWARE"}
                }
                """);
        Files.writeString(proofFile, """
                {"title":"Example proof","purpose":"Conformance","credentialSchemes":[
                  {"id":"cccccccc-cccc-cccc-cccc-cccccccccccc","attributes":[]}
                ]}
                """);

        var credentials = mock(CredentialSchemeService.class);
        var issuers = issuerService(42);
        when(credentials.findByCredentialIdentifierAndVersion(any(), any(), eq(false)))
                .thenThrow(new SchemaNotFoundException("org.example.credential"));
        when(credentials.create(any(), eq("acme"))).thenReturn(INSERTED_ID);
        var proofs = mock(ProofSchemeService.class);
        when(proofs.findAll(false, List.of())).thenReturn(new ProofSchemeOverview(List.of()));

        var runner = new PlatformApiSchemaSeedConfig.SchemaSeedRunner(
                JsonMapper.builder().build(), credentials, proofs, issuers,
                config("acme", "acme", List.of(credentialFile.toString()), List.of(proofFile.toString())));

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cccccccc-cccc-cccc-cccc-cccccccccccc");
    }

    @Test
    void rejectsSchemaArrays(@TempDir Path directory) {
        var credentialFile = directory.resolve("credential.json");
        try {
            Files.writeString(credentialFile, "[]");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }

        var credentials = mock(CredentialSchemeService.class);
        var proofs = mock(ProofSchemeService.class);
        var issuers = issuerService(42);
        var runner = new PlatformApiSchemaSeedConfig.SchemaSeedRunner(
                JsonMapper.builder().build(), credentials, proofs, issuers,
                config("acme", "acme", List.of(credentialFile.toString()), List.of()));

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Schema seed file " + credentialFile
                        + " must contain exactly one JSON object");
    }

    private static CredentialSchemeDetailResponse mockCredentialResponse() {
        return mockCredentialResponse(CredentialSchemeState.CREATED, "acme");
    }

    private static CredentialSchemeDetailResponse mockCredentialResponse(
            CredentialSchemeState state, String tenantId) {
        return new CredentialSchemeDetailResponse(
                INSERTED_ID, "org.example.credential", "1", "Example", state,
                List.of(), List.of(), null, null, tenantId, 1, null);
    }

    private static CredentialSchemeDetailResponse persistedCredential(UUID id, int first, int second) {
        return new CredentialSchemeDetailResponse(
                id, "credential", "1", "Credential", CredentialSchemeState.PUBLISHED,
                List.of(attribute(first, "given_name"), attribute(second, "family_name")),
                List.of(), null, null, "acme", 1, null);
    }

    private static CredentialSchemeAttribute attribute(int id, String name) {
        return new CredentialSchemeAttribute(
                id, name, AttributeType.STRING, false, false, true,
                LocalizedValue.en(name), java.util.Map.of());
    }

    private static IssuerService issuerService(int id) {
        var service = mock(IssuerService.class);
        when(service.findAllIssuers())
                .thenReturn(new IssuerOverview(List.of(
                        new IssuerDefinitionResponse(id, "acme", null, null))));
        return service;
    }

    private static PlatformApiSchemaSeedConfig.SeedConfig config(
            String tenantId, String issuerSlug, List<String> credentials, List<String> proofs) {
        return new PlatformApiSchemaSeedConfig.SeedConfig(List.of(
                new PlatformApiSchemaSeedConfig.SeedEntry(tenantId, issuerSlug, credentials, proofs)));
    }
}
