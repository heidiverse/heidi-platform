// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.UUID;

class I14yServiceTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final UUID DATASET_ID =
            UUID.fromString("f7223759-b198-41eb-96be-3f3bd54689fb");
    private static final String DATASET_IDENTIFIER =
            "urn:vct:ch.egovernment.residency-permit-simple";

    @Test
    void importsDatasetThroughClient() throws Exception {
        I14yClient client = mock(I14yClient.class);
        I14yService service = new I14yService(client);
        JsonNode dataset = MAPPER.readTree(
                """
                {"data":{"title":{"en":"Identity"},"version":"1.0.0"}}
                """);
        JsonNode structure = MAPPER.readTree(
                """
                [
                  {
                    "sh:path":{"@id":"https://example.test/given_name"},
                    "sh:datatype":{"@id":"http://www.w3.org/2001/XMLSchema#string"},
                    "sh:name":{"en":"Given name"}
                  }
                ]
                """);
        when(client.resolveDatasetId(DATASET_IDENTIFIER)).thenReturn(DATASET_ID);
        when(client.getDataset(DATASET_ID)).thenReturn(dataset);
        when(client.getDatasetStructure(DATASET_ID)).thenReturn(structure);

        I14yService.Result result = service.importDataset(DATASET_IDENTIFIER);

        assertEquals(DATASET_ID, result.datasetId());
        assertEquals("Identity", result.template().displayName());
        assertEquals("given_name", result.template().attributes().getFirst().name());

        InOrder calls = inOrder(client);
        calls.verify(client).resolveDatasetId(DATASET_IDENTIFIER);
        calls.verify(client).getDataset(DATASET_ID);
        calls.verify(client).getDatasetStructure(DATASET_ID);
        verifyNoMoreInteractions(client);
    }
}
