// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.context;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record CredentialContextResponse(
    @JsonProperty("@context") Map<String, CredentialContext> context
) {}
