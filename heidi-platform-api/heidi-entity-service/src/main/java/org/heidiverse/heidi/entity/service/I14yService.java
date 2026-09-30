// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import org.heidiverse.heidi.entity.model.template.Template;

import tools.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class I14yService {

    private final I14yClient client;

    public I14yService(I14yClient client) {
        this.client = client;
    }

    public Result importDataset(String datasetIdentifier) {
        UUID datasetId = client.resolveDatasetId(datasetIdentifier);
        JsonNode dataset = client.getDataset(datasetId);
        JsonNode structure = client.getDatasetStructure(datasetId);
        return I14yTemplateMapper.map(datasetId, dataset, structure);
    }

    public record Result(
            UUID datasetId, Template template, String sourceVersion, List<String> warnings) {}
}
