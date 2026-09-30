// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.localized;

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;

/**
 * Exposes the values contained in a {@link LocalizedValue} to Jakarta Bean Validation.
 *
 * <p>{@code LocalizedValue} is a custom generic container, so Hibernate Validator cannot
 * validate constraints declared on its type argument unless an extractor is registered.
 */
public final class LocalizedValueValueExtractor
        implements ValueExtractor<LocalizedValue<@ExtractedValue ?>> {

    @Override
    public void extractValues(LocalizedValue<?> originalValue, ValueReceiver receiver) {
        if (originalValue == null) {
            return;
        }

        originalValue.toMapWithLocaleKeys().forEach(
                (locale, value) -> receiver.keyedValue("<localized value>", locale, value));
    }
}
