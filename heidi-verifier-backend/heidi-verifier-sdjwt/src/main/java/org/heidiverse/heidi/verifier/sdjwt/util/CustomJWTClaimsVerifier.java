// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.util;

import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;

import java.time.Instant;
import java.util.Date;
import java.util.Set;

public class CustomJWTClaimsVerifier<C extends SecurityContext>
        extends DefaultJWTClaimsVerifier<C> {

    private Instant customCurrentTime = null;

    public CustomJWTClaimsVerifier(
            final JWTClaimsSet exactMatchClaims, final Set<String> requiredClaims) {
        super(exactMatchClaims, requiredClaims);
    }

    public CustomJWTClaimsVerifier<C> withCustomCurrentTime(final Instant customCurrentTime) {
        this.customCurrentTime = customCurrentTime;
        return this;
    }

    @Override
    protected Date currentTime() {
        if (customCurrentTime != null) {
            return Date.from(customCurrentTime);
        } else {
            return new Date();
        }
    }
}
