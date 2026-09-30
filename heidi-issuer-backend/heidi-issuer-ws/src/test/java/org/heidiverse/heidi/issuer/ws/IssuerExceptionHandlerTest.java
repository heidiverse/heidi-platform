// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.ws;

import org.heidiverse.heidi.issuer.service.Oid4vciProtocolException;
import org.heidiverse.heidi.shared.signing.SigningKeyException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IssuerExceptionHandlerTest {
    @Test
    void returnsOAuthGrantErrors() {
        var response = new IssuerExceptionHandler().oid4vci(
                new Oid4vciProtocolException(
                        "invalid_grant",
                        "The pre-authorized code is invalid, expired, or already used",
                        null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("invalid_grant", response.getBody().get("error"));
        assertEquals(
                "The pre-authorized code is invalid, expired, or already used",
                response.getBody().get("error_description"));
    }

    @Test
    void identifiesSigningKeyFailures() {
        var response = new IssuerExceptionHandler().signingKey(
                new SigningKeyException("Could not resolve signing key 'software://issuer-key'"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("signing_key_unavailable", response.getBody().get("error"));
        assertEquals(
                "Could not resolve signing key 'software://issuer-key'",
                response.getBody().get("error_description"));
    }
}
