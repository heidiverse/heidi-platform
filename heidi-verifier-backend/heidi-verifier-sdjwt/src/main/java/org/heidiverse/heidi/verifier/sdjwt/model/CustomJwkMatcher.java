// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.model;

import com.nimbusds.jose.jwk.JWK;

public interface CustomJwkMatcher {
    boolean matches(JWK jwk);
}
