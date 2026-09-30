// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.model.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NonceResponse(
        @JsonProperty("c_nonce") String cNonce,
        @JsonProperty("c_nonce_expires_in") int cNonceExpiresIn) {}
