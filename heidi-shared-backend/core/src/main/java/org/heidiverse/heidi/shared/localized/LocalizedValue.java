// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.localized;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class LocalizedValue<T extends Serializable> {
    private final Map<Locale, T> values;

    LocalizedValue(Map<Locale, T> values) {
        this.values = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }

    public static <T extends Serializable> LocalizedValueBuilder<T> builder() {
        return new LocalizedValueBuilder<>();
    }

    public static LocalizedValueBuilder<String> stringBuilder() {
        return new LocalizedValueBuilder<>();
    }

    public static <T extends Serializable> LocalizedValue<T> from(T value, Locale locale) {
        return LocalizedValue.<T>builder().add(value, locale).build();
    }

    public static <T extends Serializable> LocalizedValue<T> from(T value, Collection<Locale> locales) {
        return LocalizedValue.<T>builder().add(value, locales).build();
    }

    public static <T extends Serializable> LocalizedValue<T> fromMap(Map<Locale, T> values) {
        return LocalizedValue.<T>builder().fromMap(values).build();
    }

    public static <T extends Serializable> LocalizedValue<T> fromStringMap(Map<String, T> values) {
        return LocalizedValue.<T>builder().fromStringMap(values).build();
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static <T extends Serializable> LocalizedValue<T> fromStringMapFullLocale(Map<String, T> values) {
        return LocalizedValue.<T>builder().fullLocale().fromStringMap(values).build();
    }

    public static <T extends Serializable> LocalizedValue<T> en(T value) {
        return LocalizedValue.<T>builder().en(value).build();
    }

    public static <T extends Serializable> LocalizedValue<T> de(T value) {
        return LocalizedValue.<T>builder().de(value).build();
    }

    public static <T extends Serializable> LocalizedValue<T> fr(T value) {
        return LocalizedValue.<T>builder().fr(value).build();
    }

    public static <T extends Serializable> LocalizedValue<T> it(T value) {
        return LocalizedValue.<T>builder().it(value).build();
    }

    public static <T extends Serializable> LocalizedValue<T> romansh(T value) {
        return LocalizedValue.<T>builder().romansh(value).build();
    }

    public static <T extends Serializable> LocalizedValue<T> mergeAll(Collection<LocalizedValue<T>> values) {
        Map<Locale, T> merged = new LinkedHashMap<>();
        values.forEach(value -> merged.putAll(value.values));
        return LocalizedValue.fromMap(merged);
    }

    public LocalizedValue<T> mergeWith(LocalizedValue<T> value) {
        return mergeAll(List.of(this, value));
    }

    public T get(String key) {
        return getOptional(key).orElseThrow();
    }

    public T getNullable(String key) {
        return getOptional(key).orElse(null);
    }

    public Optional<T> getOptional(String key) {
        return getOptional(LocaleHelper.fromLanguageTagThenPosix(key));
    }

    public T get(Locale locale) {
        return getOptional(locale).orElseThrow();
    }

    public T getNullable(Locale locale) {
        return getOptional(locale).orElse(null);
    }

    public Optional<T> getOptional(Locale locale) {
        Locale matchedLocale = LocaleHelper.findBestMatchLocale(locale, values.keySet());
        return Optional.ofNullable(matchedLocale).map(values::get);
    }

    public T en() {
        return enOptional().orElseThrow();
    }

    public T de() {
        return deOptional().orElseThrow();
    }

    public T fr() {
        return frOptional().orElseThrow();
    }

    public T it() {
        return itOptional().orElseThrow();
    }

    public T romansh() {
        return romanshOptional().orElseThrow();
    }

    public T enNullable() {
        return enOptional().orElse(null);
    }

    public T deNullable() {
        return deOptional().orElse(null);
    }

    public T frNullable() {
        return frOptional().orElse(null);
    }

    public T itNullable() {
        return itOptional().orElse(null);
    }

    public T romanshNullable() {
        return romanshOptional().orElse(null);
    }

    public Optional<T> enOptional() {
        return getOptional(Locale.ENGLISH);
    }

    public Optional<T> deOptional() {
        return getOptional(Locale.GERMAN);
    }

    public Optional<T> frOptional() {
        return getOptional(Locale.FRENCH);
    }

    public Optional<T> itOptional() {
        return getOptional(Locale.ITALIAN);
    }

    public Optional<T> romanshOptional() {
        return getOptional(AdditionalLocales.ROMANSH);
    }

    public T coalesce(Locale... locales) {
        return coalesceOptional(locales).orElseThrow();
    }

    public T coalesce(String... keys) {
        return coalesceOptional(keys).orElseThrow();
    }

    public T coalesceOrGetAny(Locale... locales) {
        return coalesceOptional(locales).orElseGet(this::getAny);
    }

    public T coalesceOrGetAny(String... keys) {
        return coalesceOptional(keys).orElseGet(this::getAny);
    }

    public Optional<T> coalesceOptional(Locale... locales) {
        for (Locale locale : locales) {
            Optional<T> value = getOptional(locale);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    public Optional<T> coalesceOptional(String... keys) {
        for (String key : keys) {
            Optional<T> value = getOptional(key);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    @JsonIgnore
    public T getAny() {
        return values.values().stream().findAny().orElseThrow(NoSuchElementException::new);
    }

    public Map<Locale, T> toMapWithLocaleKeys() {
        return Map.copyOf(values);
    }

    @JsonValue
    public Map<String, T> toMapWithLanguageTagKeys() {
        return values.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().toLanguageTag(), Map.Entry::getValue));
    }

    public Map<String, T> toMapWithPosixLocaleKeys() {
        return values.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().toString(), Map.Entry::getValue));
    }

    public Map<String, T> toMapWithUppercaseKeys() {
        return values.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().toLanguageTag().toUpperCase(Locale.ROOT),
                        Map.Entry::getValue));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LocalizedValue<?> localizedValue
                && Objects.equals(values, localizedValue.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
