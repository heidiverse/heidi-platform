// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service.feign;

import org.heidiverse.heidi.coordinator.model.oidc4vp.VerifierParResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import tools.jackson.databind.JsonNode;

import java.util.Map;

@FeignClient(
        name = "oid4vpVerifierFeignClient",
        url = "${heidi.platform.verifier-internal-base-url}",
        configuration = VerifierFeignClientConfiguration.class)
public interface VerifierFeignClient {

    @PostMapping(value = "/internal/verifier/v1/par", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    VerifierParResponse sendParRequest(@RequestBody Map<String, ?> formParams);

    @GetMapping(value = "/internal/verifier/v1/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> getAuthorization(@RequestParam("transactionId") String transactionId);

    @GetMapping(value = "/internal/verifier/v1/vp-token", produces = MediaType.APPLICATION_JSON_VALUE)
    JsonNode getVpToken(@RequestParam("transactionId") String transactionId);

    @GetMapping(value = "/internal/verifier/v1/state", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> getState(@RequestParam("transactionId") String transactionId);
}
