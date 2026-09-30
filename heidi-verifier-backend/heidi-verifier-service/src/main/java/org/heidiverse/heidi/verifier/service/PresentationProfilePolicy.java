// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service;

import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;

import java.util.List;

/** Runtime presentation policy selected by the immutable profile ID on a request. */
public record PresentationProfilePolicy(
        String clientIdScheme,
        String responseMode,
        String responseEncryptionAlg,
        List<String> responseEncryptionAlgs,
        String responseEncryptionEnc,
        List<String> responseEncryptionEncs) {

    private static final String EUDI = "EUDI_PRESENTATION_2026_1";
    private static final String SWISS = "SWISS_PRESENTATION_2026_1";
    private static final String OIDF = "OIDF_PRESENTATION_2026_1";
    private static final String CUSTOM = "CUSTOM_PRESENTATION_2026_1";

    public PresentationProfilePolicy {
        responseEncryptionAlgs = List.copyOf(responseEncryptionAlgs);
        responseEncryptionEncs = List.copyOf(responseEncryptionEncs);
    }

    public static PresentationProfilePolicy resolve(String profileId) {
        if (profileId == null || profileId.isBlank()) {
            throw new VpVerificationException("Presentation profile is required");
        }
        return switch (profileId) {
            case EUDI -> new PresentationProfilePolicy(
                    "x509_hash", "direct_post.jwt", "ECDH-ES", List.of("ECDH-ES"),
                    "A256GCM", List.of("A128GCM", "A256GCM"));
            case SWISS -> new PresentationProfilePolicy(
                    "decentralized_identifier", "direct_post.jwt", "ECDH-ES", List.of("ECDH-ES"),
                    "A256GCM", List.of("A256GCM"));
            case OIDF -> new PresentationProfilePolicy(
                    "openid_federation", "direct_post.jwt", "ECDH-ES", List.of("ECDH-ES"),
                    "A256GCM", List.of("A128GCM", "A256GCM"));
            case CUSTOM -> new PresentationProfilePolicy(
                    "x509_san_dns", "direct_post", "ECDH-ES", List.of("ECDH-ES"),
                    "A256GCM", List.of("A128GCM", "A256GCM"));
            default -> throw new VpVerificationException(
                    "Unsupported presentation profile: " + profileId);
        };
    }
}
