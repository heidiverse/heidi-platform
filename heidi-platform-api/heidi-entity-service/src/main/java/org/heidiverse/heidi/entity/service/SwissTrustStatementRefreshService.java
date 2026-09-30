// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.heidiverse.heidi.entity.data.service.IssuerDataService;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustConfigurationRequest;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.issuer.SwissVerificationQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Refreshes Swiss trust statements stored on identity-statement slots. */
@Service
public class SwissTrustStatementRefreshService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SwissTrustStatementRefreshService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int VQPS_PAGE_SIZE = 100;
    private static final int VQPS_MAX_PAGES = 100;

    private final IssuerDataService issuerDataService;
    private final IdentityKeySlotService identityKeySlotService;
    private final RestClient.Builder clientBuilder;

    public SwissTrustStatementRefreshService(
            IssuerDataService issuerDataService,
            IdentityKeySlotService identityKeySlotService,
            RestClient.Builder clientBuilder) {
        this.issuerDataService = issuerDataService;
        this.identityKeySlotService = identityKeySlotService;
        this.clientBuilder = clientBuilder;
    }

    public Optional<String> requestVerificationQueryStatement(
            IssuerDefinitionEntity identity,
            IssuerTrustConfigurationRequest settings,
            String title,
            String purpose,
            String scope,
            JsonNode query) {
        if (settings == null || !hasText(settings.swissDid())
                || !hasText(settings.swissTrustRegistryAuthoringUrl())) return Optional.empty();
        var accessToken = retrieveAccessToken(identity, settings);
        if (accessToken.isEmpty()) return Optional.empty();

        var request = new LinkedHashMap<String, Object>();
        request.put("waitForPublication", true);
        request.put("sub", settings.swissDid());
        request.put("purpose_name", Map.of("default", limit(title, 40, "Verification")));
        request.put("purpose_description", Map.of("default", limit(purpose, 1000, "Verification")));
        request.put("scope", scope);
        request.put("query", query);

        var response = clientBuilder.clone()
                .baseUrl(settings.swissTrustRegistryAuthoringUrl())
                .build()
                .post()
                .uri("/api/v1/trust/vqps-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken.get())
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        var statement = response == null
                ? null : response.path("publicationResult").path("jwt").asText(null);
        if (!hasText(statement)) {
            throw new IllegalStateException(
                    "Swiss trust registry did not return a verification query statement");
        }
        return Optional.of(statement);
    }

    public List<SwissVerificationQuery> listVerificationQueryStatements(
            IssuerDefinitionEntity identity, IssuerTrustConfigurationRequest settings) {
        if (settings == null || !hasText(settings.swissTrustRegistryAuthoringUrl())) {
            return List.of();
        }
        var accessToken = retrieveAccessToken(identity, settings);
        if (accessToken.isEmpty()) return List.of();

        var result = new ArrayList<SwissVerificationQuery>();
        for (int page = 0; page < VQPS_MAX_PAGES; page++) {
            final var pageNumber = page;
            var response = clientBuilder.clone()
                    .baseUrl(settings.swissTrustRegistryAuthoringUrl())
                    .build()
                    .get()
                    .uri(uri -> uri.path("/api/v1/trust/vqps-submissions")
                            .queryParam("page", pageNumber)
                            .queryParam("size", VQPS_PAGE_SIZE)
                            .build())
                    .header("Authorization", "Bearer " + accessToken.get())
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.path("content").isArray()) break;

            var content = response.path("content");
            for (var item : content) {
                var query = toVerificationQuery(item);
                if (query != null) result.add(query);
            }
            if (response.path("last").asBoolean()
                    || content.size() < VQPS_PAGE_SIZE) break;
        }
        return List.copyOf(result);
    }

    public boolean matchesVerificationQuery(
            String statement, String scope, JsonNode query) {
        var payload = jwtPayload(statement);
        if (payload == null) return false;
        var expiration = payload.get("exp");
        if (expiration == null
                || !expiration.isNumber()
                || expiration.asLong() <= Instant.now().getEpochSecond()) return false;
        var request = payload.get("request");
        if (request == null) return false;
        return Objects.equals(text(request, "scope"), scope)
                && Objects.equals(request.get("query"), query);
    }

    public Optional<String> retrieveAccessToken(
            IssuerDefinitionEntity identity, IssuerTrustConfigurationRequest settings) {
        if (!hasText(settings.swissTrustRegistryTokenUrl())
                || !hasText(settings.swissTrustRegistryClientId())
                || !hasText(settings.swissTrustRegistryClientSecret())
                || !hasText(settings.swissTrustRegistryRefreshToken())) return Optional.empty();
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "refresh_token");
            form.add("refresh_token", settings.swissTrustRegistryRefreshToken());
            form.add("client_id", settings.swissTrustRegistryClientId());
            form.add("client_secret", settings.swissTrustRegistryClientSecret());
            var response = clientBuilder.clone()
                    .build()
                    .post()
                    .uri(settings.swissTrustRegistryTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !hasText(response.path("access_token").asText(null))) {
                return Optional.empty();
            }
            var rotatedRefreshToken = response.path("refresh_token").asText(null);
            if (hasText(rotatedRefreshToken)
                    && !rotatedRefreshToken.equals(settings.swissTrustRegistryRefreshToken())) {
                persistRotatedRefreshToken(identity, settings, rotatedRefreshToken);
            }
            return Optional.of(response.path("access_token").asText());
        } catch (Exception exception) {
            LOGGER.warn("Could not retrieve a Swiss Trust Registry access token", exception);
            return Optional.empty();
        }
    }

    private void persistRotatedRefreshToken(
            IssuerDefinitionEntity identity,
            IssuerTrustConfigurationRequest settings,
            String refreshToken) {
        if (identity == null || identityKeySlotService == null) return;
        var updated = new IssuerTrustConfigurationRequest(
                settings.issuerClaim(), settings.swissDid(), settings.swissRegistryBaseUrl(),
                settings.swissStatusRegistryApiUrl(), settings.swissStatusRegistryPartnerId(),
                settings.swissTrustRegistryAuthoringUrl(), settings.swissTrustRegistryTokenUrl(),
                settings.swissTrustRegistryClientId(), settings.swissTrustRegistryClientSecret(),
                refreshToken, settings.swissIdentityStatement(), settings.swissIssuanceStatements(),
                settings.eudiVerificationTrustAnchors(), settings.swissVerificationTrustAnchor());
        try {
            identityKeySlotService.updateConfiguration(
                    identity.getTenantId(), identity.getId(), IdentityKeySlotType.IDENTITY_STATEMENT,
                    IssuerTrustSystem.Switzerland, null, JSON.valueToTree(updated));
        } catch (Exception exception) {
            LOGGER.warn("Could not persist the rotated Swiss Trust Registry refresh token", exception);
        }
    }

    @Scheduled(fixedDelayString = "${heidi.platform.swiss-trust.refresh-interval-ms:3600000}")
    public void refreshExpiringStatements() {
        var refreshBefore = Instant.now().plus(1, ChronoUnit.HOURS);
        for (var issuer : issuerDataService.findAll()) {
            for (var slot : identityKeySlotService.slots(issuer.getId())) {
                if (slot.getType() != IdentityKeySlotType.IDENTITY_STATEMENT
                        || slot.getTrustSystem() != IssuerTrustSystem.Switzerland) continue;
                var settings = settings(slot);
                if (!hasText(settings.swissDid()) || !hasText(settings.swissRegistryBaseUrl())) continue;
                if (expiry(settings.swissIdentityStatement()).isAfter(refreshBefore)
                        && expiry(settings.swissIssuanceStatements()).isAfter(refreshBefore)) continue;
                try {
                    var refreshed = refresh(settings.swissDid(), settings.swissRegistryBaseUrl());
                    var updated = new IssuerTrustConfigurationRequest(
                            settings.issuerClaim(), settings.swissDid(), settings.swissRegistryBaseUrl(),
                            settings.swissStatusRegistryApiUrl(), settings.swissStatusRegistryPartnerId(),
                            settings.swissTrustRegistryAuthoringUrl(), settings.swissTrustRegistryTokenUrl(),
                            settings.swissTrustRegistryClientId(), settings.swissTrustRegistryClientSecret(),
                            settings.swissTrustRegistryRefreshToken(),
                            refreshed.identityStatement(), refreshed.issuanceStatements(), settings.eudiVerificationTrustAnchors(),
                            settings.swissVerificationTrustAnchor());
                    identityKeySlotService.updateConfiguration(
                            issuer.getTenantId(), issuer.getId(), IdentityKeySlotType.IDENTITY_STATEMENT,
                            IssuerTrustSystem.Switzerland, null, JSON.valueToTree(updated));
                } catch (Exception exception) {
                    LOGGER.warn("Could not refresh Swiss trust statements for issuer '{}'", issuer.getSlug(), exception);
                }
            }
        }
    }

    public RefreshedStatements refresh(String did, String registryUrl) {
        var client = clientBuilder.clone().baseUrl(registryUrl).build();
        var identity = client.get().uri(
                        "/api/v2/identity-trust-statement/{identifier}", did)
                .retrieve().body(String.class);
        var normalizedIdentity = serializedStatement(identity);
        var identityPayload = validateStatement(
                normalizedIdentity, "swiyu-identity-trust-statement+jwt", did);
        var listBody = client.get().uri(uri -> uri
                        .path("/api/v2/protected-issuance-authorization-trust-statement/")
                        .queryParam("sub", did)
                        .queryParam("filterActive", true)
                        .queryParam("size", 100)
                        .build())
                .retrieve().body(String.class);
        var root = readJson(listBody);
        var statements = new HashMap<String, String>();
        for (var item : root.path("content")) {
            var compact = serializedStatement(item.isString() ? item.asString() : item.toString());
            var payload = validateStatement(
                    compact,
                    "swiyu-protected-issuance-authorization-trust-statement+jwt",
                    did);
            var permissions = payload.has("permissions") ? payload.get("permissions") : payload.get("can_issue");
            if (permissions == null) continue;
            if (permissions.isArray()) {
                for (var permission : permissions) {
                    if (permission.has("vct")) statements.put(permission.get("vct").asString(), compact);
                }
            } else if (permissions.has("vct")) {
                var vct = permissions.get("vct");
                if (vct.isArray()) vct.forEach(value -> statements.put(value.asString(), compact));
                else statements.put(vct.asString(), compact);
            }
        }
        return new RefreshedStatements(
                normalizedIdentity,
                Instant.ofEpochSecond(identityPayload.get("exp").asLong()),
                statements,
                statements.values().stream().map(this::expiry).min(Instant::compareTo).orElse(null));
    }

    public record RefreshedStatements(
            String identityStatement,
            Instant identityStatementExpiresAt,
            Map<String, String> issuanceStatements,
            Instant issuanceStatementsExpiresAt) {}

    private IssuerTrustConfigurationRequest settings(IdentityKeySlotEntity slot) {
        var node = slot.getConfiguration();
        if (node == null || node.isNull()) {
            return new IssuerTrustConfigurationRequest(null, null, null, null, null, null, null);
        }
        try {
            return JSON.treeToValue(node, IssuerTrustConfigurationRequest.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid Swiss trust configuration", exception);
        }
    }

    private JsonNode validateStatement(String compact, String expectedType, String subject) {
        var parts = compact.split("\\.");
        if (parts.length != 3) throw new IllegalArgumentException("Registry returned a non-compact JWT");
        var header = readJson(new String(Base64.getUrlDecoder().decode(parts[0])));
        var payload = readJson(new String(Base64.getUrlDecoder().decode(parts[1])));
        if (!expectedType.equals(header.path("typ").asString())) {
            throw new IllegalArgumentException("Registry returned an unexpected trust statement type");
        }
        if (!subject.equals(payload.path("sub").asString())) {
            throw new IllegalArgumentException("Registry returned a trust statement for a different DID");
        }
        if (!payload.has("exp") || payload.get("exp").asLong() <= Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("Registry returned an expired trust statement");
        }
        return payload;
    }

    private String serializedStatement(String body) {
        if (!hasText(body)) throw new IllegalArgumentException("Registry returned an empty statement");
        var trimmed = body.trim();
        if (trimmed.startsWith("\"")) return readJson(trimmed).asString();
        if (trimmed.startsWith("{")) {
            var object = readJson(trimmed);
            for (var field : new String[] {"statement", "jwt", "value"}) {
                if (object.has(field) && object.get(field).isString()) return object.get(field).asString();
            }
        }
        return trimmed;
    }

    private JsonNode readJson(String value) {
        try { return JSON.readTree(value); }
        catch (Exception exception) { throw new IllegalArgumentException("Invalid registry JSON", exception); }
    }

    private SwissVerificationQuery toVerificationQuery(JsonNode item) {
        var publication = item.path("publicationResult");
        var jwt = text(publication, "jwt");
        var payload = jwtPayload(jwt);
        var request = payload == null ? null : payload.get("request");
        if (request == null) request = item.get("request");
        if (request == null) request = JSON.createObjectNode();
        var purpose = payload == null ? null : payload.get("purpose");
        if (purpose == null) purpose = item.get("purpose");
        var purposeName = localized(purpose, "name");
        if (!hasText(purposeName)) purposeName = claim(payload, "purpose_name");
        var purposeDescription = localized(purpose, "description");
        if (!hasText(purposeDescription)) {
            purposeDescription = claim(payload, "purpose_description");
        }
        return new SwissVerificationQuery(
                text(item, "id"),
                item.has("version") ? item.get("version").asInt() : null,
                text(item, "status"),
                purposeName,
                purposeDescription,
                text(request, "scope"),
                request.get("query"),
                jwt,
                instant(publication, "expiresAt", payload),
                instant(item, "createdAt", null),
                instant(item, "updatedAt", null));
    }

    private JsonNode jwtPayload(String compact) {
        if (!hasText(compact)) return null;
        var parts = compact.split("\\.");
        if (parts.length != 3) return null;
        try {
            return JSON.readTree(new String(
                    Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Instant instant(JsonNode primary, String field, JsonNode fallback) {
        var value = text(primary, field);
        if (!hasText(value) && fallback != null) value = text(fallback, field);
        if (!hasText(value)) return null;
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String localized(JsonNode root, String field) {
        if (root == null) return null;
        var value = root.get(field);
        if (value == null || value.isNull()) return null;
        return value.isTextual() ? value.asText() : text(value, "default");
    }

    private String claim(JsonNode root, String field) {
        var value = root == null ? null : root.get(field);
        if (value == null || value.isNull()) return null;
        return value.isObject() ? text(value, "default") : value.asText(null);
    }

    private String text(JsonNode root, String field) {
        if (root == null || root.get(field) == null || root.get(field).isNull()) return null;
        return root.get(field).asText(null);
    }

    private Instant expiry(String statement) {
        if (!hasText(statement)) return Instant.MIN;
        var parts = statement.split("\\.");
        if (parts.length != 3) return Instant.MIN;
        try {
            return Instant.ofEpochSecond(readJson(
                    new String(Base64.getUrlDecoder().decode(parts[1]))).path("exp").asLong());
        } catch (RuntimeException exception) {
            return Instant.MIN;
        }
    }

    private Instant expiry(Map<String, String> statements) {
        if (statements == null || statements.isEmpty()) return Instant.MIN;
        return statements.values().stream().map(this::expiry).min(Instant::compareTo).orElse(Instant.MIN);
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }

    private static String limit(String value, int maxLength, String fallback) {
        var text = hasText(value) ? value.trim() : fallback;
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
