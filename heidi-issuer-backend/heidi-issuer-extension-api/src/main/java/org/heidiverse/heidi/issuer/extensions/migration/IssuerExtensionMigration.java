// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.extensions.migration;

/** Describes a database migration bundle supplied by an issuer extension. */
public interface IssuerExtensionMigration {

    /** Stable lowercase identifier used in the extension history table name. */
    String extensionId();

    /** Flyway location containing this extension's versioned migrations. */
    String migrationLocation();
}
