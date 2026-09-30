// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.ws.extensions.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.heidiverse.heidi.issuer.extensions.migration.IssuerExtensionMigration;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Runs issuer extension migrations after the core issuer migrations. */
@Component
public class IssuerExtensionMigrationCallback implements Callback {

    private static final Pattern EXTENSION_ID = Pattern.compile("[a-z0-9]+(?:[_-][a-z0-9]+)*");

    private final List<IssuerExtensionMigration> migrations;

    public IssuerExtensionMigrationCallback(List<IssuerExtensionMigration> migrations) {
        migrations.forEach(IssuerExtensionMigrationCallback::validate);
        this.migrations =
                migrations.stream()
                        .sorted(Comparator.comparing(IssuerExtensionMigration::extensionId))
                        .toList();

        Set<String> historyTables = new HashSet<>();
        for (IssuerExtensionMigration migration : this.migrations) {
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

        for (IssuerExtensionMigration migration : migrations) {
            Flyway extensionFlyway =
                    new FluentConfiguration(context.getConfiguration().getClassLoader())
                            .configuration(context.getConfiguration())
                            .dataSource(context.getConfiguration().getDataSource())
                            .locations(migration.migrationLocation())
                            .table(historyTable(migration.extensionId()))
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
        return "Heidi issuer extension migrations";
    }

    static String historyTable(String extensionId) {
        validateExtensionId(extensionId);
        return "flyway_schema_history_ext_" + extensionId.replace('-', '_');
    }

    private static void validate(IssuerExtensionMigration migration) {
        if (migration == null) {
            throw new IllegalArgumentException("Extension migration must not be null");
        }
        validateExtensionId(migration.extensionId());
        if (migration.migrationLocation() == null || migration.migrationLocation().isBlank()) {
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
