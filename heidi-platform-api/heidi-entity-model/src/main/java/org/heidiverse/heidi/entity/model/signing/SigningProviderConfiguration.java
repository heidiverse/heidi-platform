// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.signing;

import java.util.List;
import org.heidiverse.heidi.shared.signing.SigningKeyCapabilities;

/** Decrypted provider settings and its last published capabilities. */
public record SigningProviderConfiguration(
        String endpoint,
        String bearerToken,
        String authenticationMode,
        String scheme,
        List<String> supportedAlgorithms,
        List<String> digestSigningAlgorithms,
        List<String> supportedOperations,
        List<String> keylessOperations,
        boolean canCreate,
        boolean canImport,
        boolean canDelete,
        List<String> contentKeyAlgorithms) {
    public SigningProviderConfiguration {
        supportedAlgorithms = supportedAlgorithms == null ? List.of() : List.copyOf(supportedAlgorithms);
        digestSigningAlgorithms = digestSigningAlgorithms == null
                ? List.of() : List.copyOf(digestSigningAlgorithms);
        supportedOperations = supportedOperations == null ? List.of() : List.copyOf(supportedOperations);
        keylessOperations = keylessOperations == null ? List.of() : List.copyOf(keylessOperations);
        contentKeyAlgorithms = contentKeyAlgorithms == null ? List.of() : List.copyOf(contentKeyAlgorithms);
    }

    public SigningProviderConfiguration(
            String endpoint, String bearerToken, String authenticationMode,
            String scheme,
            List<String> supportedAlgorithms, List<String> digestSigningAlgorithms,
            List<String> supportedOperations, List<String> keylessOperations,
            boolean canCreate, boolean canImport, boolean canDelete) {
        this(endpoint, bearerToken, authenticationMode, scheme,
                supportedAlgorithms, digestSigningAlgorithms, supportedOperations, keylessOperations,
                canCreate, canImport, canDelete, List.of());
    }

    public SigningProviderConfiguration(String endpoint, String bearerToken) {
        this(endpoint, bearerToken, null, null, List.of(), List.of(), List.of(), List.of(), false, false, false);
    }

    public SigningProviderConfiguration(
            String endpoint, String bearerToken, String authenticationMode) {
        this(endpoint, bearerToken, authenticationMode,
                null, List.of(), List.of(), List.of(), List.of(), false, false, false);
    }

    public SigningProviderConfiguration(
            String endpoint, String bearerToken, String authenticationMode,
            String scheme,
            List<String> supportedAlgorithms, List<String> digestSigningAlgorithms,
            List<String> supportedOperations) {
        this(endpoint, bearerToken, authenticationMode, scheme,
                supportedAlgorithms, digestSigningAlgorithms, supportedOperations, List.of(),
                false, false, false);
    }

    public SigningProviderConfiguration withCapabilities(SigningKeyCapabilities capabilities) {
        return new SigningProviderConfiguration(
                endpoint, bearerToken, authenticationMode,
                capabilities.scheme(), capabilities.supportedAlgorithms(),
                capabilities.digestSigningAlgorithms(), capabilities.supportedOperations(),
                capabilities.keylessOperations(),
                capabilities.canCreate(), capabilities.canImport(), capabilities.canDelete(),
                capabilities.contentKeyAlgorithms());
    }
}
