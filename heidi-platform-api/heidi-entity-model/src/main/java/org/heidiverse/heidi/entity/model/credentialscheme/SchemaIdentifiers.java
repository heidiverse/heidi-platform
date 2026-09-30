// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.credentialscheme;

import java.time.Instant;

public record SchemaIdentifiers(
        String credentialIdentifier,
        String version,
        boolean published,
        String textColor,
        String cardColor,
        String ocaBundleFileName,
        Instant createdAt,
        Instant updatedAt) {}
