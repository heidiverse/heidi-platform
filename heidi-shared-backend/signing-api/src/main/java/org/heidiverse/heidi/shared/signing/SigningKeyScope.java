// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

/**
 * Resolves a provider key URI to its stable logical-key scope.
 * Example: {@code software://kc/0f7a…/8d2b…} becomes {@code kc/0f7a…}.
 */
public final class SigningKeyScope {
    private static final String SCHEME_SEPARATOR = "://";
    private static final String PREFIX = "kc/";

    private SigningKeyScope() {}

    public static String of(String keyUri) {
        if (keyUri == null || keyUri.isBlank()) {
            throw new SigningKeyException("A key URI is required to resolve its grants");
        }
        var separator = keyUri.indexOf(SCHEME_SEPARATOR);
        var keyId = separator < 0 ? keyUri : keyUri.substring(separator + SCHEME_SEPARATOR.length());
        if (!keyId.startsWith(PREFIX)) return keyId;

        var version = keyId.indexOf('/', PREFIX.length());
        return version < 0 ? keyId : keyId.substring(0, version);
    }
}
