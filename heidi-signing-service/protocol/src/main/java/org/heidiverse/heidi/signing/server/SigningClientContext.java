// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import jakarta.servlet.http.HttpServletRequest;

/** The client the authentication filter recognised, read by the endpoints that authorize. */
public final class SigningClientContext {
    private static final String ATTRIBUTE = "heidi.signing.client";

    private SigningClientContext() {}

    static void set(HttpServletRequest request, String client) {
        request.setAttribute(ATTRIBUTE, client);
    }

    /** The authenticated client, or null where the mode carries no caller identity. */
    public static String of(HttpServletRequest request) {
        return (String) request.getAttribute(ATTRIBUTE);
    }
}
