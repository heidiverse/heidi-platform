// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.heidiverse.heidi.entity.model.credentialscheme.CredentialScheme;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeDetail;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeDetailResponse;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeState;
import org.heidiverse.heidi.entity.model.exceptions.SchemaNotFoundException;
import org.heidiverse.heidi.entity.model.issuer.IssuerSettings;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.proofscheme.ProofSchemePayload;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileCatalog;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileId;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileRole;
import org.heidiverse.heidi.entity.service.CredentialSchemeService;
import org.heidiverse.heidi.entity.service.IssuerService;
import org.heidiverse.heidi.entity.service.ProofSchemeService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Optionally loads repeatable credential and proof schema fixtures at startup. */
@Configuration
@ConditionalOnProperty(name = "heidi.schema-seed.enabled", havingValue = "true")
public class PlatformApiSchemaSeedConfig {
    private static final String MANIFEST = "heidi.schema-seed.manifest";
    private static final String DEFAULT_ISSUANCE_PROFILE = EcosystemProfileId.EUDI_ISSUANCE_2026_1;
    private static final String DEFAULT_PRESENTATION_PROFILE = EcosystemProfileId.EUDI_PRESENTATION_2026_1;

    @Bean
    @Order(2)
    ApplicationRunner schemaSeedRunner(
            ObjectMapper objectMapper,
            CredentialSchemeService credentialSchemeService,
            ProofSchemeService proofSchemeService,
            IssuerService issuerService,
            Environment environment) {
        return new SchemaSeedRunner(
                objectMapper,
                credentialSchemeService,
                proofSchemeService,
                issuerService,
                SeedConfig.from(environment, objectMapper));
    }

    public record SeedConfig(List<SeedEntry> entries) {
        public SeedConfig {
            if (entries == null || entries.isEmpty()) {
                throw new IllegalArgumentException("Schema seed manifest requires an entry");
            }
            entries = List.copyOf(entries);
        }

        static SeedConfig from(Environment environment, ObjectMapper objectMapper) {
            var manifestName = environment.getProperty(MANIFEST, "").trim();
            if (manifestName.isEmpty()) {
                throw new IllegalArgumentException(
                        "Schema seed manifest is required when schema seeding is enabled");
            }
            return fromManifest(Path.of(manifestName), objectMapper);
        }

