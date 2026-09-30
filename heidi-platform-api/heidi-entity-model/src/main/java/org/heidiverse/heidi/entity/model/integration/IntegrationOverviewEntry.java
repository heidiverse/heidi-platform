// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.integration;

import java.util.List;
import java.util.UUID;

public record IntegrationOverviewEntry(
        UUID id,
        String displayName,
        List<String> credentialIdentifiers,
        List<IntegrationScope> scopes,
        String tenantId,
        boolean isPublic) {}
