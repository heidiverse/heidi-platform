// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.wellknown;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;

class WellKnownUriUtilTest {

    @Test
    void keepsRootIssuerAtRootWellKnownPath() throws URISyntaxException {
        assertEquals(
                URI.create("http://192.0.2.10:8082/.well-known/jwt-vc-issuer"),
                WellKnownUriUtil.fromIssuer(
                        "http://192.0.2.10:8082", "/.well-known/jwt-vc-issuer"));
    }

    @Test
    void insertsWellKnownPathBeforeIssuerPath() throws URISyntaxException {
        assertEquals(
                URI.create(
                        "http://192.0.2.10:8082/.well-known/jwt-vc-issuer/local/c/test-lphee/1.0.0"),
                WellKnownUriUtil.fromIssuer(
                        "http://192.0.2.10:8082/local/c/test-lphee/1.0.0",
                        "/.well-known/jwt-vc-issuer"));
    }

    @Test
    void dropsQueryAndFragment() throws URISyntaxException {
        assertEquals(
                URI.create(
                        "https://issuer.example/.well-known/oauth-authorization-server/local/c/test-lphee/1.0.0"),
                WellKnownUriUtil.fromIssuer(
                        URI.create(
                                "https://issuer.example/local/c/test-lphee/1.0.0?ignored=true#ignored"),
                        "/.well-known/oauth-authorization-server"));
    }
}
