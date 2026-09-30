// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest(
        classes = {
            HeidiPlatformApiApplication.class,
            OpenApiSnapshotIntegrationTest.TestSecurityConfiguration.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "management.server.port=0",
            "spring.main.allow-bean-definition-overriding=true",
            "springdoc.api-docs.enabled=true",
            "springdoc.swagger-ui.enabled=false"
        })
@ActiveProfiles("test")
class OpenApiSnapshotIntegrationTest {

    private static final String UPDATE_PROPERTY = "openapi.update";
    private static final Path SNAPSHOT_DIRECTORY = Path.of("docs", "openapi");

    @SuppressWarnings("resource")
    @ServiceConnection
    static PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:latest"));

    static {
        postgresContainer.start();
    }

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void openApiMatchesCommittedSnapshots() throws Exception {
        assertSnapshot("integrator");
        assertSnapshot("management");
    }

    @Test
    void integratorApiKeepsGeneratedSchemas() throws Exception {
        JsonNode document = openApi("integrator");

        assertFalse(document.at("/components/schemas/InitializeProcessRequest").isMissingNode());
        assertFalse(document.at("/components/schemas/StartIntegrationProcessRequest")
                .isMissingNode());
        assertTrue(document.at("/components/schemas/JsonNode/additionalProperties").asBoolean());
        assertTrue(document.at("/components/schemas/JsonNode/properties").isMissingNode());
        assertTrue(document.at(
                        "/components/schemas/IntegrationProcessResponse/properties/expiresAt/default")
                .isMissingNode());
    }

    @Test
    void integratorApiDocumentsPresentationRedirect() throws Exception {
        JsonNode document = openApi("integrator");
        JsonNode responses = document.at(
                "/paths/~1interaction~1v1~1qr~1openid4vp/get/responses");

        assertTrue(responses.path("200").isMissingNode());
        assertEquals(
                "uri",
                responses.path("303")
                        .path("headers")
                        .path("Location")
                        .path("schema")
                        .path("format")
                        .asText());
    }

    private void assertSnapshot(String group) throws Exception {
        String actual = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(openApi(group))
                + System.lineSeparator();
        Path snapshot = repositoryRoot().resolve(SNAPSHOT_DIRECTORY).resolve(group + ".json");

        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            Files.createDirectories(snapshot.getParent());
            Files.writeString(snapshot, actual);
            return;
        }

        assertEquals(
                Files.readString(snapshot),
                actual,
                "OpenAPI changed. Run `just openapi` and commit the reviewed snapshots.");
    }

    private JsonNode openApi(String group) throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String response = mockMvc.perform(get("/api-docs/{group}", group))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private Path repositoryRoot() throws IOException {
        Path directory = Path.of("").toAbsolutePath();

        while (directory != null && !Files.exists(directory.resolve("justfile"))) {
            directory = directory.getParent();
        }

        assertNotNull(directory, "Could not find repository root");
        return directory;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSecurityConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token ->
                    Jwt.withTokenValue(token)
                            .header("alg", "none")
                            .subject("openapi-snapshot")
                            .build();
        }
    }
}
