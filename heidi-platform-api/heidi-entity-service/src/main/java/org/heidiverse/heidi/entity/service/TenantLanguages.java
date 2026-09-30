// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;

final class TenantLanguages {

    static final String FALLBACK = "en";
    static final String INVALID = "tenant.languages.invalid";
    static final String FALLBACK_NOT_SELECTED = "tenant.languages.fallbackNotSelected";

    private TenantLanguages() {}

    static List<String> normalize(List<String> languages) {
        return normalize(languages, FALLBACK);
    }

    static List<String> normalize(List<String> languages, String fallback) {
        var normalizedFallback = tag(fallback);
        var normalized = languages.stream()
                .map(TenantLanguages::tag)
                .distinct()
                .toList();
        return normalized.isEmpty() ? List.of(normalizedFallback) : normalized;
    }

    static String tag(String language) {
        if (language == null || !language.matches("[A-Za-z0-9-]+")) {
            throw new IllegalArgumentException(INVALID);
        }
        try {
            var locale = new Locale.Builder().setLanguageTag(language).build();
            if (locale.getLanguage().isBlank()) {
                throw new IllegalArgumentException(INVALID);
            }
            return locale.toLanguageTag();
        } catch (IllformedLocaleException exception) {
            throw new IllegalArgumentException(INVALID);
        }
    }

    static String fallback(List<String> languages, String fallback) {
        var normalized = tag(fallback);
        if (languages.contains(normalized)) return normalized;

        throw new IllegalArgumentException(FALLBACK_NOT_SELECTED);
    }
}
