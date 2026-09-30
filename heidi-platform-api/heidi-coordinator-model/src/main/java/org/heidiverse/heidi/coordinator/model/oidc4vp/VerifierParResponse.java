// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.oidc4vp;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VerifierParResponse(
        @JsonProperty("client_id") String clientId,
        @JsonProperty("cross_device_flow") RequestParams crossDeviceFlow,
        @JsonProperty("same_device_flow") RequestParams sameDeviceFlow) {
    public record RequestParams(
            @JsonProperty("transaction_id") String transactionId,
            @JsonProperty("request_uri") String requestUri,
            @JsonProperty("expires_at") long expiresAt // expiration timestamp in epoch milliseconds
            ) {}
}
