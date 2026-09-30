// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.temporal.TemporalAccessor;
import java.util.Comparator;
import java.util.List;

/** Audience-based API documentation. Compatibility routes remain callable but undocumented. */
@Configuration
public class ApiDocumentationConfig {

    public static final String TAG_BACKEND_PROCESS = "Start here: backend process lifecycle";
    public static final String TAG_BROWSER_PROCESS = "Then: browser process interaction";
    public static final String TAG_CAPABILITIES = "Discover integration capabilities";
    public static final String TAG_PRESENTATION_HANDOFF = "Presentation wallet hand-off";
    public static final String TAG_PRESENTATION_SUPPORT =
            "Optional: presentation status and metadata";

    private static final List<String> INTEGRATOR_TAG_ORDER = List.of(
            TAG_BACKEND_PROCESS,
            TAG_BROWSER_PROCESS,
            TAG_CAPABILITIES,
            TAG_PRESENTATION_HANDOFF,
            TAG_PRESENTATION_SUPPORT);

    private static final String API_KEY_AUTHORIZATION = "apiKeyAuthorization";
    private static final String API_KEY_HEADER = "apiKeyHeader";
    private static final String CLIENT_INTERACTION_TOKEN = "clientInteractionToken";
    private static final String CLIENT_INTERACTION_PATH = "/interaction/v1/processes/current";
    private static final String INTEGRATION_CREDENTIALS_PATH = "/integration/v1/credentials";
    private static final String INTEGRATION_PROCESS_PREFIX = "/integration/v1/processes";
    private static final String JSON_NODE_SCHEMA = "JsonNode";
    private static final long MIN_TEMPORAL_DEFAULT_MILLIS = 946_684_800_000L;
    private static final String PLATFORM_SESSION = "platformSession";

    @Bean
    GroupedOpenApi managementApi() {
        return GroupedOpenApi.builder()
                .group("management")
                .pathsToMatch("/management/**")
                .addOpenApiCustomizer(openApi -> {
                    openApi.info(new Info()
                            .title("Heidi Management API")
                            .version("1")
                            .description(
                                    "Administrative API for Cockpit and management automation. "
                                            + "This is not the API used to run wallet processes."));
                    components(openApi).addSecuritySchemes(
                            PLATFORM_SESSION,
                            new SecurityScheme()
                                    .type(SecurityScheme.Type.HTTP)
                                    .scheme("bearer")
                                    .bearerFormat("JWT")
                                    .description("Authenticated platform session."));
                    simplifyJsonSchema(openApi);
                    removeTemporalDefaults(openApi);
                    openApi.getPaths().values().forEach(path -> path.readOperations().forEach(
                            operation -> operation.addSecurityItem(
                                    new SecurityRequirement().addList(PLATFORM_SESSION))));
                })
                .build();
    }

