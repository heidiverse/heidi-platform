// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import java.util.List;

/** Resolves and validates an issuer's OpenID Federation path. */
public interface OpenIdFederationTrustResolver {
    boolean isTrusted(String issuer, List<String> acceptedFederationEntities);
}
