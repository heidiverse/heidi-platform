// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.model.api;

import java.util.List;
import java.util.Map;

public record JwtVcIssuerMetadata(String issuer, JwkSet jwks) {
    /** JWK members are extensible by definition, so only the surrounding set is fixed. */
    public record JwkSet(List<Map<String, Object>> keys) {}
}
