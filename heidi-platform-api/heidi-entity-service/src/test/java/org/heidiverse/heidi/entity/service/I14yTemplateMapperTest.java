// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class I14yTemplateMapperTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final UUID DATASET_ID =
            UUID.fromString("b902add5-9538-47ed-b663-f9fbfac92381");

    @Test
    void mapsJsonLdPropertiesToCredentialAttributes() throws Exception {
        var result = I14yTemplateMapper.map(
                DATASET_ID,
                MAPPER.readTree("""
                        {"data":{"title":{"de":"Register","en":"Register"},"version":"2.0.0"}}
                        """),
                MAPPER.readTree("""
                        {"@graph":[
                          {"@id":"urn:birthDate","http://www.w3.org/ns/shacl#path":{"@id":"https://example.test/birthDate"},"http://www.w3.org/ns/shacl#datatype":{"@id":"http://www.w3.org/2001/XMLSchema#date"},"http://www.w3.org/ns/shacl#minCount":1,"http://www.w3.org/ns/shacl#maxCount":1,"http://www.w3.org/2000/01/rdf-schema#label":{"de":"Geburtsdatum","en":"Birth date"}},
                          {"@id":"urn:tag","sh:path":{"@id":"https://example.test/tag"},"sh:datatype":{"@id":"http://www.w3.org/2001/XMLSchema#string"},"sh:maxCount":2,"sh:in":["a","b"],"sh:label":{"en":"Tag"}}
                        ]}
                        """));

        assertEquals("2.0.0", result.sourceVersion());
        assertEquals("Register", result.template().displayName());
        assertEquals(2, result.template().attributes().size());
        assertEquals("DATE", result.template().attributes().getFirst().type());
        assertEquals("Geburtsdatum", result.template().attributes().getFirst().displayName().get("de"));
        assertEquals("STRING", result.template().attributes().get(1).type());
        assertEquals("tag", result.template().attributes().get(1).name());
        assertTrue(result.template().attributes().get(1).isArray());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void mapsArrayWrappedJsonLdValues() throws Exception {
        var result = I14yTemplateMapper.map(
                DATASET_ID,
                MAPPER.readTree("""
                        {"data":{"title":{"en":"Beta-ID"},"version":"1.0.0"}}
                        """),
                MAPPER.readTree("""
                        [
                          {
                            "http://www.w3.org/ns/shacl#path":[{"@id":"https://example.test/given_name"}],
                            "http://www.w3.org/ns/shacl#datatype":[{"@id":"http://www.w3.org/2001/XMLSchema#string"}],
                            "http://www.w3.org/ns/shacl#name":[{"@language":"en","@value":"Given name"}],
                            "http://www.w3.org/ns/shacl#minCount":[{"@value":"1","@type":"http://www.w3.org/2001/XMLSchema#integer"}]
                          },
                          {
                            "http://www.w3.org/ns/shacl#path":[{"@id":"https://example.test/birth_date"}],
                            "http://www.w3.org/ns/shacl#datatype":[{"@id":"http://www.w3.org/2001/XMLSchema#date"}],
                            "http://www.w3.org/ns/shacl#name":[{"@language":"en","@value":"Birth date"}]
                          }
                        ]
                        """));

        assertEquals(2, result.template().attributes().size());
        assertEquals("given_name", result.template().attributes().get(0).name());
        assertEquals("STRING", result.template().attributes().get(0).type());
        assertTrue(result.template().attributeRules().get("given_name").isRequired());
        assertEquals("birth_date", result.template().attributes().get(1).name());
        assertEquals("DATE", result.template().attributes().get(1).type());
    }

    @Test
    void normalizesImportedDisplayName() throws Exception {
        var result = I14yTemplateMapper.map(
                DATASET_ID,
                MAPPER.readTree(
                        """
                        {"data":{"title":{"en":"  Residence\\n  permit  "},"version":"1.0.0"}}
                        """),
                MAPPER.readTree(
                        """
                        [{"sh:path":{"@id":"https://example.test/id"},"sh:name":{"en":"ID"}}]
                        """));

        assertEquals("Residence permit", result.template().displayName());
    }
}
