// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.api.verifier;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CredentialsRequestResponse(
        @JsonProperty("client_id") String clientId,
        @JsonProperty("cross_device_flow") VerificationRequestRedirect crossDeviceFlow,
        @JsonProperty("same_device_flow") VerificationRequestRedirect sameDeviceFlow) {}
