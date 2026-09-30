// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.model.api;

import org.heidiverse.heidi.issuer.model.TrustSystem;
public record CredentialOfferRequest(
        String token, TrustSystem trustSystem) {

    public CredentialOfferRequest {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must be supplied");
        }
    }
}
