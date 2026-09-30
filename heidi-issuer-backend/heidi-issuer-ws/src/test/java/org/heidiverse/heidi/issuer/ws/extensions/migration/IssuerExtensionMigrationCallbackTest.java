// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.ws.extensions.migration;

import org.heidiverse.heidi.issuer.extensions.migration.IssuerExtensionMigration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssuerExtensionMigrationCallbackTest {

    @Test
    void usesAnExtensionSpecificHistoryTable() {
        assertEquals(
                "flyway_schema_history_ext_hosted_issuer",
                IssuerExtensionMigrationCallback.historyTable("hosted-issuer"));
    }

    @Test
    void rejectsUnsafeExtensionIds() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new IssuerExtensionMigrationCallback(
                                List.of(migration("Hosted Issuer"))));
    }

    @Test
    void rejectsHistoryTableCollisions() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new IssuerExtensionMigrationCallback(
                                List.of(
                                        migration("hosted-issuer"),
                                        migration("hosted_issuer"))));
    }

    @Test
    void onlyRunsAfterCoreMigration() {
        var callback = new IssuerExtensionMigrationCallback(List.of());

        org.junit.jupiter.api.Assertions.assertTrue(
                callback.supports(org.flywaydb.core.api.callback.Event.AFTER_MIGRATE, null));
        org.junit.jupiter.api.Assertions.assertFalse(
                callback.supports(org.flywaydb.core.api.callback.Event.BEFORE_MIGRATE, null));
    }

    private static IssuerExtensionMigration migration(String extensionId) {
        return new IssuerExtensionMigration() {
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
