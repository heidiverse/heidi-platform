// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class TenantLanguagesTest {

    @Test
    void defaultsEmptyLanguagesToEnglish() {
        assertEquals(List.of("en"), TenantLanguages.normalize(List.of()));
        assertEquals(List.of("de"), TenantLanguages.normalize(List.of(), "de"));
    }

    @Test
    void canonicalizesLanguageTags() {
        assertEquals("en-US", TenantLanguages.tag("en-us"));
    }

    @Test
    void rejectsInvalidLanguageTags() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TenantLanguages.tag("en_US"));
        assertThrows(
                IllegalArgumentException.class,
                () -> TenantLanguages.tag("en--US"));
    }

    @Test
    void rejectsUnselectedFallback() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TenantLanguages.fallback(List.of("en"), "de"));
    }
}
