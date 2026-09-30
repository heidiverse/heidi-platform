// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.oca;

import java.util.Locale;
import java.util.regex.Pattern;

public enum OcaFormat {
    LEGACY("legacy"),
    SWIYU("swiyu");

    private static final Pattern SWIYU_AGENT =
            Pattern.compile("^swiyu(?:Sandbox)?Wallet(?:/|\\s|$)", Pattern.CASE_INSENSITIVE);

    private final String value;

    OcaFormat(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static OcaFormat select(String format, String userAgent) {
        return select(format, userAgent, LEGACY);
    }

    public static OcaFormat select(String format, String userAgent, OcaFormat defaultFormat) {
        // Explicit URLs bind metadata integrity to one representation, independent of headers.
        if (format != null) {
            return switch (format.toLowerCase(Locale.ROOT)) {
                case "legacy" -> LEGACY;
                case "swiyu" -> SWIYU;
                default -> throw new IllegalArgumentException("Unknown OCA format: " + format);
            };
        }
        return userAgent != null && SWIYU_AGENT.matcher(userAgent).find()
                ? SWIYU
                : defaultFormat;
    }
}
