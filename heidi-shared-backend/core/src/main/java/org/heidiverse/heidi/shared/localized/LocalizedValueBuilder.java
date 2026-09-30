// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.localized;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class LocalizedValueBuilder<T extends Serializable> {
    private final Map<Locale, T> values = new LinkedHashMap<>();
    private boolean keepLanguageOnly = true;

    LocalizedValueBuilder() {}

    public LocalizedValueBuilder<T> en(T value) {
        return add(value, Locale.ENGLISH);
    }

    public LocalizedValueBuilder<T> fr(T value) {
        return add(value, Locale.FRENCH);
    }

    public LocalizedValueBuilder<T> it(T value) {
        return add(value, Locale.ITALIAN);
    }

    public LocalizedValueBuilder<T> de(T value) {
        return add(value, Locale.GERMAN);
    }

    public LocalizedValueBuilder<T> romansh(T value) {
        return add(value, AdditionalLocales.ROMANSH);
    }

    public LocalizedValueBuilder<T> add(T value, String localeCode) {
        return add(value, LocaleHelper.fromLanguageTagThenPosix(localeCode));
    }

    public LocalizedValueBuilder<T> add(T value, Collection<Locale> locales) {
        locales.forEach(locale -> add(value, locale));
        return this;
    }

    public LocalizedValueBuilder<T> add(T value, Locale locale) {
        values.put(locale, value);
        return this;
    }

    public LocalizedValueBuilder<T> fromMap(Map<Locale, T> values) {
        values.forEach((locale, value) -> add(value, locale));
        return this;
    }

    public LocalizedValueBuilder<T> fromStringMap(Map<String, T> values) {
        values.forEach((locale, value) -> add(value, locale));
        return this;
    }

    public LocalizedValueBuilder<T> fullLocale() {
        keepLanguageOnly = false;
        return this;
    }

    public LocalizedValue<T> build() {
        return buildOptional().orElseThrow(() -> new NoValidValuesException(values));
    }

    public Optional<LocalizedValue<T>> buildOptional() {
        Map<Locale, T> cleanValues = values.entrySet().stream()
                .filter(this::isEntryValid)
                .collect(Collectors.toMap(this::finalLocale, Map.Entry::getValue, (left, right) -> right, LinkedHashMap::new));
        return cleanValues.isEmpty() ? Optional.empty() : Optional.of(new LocalizedValue<>(cleanValues));
    }

    public LocalizedValue<T> buildNullable() {
        return buildOptional().orElse(null);
    }

    private boolean isEntryValid(Map.Entry<Locale, T> entry) {
        if (entry.getKey() == null || entry.getValue() == null) {
            return false;
        }
        return !(entry.getValue() instanceof String string && string.isBlank());
    }

    private Locale finalLocale(Map.Entry<Locale, T> entry) {
        Locale locale = entry.getKey();
        return keepLanguageOnly ? Locale.of(locale.getLanguage()) : locale;
    }
}

final class AdditionalLocales {
    static final Locale ROMANSH = Locale.of("rm");

    private AdditionalLocales() {}
}

final class LocaleHelper {
    private LocaleHelper() {}

    static Locale fromLanguageTagThenPosix(String locale) {
        if (locale == null || locale.isBlank()) {
            return Locale.ROOT;
        }
        Locale languageTagLocale = Locale.forLanguageTag(locale);
        if (!languageTagLocale.getLanguage().isBlank()) {
            return languageTagLocale;
        }
        String[] parts = locale.split("[_-]", -1);
        if (parts.length == 1) {
            return Locale.of(parts[0]);
        }
        if (parts.length == 2) {
            return Locale.of(parts[0], parts[1]);
        }
        return Locale.of(parts[0], parts[1], parts[2]);
    }

    static Locale findBestMatchLocale(Locale requested, Collection<Locale> supportedLocales) {
        if (requested == null) {
            return null;
        }
        if (supportedLocales.contains(requested)) {
            return requested;
        }
        Locale languageOnly = Locale.of(requested.getLanguage());
        if (supportedLocales.contains(languageOnly)) {
            return languageOnly;
        }
        return supportedLocales.stream()
                .filter(locale -> locale.getLanguage().equals(requested.getLanguage()))
                .findFirst()
                .orElse(null);
    }
}

final class NoValidValuesException extends RuntimeException {
    NoValidValuesException(Map<Locale, ?> values) {
        super("No valid localized values found: " + values);
    }
}
