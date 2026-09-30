// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.signing;

import java.util.List;

/** Public provider metadata; connection secrets are never returned. */
public record SigningProviderResponse(
        Integer id,
        String name,
        String scheme,
        String endpoint,
        String authenticationMode,
        boolean defaultProvider,
        List<String> supportedAlgorithms,
        List<String> digestSigningAlgorithms,
        List<String> supportedOperations,
        List<String> keylessOperations,
        boolean canCreate,
        boolean canImport,
        boolean canDelete,
        String scope,
        String tenantId,
        List<String> contentKeyAlgorithms) {
    public SigningProviderResponse(
            Integer id, String name, String scheme, String endpoint,
            String authenticationMode, boolean defaultProvider,
            List<String> supportedAlgorithms, List<String> digestSigningAlgorithms,
            List<String> supportedOperations, List<String> keylessOperations,
            boolean canCreate, boolean canImport, boolean canDelete,
            String scope, String tenantId) {
        this(id, name, scheme, endpoint, authenticationMode, defaultProvider,
                supportedAlgorithms, digestSigningAlgorithms, supportedOperations,
                keylessOperations, canCreate, canImport, canDelete, scope, tenantId, List.of());
    }

    public SigningProviderResponse {
        supportedAlgorithms = supportedAlgorithms == null ? List.of() : List.copyOf(supportedAlgorithms);
        digestSigningAlgorithms = digestSigningAlgorithms == null
                ? List.of() : List.copyOf(digestSigningAlgorithms);
        supportedOperations = supportedOperations == null ? List.of() : List.copyOf(supportedOperations);
        keylessOperations = keylessOperations == null ? List.of() : List.copyOf(keylessOperations);
        contentKeyAlgorithms = contentKeyAlgorithms == null ? List.of() : List.copyOf(contentKeyAlgorithms);
    }
}
