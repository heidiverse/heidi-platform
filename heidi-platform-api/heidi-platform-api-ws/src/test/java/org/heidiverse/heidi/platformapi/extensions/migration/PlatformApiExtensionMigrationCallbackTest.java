// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.extensions.migration;

import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlatformApiExtensionMigrationCallbackTest {

    @Test
    void usesAnExtensionSpecificHistoryTable() {
        assertEquals(
                "flyway_schema_history_ext_sample_extension",
                PlatformApiExtensionMigrationCallback.historyTable("sample-extension"));
    }

    @Test
    void rejectsUnsafeExtensionIds() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PlatformApiExtensionMigrationCallback(
                                List.of(migration("Sample Extension"))));
    }

    @Test
    void rejectsHistoryTableCollisions() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PlatformApiExtensionMigrationCallback(
                                List.of(
                                        migration("sample-extension"),
                                        migration("sample_extension"))));
    }

    @Test
    void onlyRunsAfterCoreMigration() {
        var callback = new PlatformApiExtensionMigrationCallback(List.of());

        org.junit.jupiter.api.Assertions.assertTrue(callback.supports(Event.AFTER_MIGRATE, null));
        org.junit.jupiter.api.Assertions.assertFalse(callback.supports(Event.BEFORE_MIGRATE, null));
    }

    private static PlatformApiExtensionMigration migration(String extensionId) {
        return new PlatformApiExtensionMigration() {
            @Override
            public String extensionId() {
                return extensionId;
            }

            @Override
            public String migrationLocation() {
                return "classpath:/db/migration/extensions/" + extensionId;
            }
        };
    }
}
