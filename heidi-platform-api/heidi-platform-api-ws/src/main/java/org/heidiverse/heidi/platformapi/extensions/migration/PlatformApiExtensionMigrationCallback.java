// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.extensions.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Runs extension migrations as part of the core Flyway lifecycle.
 *
 * <p>Using a callback keeps extension migrations before Hibernate validation,
 * while separate Flyway history tables keep version numbers independent across
 * extensions.
 */
@Component
public class PlatformApiExtensionMigrationCallback implements Callback {

    private static final Pattern EXTENSION_ID = Pattern.compile("[a-z0-9]+(?:[_-][a-z0-9]+)*");

    private final List<PlatformApiExtensionMigration> migrations;

    public PlatformApiExtensionMigrationCallback(
            List<PlatformApiExtensionMigration> migrations) {
        migrations.forEach(PlatformApiExtensionMigrationCallback::validate);
        this.migrations =
                migrations.stream()
                        .sorted(Comparator.comparing(PlatformApiExtensionMigration::extensionId))
                        .toList();
        Set<String> historyTables = new HashSet<>();
        for (PlatformApiExtensionMigration migration : this.migrations) {
            if (!historyTables.add(historyTable(migration.extensionId()))) {
                throw new IllegalArgumentException(
                        "Duplicate extension migration id: " + migration.extensionId());
            }
        }
    }

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.AFTER_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return false;
    }

    @Override
    public void handle(Event event, Context context) {
        if (event != Event.AFTER_MIGRATE) {
            return;
        }

        for (PlatformApiExtensionMigration migration : migrations) {
            Flyway extensionFlyway =
                    new FluentConfiguration(context.getConfiguration().getClassLoader())
                            .configuration(context.getConfiguration())
                            .dataSource(context.getConfiguration().getDataSource())
                            .locations(migration.migrationLocation())
                            .table(historyTable(migration.extensionId()))
                            // The core Flyway run has already created tables in
                            // public, so every newly installed extension sees a
                            // non-empty schema before its own history table exists.
                            // Baseline at zero so V0_1_0 extension migrations are
                            // still applied on existing databases.
                            .baselineOnMigrate(true)
                            .baselineVersion("0")
                            .callbacks(new Callback[0])
                            .skipDefaultCallbacks(true)
                            .failOnMissingLocations(true)
                            .load();
            extensionFlyway.migrate();
        }
    }

    @Override
    public String getCallbackName() {
        return "Heidi Platform API extension migrations";
    }

    static String historyTable(String extensionId) {
        validateExtensionId(extensionId);
        return "flyway_schema_history_ext_" + extensionId.replace('-', '_');
    }

    private static void validate(PlatformApiExtensionMigration migration) {
        if (migration == null) {
            throw new IllegalArgumentException("Extension migration must not be null");
        }
        validateExtensionId(migration.extensionId());
        if (migration.migrationLocation() == null
                || migration.migrationLocation().isBlank()) {
            throw new IllegalArgumentException(
                    "Migration location must be configured for extension "
                            + migration.extensionId());
        }
    }

    private static void validateExtensionId(String extensionId) {
        if (extensionId == null || !EXTENSION_ID.matcher(extensionId).matches()) {
            throw new IllegalArgumentException(
                    "Extension migration id must contain only lowercase letters, numbers, "
                            + "hyphens, and underscores: " + extensionId);
        }
    }
}
