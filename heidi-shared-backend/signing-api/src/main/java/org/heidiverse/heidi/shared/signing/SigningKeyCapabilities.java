// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

import java.util.List;

/**
 * What a provider can do, as a value.
 *
 * <p>Static providers derive capabilities from their optional interfaces. Dynamic providers, such
 * as the HTTP adapter, implement {@link SigningKeyCapabilitiesSource} so the remote capability
 * response remains authoritative. Code that acts on a capability must check this value before
 * invoking an optional operation; the Java interface alone is not sufficient for a dynamic
 * provider.
 */
public record SigningKeyCapabilities(
        String scheme,
        boolean canCreate,
        boolean canImport,
        boolean canDelete,
        List<String> supportedAlgorithms,
        List<String> digestSigningAlgorithms,
        List<String> supportedOperations,
        List<String> keylessOperations,
        List<String> contentKeyAlgorithms) {

    public SigningKeyCapabilities {
        supportedAlgorithms = supportedAlgorithms == null ? List.of() : List.copyOf(supportedAlgorithms);
        digestSigningAlgorithms = digestSigningAlgorithms == null
                ? List.of() : List.copyOf(digestSigningAlgorithms);
        supportedOperations = supportedOperations == null ? List.of() : List.copyOf(supportedOperations);
        keylessOperations = keylessOperations == null ? List.of() : List.copyOf(keylessOperations);
        contentKeyAlgorithms = contentKeyAlgorithms == null
                ? List.of() : List.copyOf(contentKeyAlgorithms);
    }

    public SigningKeyCapabilities(
            String scheme,
            boolean canCreate,
            boolean canImport,
            boolean canDelete,
            List<String> supportedAlgorithms,
            List<String> digestSigningAlgorithms,
            List<String> supportedOperations) {
        this(scheme, canCreate, canImport, canDelete, supportedAlgorithms,
                digestSigningAlgorithms, supportedOperations, List.of(), List.of());
    }

    public SigningKeyCapabilities(
            String scheme,
            boolean canCreate,
            boolean canImport,
            boolean canDelete,
            List<String> supportedAlgorithms,
            List<String> digestSigningAlgorithms,
            List<String> supportedOperations,
            List<String> keylessOperations) {
        this(scheme, canCreate, canImport, canDelete, supportedAlgorithms,
                digestSigningAlgorithms, supportedOperations, keylessOperations, List.of());
    }

    public static SigningKeyCapabilities of(SigningKeyProvider provider) {
        if (provider instanceof SigningKeyCapabilitiesSource source) {
            return source.capabilities();
        }
        return new SigningKeyCapabilities(
                provider.scheme(),
                provider instanceof SigningKeyCreator,
                provider instanceof SigningKeyImporter,
                provider instanceof SigningKeyDeleter,
                List.copyOf(provider.supportedAlgorithms()),
                List.copyOf(provider.digestSigningAlgorithms()),
                provider instanceof SigningOperationProvider operations
                        ? List.copyOf(operations.supportedOperations()) : List.of(),
                provider instanceof SigningOperationProvider operations
                        ? List.copyOf(operations.keylessOperations()) : List.of(),
                provider instanceof SigningContentKeyProvider content
                        ? List.copyOf(content.contentKeyAlgorithms()) : List.of());
    }

    /** The algorithms an issuer may actually be offered: what the platform allows, that this can do. */
    public List<String> offerableAlgorithms(List<String> platformSupported) {
        return platformSupported.stream().filter(supportedAlgorithms::contains).toList();
    }
}
