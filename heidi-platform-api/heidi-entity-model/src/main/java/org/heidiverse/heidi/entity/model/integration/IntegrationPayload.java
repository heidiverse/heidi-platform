// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.integration;

import java.util.List;

public record IntegrationPayload(
        String displayName,
        List<String> credentialIdentifiers,
        List<IntegrationScope> scopes,
        Boolean isPublic // nullable to keep backwards compatible
        ) {}
