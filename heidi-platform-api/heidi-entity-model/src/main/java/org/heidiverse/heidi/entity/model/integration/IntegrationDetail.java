// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.integration;

import java.util.List;
import java.util.UUID;

public record IntegrationDetail(
        UUID id,
        String displayName,
        List<String> credentialIdentifiers,
        List<IntegrationScope> scopes,
        String apiKey,
        String tenantId,
        boolean isPublic) {}
