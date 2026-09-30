// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.api.verifier;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VerificationRequestRedirect(
        @JsonProperty("request_uri") String requestUri,
        @JsonProperty("expires_at") long expiresAt,
        @JsonProperty("transaction_id") String transactionId) {}
