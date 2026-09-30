// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IssuerMetadata(
        @JsonProperty(value = "issuer", required = true) String issuer,
        @JsonProperty("jwks_uri") String jwksUri,
        @JsonProperty("jwks") Object jwks) {}