        private static SeedConfig fromManifest(Path manifest, ObjectMapper objectMapper) {
            try {
                var manifestPath = locate(manifest);
                var root = objectMapper.readTree(Files.readString(manifestPath));
                var directory = manifestPath.getParent();
                var entries = root.get("entries");
                if (entries == null || !entries.isArray() || entries.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Schema seed manifest " + manifestPath
                                    + " must contain a non-empty entries array");
                }

                var configs = new ArrayList<SeedEntry>();
                for (var entry : entries) {
                    if (!entry.isObject()) {
                        throw new IllegalArgumentException(
                                "Schema seed manifest " + manifestPath
                                        + " contains a non-object entry");
                    }
                    configs.add(new SeedEntry(
                            requiredText(entry, "tenantId", manifestPath),
                            requiredText(entry, "issuerSlug", manifestPath),
                            paths(entry, directory, "credentialSchemas", manifestPath),
                            paths(entry, directory, "proofSchemas", manifestPath)));
                }
                return new SeedConfig(configs);
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "Could not read schema seed manifest " + manifest, exception);
            }
        }

        private static Path locate(Path manifest) {
            if (manifest.isAbsolute()) return manifest.normalize();

            for (var directory = Path.of("").toAbsolutePath().normalize();
                    directory != null;
                    directory = directory.getParent()) {
                var candidate = directory.resolve(manifest).normalize();
                if (Files.isRegularFile(candidate)) return candidate;
            }
            return manifest.toAbsolutePath().normalize();
        }

        private static List<String> paths(
                JsonNode root, Path directory, String name, Path manifest) {
            var node = root.get(name);
            if (node == null || !node.isArray()) {
                throw new IllegalArgumentException(
                        "Schema seed manifest " + manifest
                                + " must contain an array named " + name);
            }

            var values = new ArrayList<String>();
            for (var item : node) {
                if (!item.isString() || item.asString().isBlank()) {
                    throw new IllegalArgumentException(
                            "Schema seed manifest " + manifest
                                    + " contains a non-string path in " + name);
                }
                values.add(resolve(directory, item.asString()));
            }
            return values;
        }

        private static String resolve(Path directory, String value) {
            var path = Path.of(value);
            return path.isAbsolute()
                    ? path.toString()
                    : directory.resolve(path).normalize().toString();
        }

        private static String requiredText(JsonNode root, String name, Path manifest) {
            var node = root.get(name);
            if (node == null || !node.isString() || node.asString().isBlank()) {
                throw new IllegalArgumentException(
                        "Schema seed manifest " + manifest + " requires a non-empty " + name);
            }
            return node.asString();
        }
    }

    public record SeedEntry(
            String tenantId,
            String issuerSlug,
            List<String> credentialSchemas,
            List<String> proofSchemas) {
        public SeedEntry {
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("Schema seed tenant ID is required");
            }
            if (issuerSlug == null || issuerSlug.isBlank()) {
                throw new IllegalArgumentException("Schema seed issuer slug is required");
            }
            credentialSchemas = List.copyOf(
                    credentialSchemas == null ? List.of() : credentialSchemas);
            proofSchemas = List.copyOf(proofSchemas == null ? List.of() : proofSchemas);
        }
    }

    static final class SchemaSeedRunner implements ApplicationRunner {
        private record SeededCredential(UUID id, Map<Integer, Integer> attributeIds) {}

        private final ObjectMapper objectMapper;
        private final CredentialSchemeService credentialSchemeService;
        private final ProofSchemeService proofSchemeService;
        private final IssuerService issuerService;
        private final SeedConfig config;

        SchemaSeedRunner(
                ObjectMapper objectMapper,
                CredentialSchemeService credentialSchemeService,
                ProofSchemeService proofSchemeService,
                IssuerService issuerService,
                SeedConfig config) {
            this.objectMapper = objectMapper;
            this.credentialSchemeService = credentialSchemeService;
            this.proofSchemeService = proofSchemeService;
            this.issuerService = issuerService;
            this.config = config;
        }

        @Override
        public void run(ApplicationArguments args) throws Exception {
            for (var entry : config.entries()) {
                var identityId = identityId(entry);
                var credentialIds = seedCredentials(entry, identityId);
                seedProofs(entry, credentialIds, identityId);
            }
        }

        private int identityId(SeedEntry entry) {
            return issuerService.findAllIssuers().issuerDefinitions().stream()
                    .filter(issuer -> entry.issuerSlug().equals(issuer.slug()))
                    .filter(issuer -> issuer.tenantId() == null
                            || entry.tenantId().equals(issuer.tenantId()))
                    .map(issuer -> issuer.id())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Schema seed issuer not found: " + entry.issuerSlug()));
        }

        private Map<UUID, SeededCredential> seedCredentials(SeedEntry entry, int issuerId)
                throws Exception {
            var credentialIds = new HashMap<UUID, SeededCredential>();
            for (var file : entry.credentialSchemas()) {
                var detail = read(file, CredentialSchemeDetail.class);
                var seedId = requireId(detail.id(), file);
                UUID actualId;
                CredentialSchemeDetailResponse persisted = null;
                try {
                    persisted = credentialSchemeService.findByCredentialIdentifierAndVersion(
                            detail.credentialIdentifier(), detail.version(), false);
                    if (persisted.tenantId() != null
                            && !entry.tenantId().equals(persisted.tenantId())) {
                        throw new IllegalStateException(
                                "Schema seed credential " + detail.credentialIdentifier()
                                        + " belongs to tenant " + persisted.tenantId()
                                        + ", not " + entry.tenantId());
                    }
                    actualId = persisted.id();
                    if (persisted.state() == CredentialSchemeState.CREATED) {
                        credentialSchemeService.publishForTenant(
                                actualId, detail.version(), entry.tenantId());
                    }
                } catch (SchemaNotFoundException exception) {
                    actualId = credentialSchemeService.create(
                            withIssuer(detail, issuerId), entry.tenantId());
                    credentialSchemeService.publishForTenant(
                            actualId, detail.version(), entry.tenantId());
                    if (!detail.attributes().isEmpty()) {
                        persisted = credentialSchemeService.findById(actualId, false);
                    }
                }
                credentialIds.put(seedId, new SeededCredential(
                        actualId, attributeIds(detail, persisted, file)));
            }
            return credentialIds;
        }

        private static Map<Integer, Integer> attributeIds(
                CredentialSchemeDetail source,
                CredentialSchemeDetailResponse persisted,
                String file) {
            if (source.attributes().isEmpty()) return Map.of();
            if (persisted == null) {
                throw new IllegalStateException(
                        "Schema seed credential " + file + " has no persisted attributes");
            }

            var persistedByName = new HashMap<String, Integer>();
            for (var attribute : persisted.attributes()) {
                persistedByName.put(attribute.name(), attribute.id());
            }
            var result = new HashMap<Integer, Integer>();
            for (var attribute : source.attributes()) {
                var actualId = persistedByName.get(attribute.name());
                if (actualId == null) {
                    throw new IllegalStateException(
                            "Schema seed credential " + file
                                    + " has no persisted attribute named " + attribute.name());
                }
                result.put(attribute.id(), actualId);
            }
            return result;
        }

        private static CredentialSchemeDetail withIssuer(
                CredentialSchemeDetail source, int issuerId) {
            var settings = source.issuerSettings();
            var resolved = new IssuerSettings(
                    issuerId,
                    settings.issuerKeyType(),
                    settings.doctype(),
                    settings.namespace(),
                    settings.vct(),
                    settings.supportedCredentialTypes(),
                    settings.issClaimOverride(),
                    settings.kidOverride(),
                    settings.bbsCredentialType(),
                    settings.defaultTrustSystem(),
                    settings.signingKeyIds(),
                    settings.statusListId(),
                    settings.credentialOfferType(),
                    valueOrDefault(settings.issuanceProfileId(), DEFAULT_ISSUANCE_PROFILE));
            return new CredentialSchemeDetail(
                    source.id(),
                    source.credentialIdentifier(),
                    source.version(),
                    source.displayName(),
                    source.attributes(),
                    source.credentialSchemeStylePayloads(),
                    source.metadata(),
                    resolved,
                    source.maxBatchSize(),
                    source.templateId());
        }

        private void seedProofs(
                SeedEntry entry, Map<UUID, SeededCredential> credentialIds, int identityId)
                throws Exception {
            var existing = new HashSet<String>();
            proofSchemeService.findAll(false, List.of()).proofSchemeDetails().stream()
                    .filter(proof -> entry.tenantId().equals(proof.tenantId()))
                    .map(proof -> proof.title())
                    .forEach(existing::add);
            for (var file : entry.proofSchemas()) {
                var payload = read(file, ProofSchemePayload.class);
                if (existing.contains(payload.title())) {
                    continue;
                }

                var mapped = payload.credentialSchemes().stream()
                        .map(credential -> mapCredential(credential, credentialIds, file))
                        .toList();
                proofSchemeService.insertProofScheme(
                        copy(payload, mapped, identityId), entry.tenantId());
                existing.add(payload.title());
            }
        }

        private CredentialScheme mapCredential(
                CredentialScheme source,
                Map<UUID, SeededCredential> credentialIds,
                String file) {
            var seeded = credentialIds.get(source.id());
            if (seeded == null) {
                throw new IllegalStateException(
                        "Proof schema " + file
                                + " references unknown credential seed ID " + source.id());
            }
            var attributes = source.attributes().stream()
                    .map(attributeId -> seeded.attributeIds().get(attributeId))
                    .toList();
            if (attributes.stream().anyMatch(Objects::isNull)) {
                throw new IllegalStateException(
                        "Proof schema " + file + " references unknown attribute for credential "
                                + source.id());
            }
            return new CredentialScheme(seeded.id(), attributes);
        }

        private static UUID requireId(UUID id, String file) {
            if (id == null) {
                throw new IllegalStateException("Credential schema " + file + " has no seed ID");
            }
            return id;
        }

        private ProofSchemePayload copy(
                ProofSchemePayload source,
                List<CredentialScheme> credentials,
                int identityId) {
            var profileId = valueOrDefault(
                    source.presentationProfileId(), DEFAULT_PRESENTATION_PROFILE);
            var trustSystem = source.verifierTrustSystem();
            if (trustSystem == null) {
                trustSystem = EcosystemProfileCatalog.find(
                                profileId, EcosystemProfileRole.PRESENTATION)
                        .map(profile -> profile.policy().trustSystem())
                        .orElse(null);
            }
            return new ProofSchemePayload(
                    source.title(), source.purpose(),
                    profileId, trustSystem,
                    source.validationLogic(), source.validationMode(),
                    source.redirectUri(),
                    source.verifierIdentityId() == null ? identityId : source.verifierIdentityId(),
                    source.verifierSigningKeyId(),
                    source.proofSigningProviderId(), source.verifierClientIdScheme(),
                    source.registrationCertificate(), source.swissIdentityStatement(),
                    source.swissVerificationQueryStatement(), source.swissProtectedVerificationStatements(),
                    source.trustedAuthorities(), credentials);
        }

        private static String valueOrDefault(String value, String defaultValue) {
            return value == null || value.isBlank() ? defaultValue : value;
        }

        private <T> T read(String file, Class<T> type) {
            try {
                var root = objectMapper.readTree(Files.readString(Path.of(file)));
                if (!root.isObject()) {
                    throw new IllegalArgumentException(
                            "Schema seed file " + file + " must contain exactly one JSON object");
                }
                return objectMapper.treeToValue(root, type);
            } catch (Exception exception) {
                throw new IllegalStateException("Could not read schema seed file " + file, exception);
            }
        }
    }
}
