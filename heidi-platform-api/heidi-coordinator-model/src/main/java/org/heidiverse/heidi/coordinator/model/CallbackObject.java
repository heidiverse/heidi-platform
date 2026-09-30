// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CallbackObject(
        @JsonProperty("variant_id") Long variantId,
        @JsonProperty("connection_id") String connectionId,
        @JsonProperty("payment_intent_id") String paymentId) {}
