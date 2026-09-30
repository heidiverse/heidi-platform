// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.oid4vci;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionPayload {
    private final String action;
    private final UUID deferredTransactionId;
    private final Object data;
    private final boolean includeVpToken;

    @JsonCreator
    public ActionPayload(
            @JsonProperty("action") String action,
            @JsonProperty("data") Object data,
            @JsonProperty("deferredTransactionId") UUID deferredTransactionId,
            @JsonProperty("includeVpToken") Boolean includeVpToken) {
        this.action = action;
        this.data = data;
        this.deferredTransactionId = deferredTransactionId;
        this.includeVpToken = Boolean.TRUE.equals(includeVpToken);
    }

    public ActionPayload(String action, Object data, UUID deferredTransactionId) {
        this(action, data, deferredTransactionId, false);
    }

    public ActionPayload(
            String action, Object data, UUID deferredTransactionId, boolean includeVpToken) {
        this.action = action;
        this.data = data;
        this.deferredTransactionId = deferredTransactionId;
        this.includeVpToken = includeVpToken;
    }

    // Getters
    public String getAction() {
        return action;
    }

    public Object getData() {
        return data;
    }

    public UUID getDeferredTransactionId() {
        return deferredTransactionId;
    }

    public boolean getIncludeVpToken() {
        return includeVpToken;
    }
}