    @Bean
    GroupedOpenApi integratorApi() {
        return GroupedOpenApi.builder()
                .group("integrator")
                .pathsToMatch("/integration/**", "/interaction/**")
                .addOpenApiCustomizer(openApi -> {
                    openApi.info(new Info()
                            .title("Heidi Integrator API")
                            .version("1")
                            .description(
                                    "Run issuance and presentation processes.\n\n"
                                            + "## Start here\n\n"
                                            + "1. Your backend calls `POST /integration/v1/processes`.\n"
                                            + "2. Your backend calls `POST /integration/v1/processes/{processId}/start`.\n"
                                            + "3. Give only the returned `clientInteractionToken` to the browser.\n"
                                            + "4. Use Heidi Web Components, or call `GET /interaction/v1/processes/current`.\n"
                                            + "5. Your backend polls `GET /integration/v1/processes/{processId}/result`.\n\n"
                                            + "`/integration/v1` is primarily backend-to-backend."
                                            + " `/interaction/v1` is browser-facing. Sections marked"
                                            + " **Optional** support custom clients"
                                            + " and are not required for the standard flow."));
                    components(openApi)
                            .addSecuritySchemes(
                                    API_KEY_AUTHORIZATION,
                                    new SecurityScheme()
                                            .type(SecurityScheme.Type.APIKEY)
                                            .in(SecurityScheme.In.HEADER)
                                            .name("Authorization")
                                            .description("`ApiKey <API_KEY>`; backend only."))
                            .addSecuritySchemes(
                                    API_KEY_HEADER,
                                    new SecurityScheme()
                                            .type(SecurityScheme.Type.APIKEY)
                                            .in(SecurityScheme.In.HEADER)
                                            .name("X-API-KEY")
                                            .description("Alternative backend-only API key header."))
                            .addSecuritySchemes(
                                    CLIENT_INTERACTION_TOKEN,
                                    new SecurityScheme()
                                            .type(SecurityScheme.Type.HTTP)
                                            .scheme("bearer")
                                            .description(
                                                    "Short-lived browser-safe client interaction token."));
                    simplifyJsonSchema(openApi);
                    removeTemporalDefaults(openApi);
                    openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(
                            operation -> addIntegratorSecurity(path, operation)));
                    openApi.getTags().sort(Comparator.comparingInt(this::tagOrder));
                })
                .build();
    }

    @Bean
    GroupedOpenApi internalApi() {
        return group("internal", "/internal/**");
    }

    @Bean
    GroupedOpenApi protocolApi() {
        return GroupedOpenApi.builder()
                .group("protocol")
                .pathsToMatch("/public/**", "/federation/**")
                .build();
    }

    @Bean
    GroupedOpenApi operationsApi() {
        return GroupedOpenApi.builder()
                .group("operations")
                .pathsToMatch("/health", "/healthz", "/actuator/**")
                .build();
    }

    private GroupedOpenApi group(String name, String path) {
        return GroupedOpenApi.builder().group(name).pathsToMatch(path).build();
    }

    private Components components(OpenAPI openApi) {
        if (openApi.getComponents() != null) return openApi.getComponents();

        Components components = new Components();
        openApi.components(components);
        return components;
    }

    private void simplifyJsonSchema(OpenAPI openApi) {
        components(openApi).addSchemas(
                JSON_NODE_SCHEMA,
                new ObjectSchema()
                        .additionalProperties(true)
                        .description("Arbitrary JSON object."));
    }

    private void removeTemporalDefaults(OpenAPI openApi) {
        // Springdoc derives time defaults from now, making committed snapshots unstable.
        components(openApi).getSchemas().values().forEach(schema -> {
            if (schema.getProperties() == null) return;

            schema.getProperties().values().forEach(property -> {
                if (!(property instanceof Schema<?> propertySchema)) return;

                removeTemporalDefault(propertySchema);
            });
        });
    }

    private void removeTemporalDefault(Schema<?> schema) {
        Object value = schema.getDefault();
        boolean temporal = value instanceof TemporalAccessor;
        boolean epochMillis =
                value instanceof Number number && number.longValue() >= MIN_TEMPORAL_DEFAULT_MILLIS;
        if (!temporal && !epochMillis) return;

        schema.setDefault(null);
    }

    private int tagOrder(Tag tag) {
        int index = INTEGRATOR_TAG_ORDER.indexOf(tag.getName());
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private void addIntegratorSecurity(
            String path, io.swagger.v3.oas.models.Operation operation) {
        if (path.equals(CLIENT_INTERACTION_PATH)) {
            operation.addSecurityItem(
                    new SecurityRequirement().addList(CLIENT_INTERACTION_TOKEN));
            return;
        }

        if (path.equals(INTEGRATION_CREDENTIALS_PATH)) {
            operation.addSecurityItem(new SecurityRequirement().addList(API_KEY_HEADER));
            return;
        }

        if (!path.startsWith(INTEGRATION_PROCESS_PREFIX)) return;

        // Separate requirements mean either API-key header is accepted.
        operation.addSecurityItem(new SecurityRequirement().addList(API_KEY_AUTHORIZATION));
        operation.addSecurityItem(new SecurityRequirement().addList(API_KEY_HEADER));
    }
}
