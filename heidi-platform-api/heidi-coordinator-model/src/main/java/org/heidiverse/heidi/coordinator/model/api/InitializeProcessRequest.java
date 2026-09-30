// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.api;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.heidiverse.heidi.coordinator.model.oid4vci.PresentationData;
import org.heidiverse.heidi.coordinator.model.oid4vci.PreAuthIssuanceData;

import tools.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic process initialization request.
 *
 * <p>Unknown top-level fields are retained as extension data. This allows a
 * hosted deployment to add private process actions without adding those
 * actions or their payload models to the OSS coordinator contract.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class InitializeProcessRequest {

    @NotNull private String action;
    private PreAuthIssuanceData preAuthIssuanceData;
    private PresentationData presentationData;
    @JsonProperty("includeVpToken") private boolean includeVpToken;
    @JsonProperty("clientDisplayClaims") private List<String> clientDisplayClaims;
    @JsonProperty("clientConfiguration") private JsonNode clientConfiguration;
    private final Map<String, JsonNode> extensionData = new LinkedHashMap<>();

    public InitializeProcessRequest() {}

    public InitializeProcessRequest(
            @JsonProperty("action") String action,
            @JsonProperty("preAuthIssuanceData") PreAuthIssuanceData preAuthIssuanceData,
            @JsonProperty("presentationData") PresentationData presentationData) {
        this.action = action;
        this.preAuthIssuanceData = preAuthIssuanceData;
        this.presentationData = presentationData;
    }

    public String action() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public PreAuthIssuanceData preAuthIssuanceData() {
        return preAuthIssuanceData;
    }

    public void setPreAuthIssuanceData(PreAuthIssuanceData preAuthIssuanceData) {
        this.preAuthIssuanceData = preAuthIssuanceData;
    }

    public PresentationData presentationData() {
        return presentationData;
    }

    public void setPresentationData(PresentationData presentationData) {
        this.presentationData = presentationData;
    }

    public boolean includeVpToken() {
        return includeVpToken;
    }

    public void setIncludeVpToken(boolean includeVpToken) {
        this.includeVpToken = includeVpToken;
    }

    public List<String> clientDisplayClaims() {
        return clientDisplayClaims;
    }

    public void setClientDisplayClaims(List<String> clientDisplayClaims) {
        this.clientDisplayClaims = clientDisplayClaims;
    }

    public JsonNode clientConfiguration() {
        return clientConfiguration;
    }

    public void setClientConfiguration(JsonNode clientConfiguration) {
        this.clientConfiguration = clientConfiguration;
    }

    @JsonAnySetter
    public void setExtensionData(String name, JsonNode value) {
        extensionData.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, JsonNode> extensionData() {
        return extensionData;
    }
}
