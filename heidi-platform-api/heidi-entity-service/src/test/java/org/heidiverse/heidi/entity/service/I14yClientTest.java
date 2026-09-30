// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.heidiverse.heidi.entity.service.feign.I14yFeignClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class I14yClientTest {

    private static final UUID DATASET_ID =
            UUID.fromString("f7223759-b198-41eb-96be-3f3bd54689fb");
    private static final String DATASET_IDENTIFIER =
            "urn:vct:ch.egovernment.residency-permit-simple";
    private static final int FIRST_PAGE = 1;
    private static final int PAGE_SIZE = 25;

    private final I14yFeignClient feignClient = mock(I14yFeignClient.class);
    private final I14yClient client = new I14yClient(feignClient);
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void delegatesDatasetRequests() {
        JsonNode dataset = JsonMapper.builder().build().createObjectNode();
        when(feignClient.getDataset(DATASET_ID)).thenReturn(dataset);

        assertSame(dataset, client.getDataset(DATASET_ID));

        verify(feignClient).getDataset(DATASET_ID);
    }

    @Test
    void translatesFeignFailures() {
        when(feignClient.getDatasetStructure(DATASET_ID))
                .thenThrow(new IllegalStateException("request failed"));

        I14yClientException exception = assertThrows(
                I14yClientException.class,
                () -> client.getDatasetStructure(DATASET_ID));

        assertSame(IllegalStateException.class, exception.getCause().getClass());
    }

    @Test
    void resolvesDatasetIdentifier() throws Exception {
        JsonNode datasets = mapper.readTree(
                """
                {"data":[{"id":"f7223759-b198-41eb-96be-3f3bd54689fb"}]}
                """
        );
        when(feignClient.getDatasets(DATASET_IDENTIFIER, FIRST_PAGE, PAGE_SIZE))
                .thenReturn(datasets);

        assertEquals(DATASET_ID, client.resolveDatasetId(DATASET_IDENTIFIER));

        verify(feignClient).getDatasets(DATASET_IDENTIFIER, FIRST_PAGE, PAGE_SIZE);
    }

    @Test
    void resolvesUuidWithoutLookup() {
        assertEquals(DATASET_ID, client.resolveDatasetId(DATASET_ID.toString()));

        verifyNoInteractions(feignClient);
    }
}
