// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CredentialContextResponse(
        @JsonProperty("@context") Map<String, CredentialContext> context) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CredentialContext(
            @JsonProperty("@id") String id,
            @JsonProperty("@context") Map<String, ClaimContext> claims) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClaimContext(
            @JsonProperty("@id") String id,
            @JsonProperty("@type") String type) {}
}
