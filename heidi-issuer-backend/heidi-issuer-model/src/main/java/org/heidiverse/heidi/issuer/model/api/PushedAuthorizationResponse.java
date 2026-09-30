// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.model.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** RFC 9126 response returned by an issuer authorization extension. */
public record PushedAuthorizationResponse(
        @JsonProperty("request_uri") String requestUri,
        @JsonProperty("expires_in") int expiresIn) {}
