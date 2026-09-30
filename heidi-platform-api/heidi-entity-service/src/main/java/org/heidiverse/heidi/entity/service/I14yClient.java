// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import org.heidiverse.heidi.entity.service.feign.I14yFeignClient;

import tools.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

@Component
public class I14yClient {

    private static final int FIRST_PAGE = 1;
    private static final int PAGE_SIZE = 25;
    private final I14yFeignClient feignClient;

    public I14yClient(I14yFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    public JsonNode getDataset(UUID datasetId) {
        return get("dataset", () -> feignClient.getDataset(datasetId));
    }

    public JsonNode getDatasetStructure(UUID datasetId) {
        return get("dataset structure", () -> feignClient.getDatasetStructure(datasetId));
    }

    public UUID resolveDatasetId(String datasetIdentifier) {
        String identifier = datasetIdentifier == null ? "" : datasetIdentifier.trim();
        try {
            return UUID.fromString(identifier);
        } catch (IllegalArgumentException ignored) {
            // I14Y identifiers are resolved through the dataset search endpoint.
        }

        JsonNode response = get(
                "dataset lookup",
                () -> feignClient.getDatasets(identifier, FIRST_PAGE, PAGE_SIZE));
        JsonNode data = response.get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            throw new I14yClientException(
                    "I14Y returned no dataset for identifier: " + identifier);
        }

        String datasetId = data.get(0).path("id").asText();
        try {
            return UUID.fromString(datasetId);
        } catch (IllegalArgumentException exception) {
            throw new I14yClientException(
                    "I14Y returned an invalid dataset ID for identifier: " + identifier,
                    exception);
        }
    }

    private JsonNode get(String resource, Supplier<JsonNode> request) {
        try {
            JsonNode response = request.get();
            if (response == null || response.isNull()) {
                throw new I14yClientException("I14Y returned an empty " + resource + ".");
            }
            return response;
        } catch (I14yClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new I14yClientException("Could not fetch the I14Y " + resource + ".", exception);
        }
    }
}
