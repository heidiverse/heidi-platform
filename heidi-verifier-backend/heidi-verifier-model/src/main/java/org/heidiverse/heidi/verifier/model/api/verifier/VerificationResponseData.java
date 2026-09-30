// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.api.verifier;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record VerificationResponseData(
        Map<String, Object> disclosures,
        @JsonProperty("validation_result") Boolean validationResult,
        @JsonProperty("transaction_data") List<String> transactionData) {}
