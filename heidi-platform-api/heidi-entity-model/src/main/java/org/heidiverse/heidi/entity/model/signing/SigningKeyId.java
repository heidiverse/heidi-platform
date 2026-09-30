// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.signing;

import java.util.regex.Pattern;

/** Validation rules for the platform's logical signing-key identifiers. */
public final class SigningKeyId {
    public static final String PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{0,127}";
    public static final String MESSAGE =
            "must start with a letter or number and contain only letters, numbers, '.', '_' or '-' (max 128 characters)";

    private static final Pattern VALID_PATTERN = Pattern.compile(PATTERN);

    private SigningKeyId() {}

    public static void requireValid(String value) {
        if (value == null || !VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Key ID " + MESSAGE);
        }
    }
}
