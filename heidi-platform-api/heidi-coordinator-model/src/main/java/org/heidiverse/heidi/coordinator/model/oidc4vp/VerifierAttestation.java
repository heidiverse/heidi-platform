// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.oidc4vp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record VerifierAttestation(
        String format,
        String data,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) @JsonProperty("credential_ids")
                List<String> credentialIds) {
    public VerifierAttestation(String format, String data) { this(format, data, null); }
}
