// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.extensions.migration;

/**
 * Describes a database migration bundle supplied by a platform extension.
 *
 * <p>The interface is published independently of the executable web-service
 * module so external extension JARs can compile against it without creating a
 * dependency cycle back to the application.
 */
public interface PlatformApiExtensionMigration {

    /**
     * Stable, lowercase identifier for the extension. It becomes part of the
     * extension's Flyway history table name.
     */
    String extensionId();

    /**
     * Flyway location containing this extension's versioned migrations.
     *
     * <p>Use a classpath location such as
     * {@code classpath:/db/migration/extensions/example/postgresql}.
     */
    String migrationLocation();
}
