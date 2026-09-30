// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service.feign;

import tools.jackson.databind.JsonNode;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(
        name = "i14yFeignClient",
        url = "${heidi.i14y.public-api-url:https://api.i14y.admin.ch/api/public/v1}")
public interface I14yFeignClient {

    @GetMapping("/datasets/{datasetId}")
    JsonNode getDataset(@PathVariable("datasetId") UUID datasetId);

    @GetMapping("/datasets/{datasetId}/structures/exports/JsonLd")
    JsonNode getDatasetStructure(@PathVariable("datasetId") UUID datasetId);

    @GetMapping("/datasets")
    JsonNode getDatasets(
            @RequestParam("datasetIdentifier") String datasetIdentifier,
            @RequestParam("page") int page,
            @RequestParam("pageSize") int pageSize);
}
