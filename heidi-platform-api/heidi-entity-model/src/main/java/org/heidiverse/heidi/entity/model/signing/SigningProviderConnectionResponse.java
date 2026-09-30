// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.signing;

import java.util.List;

/** Capabilities returned by a signing provider connection check; no configuration is persisted. */
public record SigningProviderConnectionResponse(
        String scheme,
        String endpoint,
        String authenticationMode,
        List<String> supportedAlgorithms,
        List<String> digestSigningAlgorithms,
        List<String> supportedOperations,
        List<String> keylessOperations,
        boolean canCreate,
        boolean canImport,
        boolean canDelete,
        /** How this service accepts clients: allow-list, platform-assisted, or null if it predates them. */
        String clientAcceptance,
        /** Each client of this service: known to it, or waiting with the public key to add. */
        List<ClientStatus> clients,
        /** JWE key-management algorithms supported by the provider. */
        List<String> contentKeyAlgorithms) {

    public SigningProviderConnectionResponse(
            String scheme, String endpoint, String authenticationMode,
            List<String> supportedAlgorithms, List<String> digestSigningAlgorithms,
            List<String> supportedOperations, List<String> keylessOperations,
            boolean canCreate, boolean canImport, boolean canDelete,
            String clientAcceptance, List<ClientStatus> clients) {
        this(scheme, endpoint, authenticationMode, supportedAlgorithms,
                digestSigningAlgorithms, supportedOperations, keylessOperations,
                canCreate, canImport, canDelete, clientAcceptance, clients, List.of());
    }

    public SigningProviderConnectionResponse {
        supportedAlgorithms = supportedAlgorithms == null ? List.of() : List.copyOf(supportedAlgorithms);
        digestSigningAlgorithms = digestSigningAlgorithms == null
                ? List.of() : List.copyOf(digestSigningAlgorithms);
        supportedOperations = supportedOperations == null ? List.of() : List.copyOf(supportedOperations);
        keylessOperations = keylessOperations == null ? List.of() : List.copyOf(keylessOperations);
        clients = clients == null ? List.of() : List.copyOf(clients);
        contentKeyAlgorithms = contentKeyAlgorithms == null ? List.of() : List.copyOf(contentKeyAlgorithms);
    }

    public record ClientStatus(
            String name, boolean known, boolean registered, java.time.Instant lastUse,
            String publicKey) {
        public ClientStatus(String name, boolean known, String publicKey) {
            this(name, known, false, null, publicKey);
        }
    }
}
