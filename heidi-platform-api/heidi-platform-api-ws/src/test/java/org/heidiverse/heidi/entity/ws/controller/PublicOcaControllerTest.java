// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService;
import org.heidiverse.heidi.entity.model.credentialscheme.AttributeType;
import org.heidiverse.heidi.entity.model.credentialscheme.OcaVersion;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeAttributeEntity;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeEntity;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeStyleEntity;
import org.heidiverse.heidi.entity.service.CredentialSchemeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicOcaControllerTest {
    private static final String SCHEMA_URL = "/public/v2/schema/card/1";
    private static final String LEGACY = "{\"capture_base\":{},\"overlays\":[]}";
    private final ObjectMapper json = new ObjectMapper();
    private final CredentialSchemeStyleEntity style = new CredentialSchemeStyleEntity();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var scheme = new CredentialSchemeEntity();
        scheme.setCredentialIdentifier("card");
        scheme.setVersion("1");
        scheme.setDisplayName("Employee card");
        style.setCredentialSchemeEntity(scheme);
        style.setStyle("{\"cardColor\":4279312947,\"textColor\":\"light\"}");
        style.setOcaBundle(LEGACY);
        style.setOcaBundleFileName("old");
        var attribute = new CredentialSchemeAttributeEntity();
        attribute.setFieldName("givenName");
        attribute.setFieldType(AttributeType.STRING);
        attribute.setFormatSpecificAttributeName(Map.of("SD_JWT", "given_name"));
        attribute.setDisplayName(Map.of("de", "Vorname", "en", "Given name"));
        var data = mock(CredentialSchemeDataService.class);
        when(data.findByCredentialIdentifierAndVersion("card", "1")).thenReturn(Optional.of(scheme));
        when(data.findAllStylesByCredentialSchemeEntity(scheme)).thenReturn(List.of(style));
        when(data.findAttributesByCredentialScheme(scheme)).thenReturn(List.of(attribute));
        when(data.findBundleByOcaBundleFileName("old")).thenReturn(Optional.of(LEGACY));
        when(data.findSwiyuStyle(anyString())).thenAnswer(call -> {
            String file = call.getArgument(0);
            return file.equals("old") || file.equals(style.getSwiyuOcaFileName())
                    ? Optional.of(style) : Optional.empty();
        });
        var service = new CredentialSchemeService(data, null, null, null, json);
        ReflectionTestUtils.setField(service, "entityBaseUrl", "https://platform.example");
        ReflectionTestUtils.setField(service, "issuerBaseUrl", "https://issuer.example");
        mvc = MockMvcBuilders.standaloneSetup(new PublicCredentialSchemaController(service)).build();
    }

    @Test
    void metadataPinsBundleAndHash() throws Exception {
        var metadata = json.readTree(mvc.perform(get(SCHEMA_URL).header(HttpHeaders.USER_AGENT, "swiyuWallet"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(HttpHeaders.VARY,
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString(HttpHeaders.USER_AGENT))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        var oca = metadata.get("display").get(0).get("rendering").get("oca");
        var uri = URI.create(oca.get("uri").asString());
        assertNull(uri.getQuery());
        assertEquals("/oca/" + style.getSwiyuOcaFileName() + ".json", uri.getPath());
        // The issuer forwards this explicit format; the platform needs no wallet header here.
        var raw = mvc.perform(get("/public/v2/oca/" + style.getSwiyuOcaFileName())
                        .header(HttpHeaders.USER_AGENT, "swiyuWallet"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        assertEquals("sha256-" + Base64.getEncoder().encodeToString(hash), oca.get("uri#integrity").asString());
        assertFalse(oca.get("uri#integrity").asString().contains("%"));
        assertTrue(json.readTree(raw).has("capture_bases"));
        assertTrue(raw.contains("Vorname"));
        assertTrue(raw.contains("given_name"));
        assertTrue(raw.contains("\"primary_background_color\":\"#112233\""));
        assertEquals(LEGACY, style.getOcaBundle());
        assertEquals("old", style.getOcaBundleFileName());

        mvc.perform(get("/public/v2/oca/" + style.getSwiyuOcaFileName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capture_bases").exists());
    }

    @Test
    void preservesLegacyResponses() throws Exception {
        var metadata = json.readTree(mvc.perform(get(SCHEMA_URL)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("https://issuer.example/oca/old.json",
                metadata.get("display").get(0).get("rendering").get("oca").get("uri").asString());
        mvc.perform(get("/public/v2/oca/old")).andExpect(status().isOk()).andExpect(content().string(LEGACY));
        assertNull(style.getSwiyuOcaBundle());
    }

    @Test
    void refreshesExistingStyles() throws Exception {
        style.setStyle("{}");
        mvc.perform(get(SCHEMA_URL).header(HttpHeaders.USER_AGENT, "swiyuSandboxWallet"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.display[0].rendering.oca.uri").exists());
        var bundle = style.getSwiyuOcaBundle();
        var file = style.getSwiyuOcaFileName();
        style.getCredentialSchemeEntity().setDisplayName("Later title");
        mvc.perform(get("/public/v2/oca/old").header(HttpHeaders.USER_AGENT, "swiyuWallet"))
                .andExpect(status().isOk()).andExpect(content().string(style.getSwiyuOcaBundle()));
        assertNotEquals(bundle, style.getSwiyuOcaBundle());
        assertNotEquals(file, style.getSwiyuOcaFileName());
    }

    @Test
    void usesConfiguredVersionWithoutHeader() throws Exception {
        style.setOcaVersion(OcaVersion.SWIYU);

        var metadata = json.readTree(mvc.perform(get(SCHEMA_URL))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertEquals("/oca/" + style.getSwiyuOcaFileName() + ".json",
                URI.create(metadata.get("display").get(0).get("rendering").get("oca")
                        .get("uri").asString()).getPath());
        mvc.perform(get("/public/v2/oca/" + style.getSwiyuOcaFileName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capture_bases").exists());
    }
}
