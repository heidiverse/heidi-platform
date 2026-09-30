// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.heidiverse.heidi.entity.model.credentialscheme.AttributeType;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeAttributeEntity;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeEntity;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class TypeMetadataUtilsTest {

    @Test
    void defaultVctMatchesTheSchemaUrlUsedByDcqlQueries() {
        var scheme = new CredentialSchemeEntity();
        scheme.setCredentialIdentifier("employee-card");
        scheme.setVersion("1.2");

        var attribute = new CredentialSchemeAttributeEntity();
        attribute.setFieldName("active");
        attribute.setFieldType(AttributeType.BOOLEAN);
        attribute.setDisplayName(Map.of("en-US", "Active"));
        attribute.setDisclosable(false);

        var metadata =
                TypeMetadataUtils.buildTypeMetadata(
                        scheme,
                        List.of(attribute),
                        List.of(),
                        "https://entity.example",
                        "https://issuer.example");

        assertEquals(
                "https://entity.example/public/v2/schema/employee-card/1.2",
                metadata.vct());

        var json = new ObjectMapper().valueToTree(metadata);
        assertEquals(
                "https://json-schema.org/draft/2020-12/schema",
                json.get("schema").get("$schema").asString());
        assertEquals(
                "boolean",
                json.get("schema").get("properties").get("active").get("type").asString());
        assertEquals(
                "string",
                json.get("schema").get("properties").get("vct_metadata_uri").get("type").asString());
        assertEquals("never", json.get("claims").get(0).get("sd").asString());
        assertEquals("active", json.get("claims").get(0).get("svg_id").asString());
        assertFalse(json.has("name"));
        assertFalse(json.has("description"));
    }
}
