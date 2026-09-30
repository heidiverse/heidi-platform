// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.api.wallet;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SubmissionResponse(
        @JsonProperty("redirect_uri") String redirectUri) {}
