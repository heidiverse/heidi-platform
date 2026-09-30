// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.api.wallet;

import org.kapunsdk.presentation.request.model.OID4VPVersion;

import java.util.List;
import java.util.Map;

public sealed interface ClientMetadata permits ClientMetadataPreDraft27, ClientMetadataDraft27 {

    static ClientMetadata from(
            OID4VPVersion OID4VPVersion,
            Map<String, Object> vpFormatsSupported,
            String authEncryptedResponseAlg,
            String authEncryptedResponseEnc,
            Map<String, Object> jwks) {
        return from(
                OID4VPVersion,
                vpFormatsSupported,
                authEncryptedResponseAlg,
                authEncryptedResponseEnc,
                jwks,
                Map.of());
    }

    static ClientMetadata from(
            OID4VPVersion OID4VPVersion,
            Map<String, Object> vpFormatsSupported,
            String authEncryptedResponseAlg,
            String authEncryptedResponseEnc,
            Map<String, Object> jwks,
            Map<String, String> displayMetadata) {
        return from(
                OID4VPVersion,
                vpFormatsSupported,
                authEncryptedResponseAlg,
                authEncryptedResponseEnc,
                authEncryptedResponseEnc == null
                        ? List.of()
                        : List.of(authEncryptedResponseEnc),
                jwks,
                displayMetadata);
    }

    static ClientMetadata from(
            OID4VPVersion OID4VPVersion,
            Map<String, Object> vpFormatsSupported,
            String authEncryptedResponseAlg,
            String authEncryptedResponseEnc,
            List<String> authEncryptedResponseEncValuesSupported,
            Map<String, Object> jwks,
            Map<String, String> displayMetadata) {
        var metadata = displayMetadata == null ? Map.<String, String>of() : displayMetadata;
        var encValues = OID4VPVersion.getVersion() < OID4VPVersion.DRAFT_28.getVersion()
                || authEncryptedResponseEncValuesSupported == null
                ? List.<String>of()
                : List.copyOf(authEncryptedResponseEncValuesSupported);

        if (OID4VPVersion.getVersion() < OID4VPVersion.DRAFT_28.getVersion()) {
            return new ClientMetadataPreDraft27(
                    vpFormatsSupported,
                    authEncryptedResponseAlg,
                    authEncryptedResponseEnc,
                    jwks,
                    metadata.get("client_name"),
                    metadata.get("logo_uri"));
        }

        return new ClientMetadataDraft27(vpFormatsSupported, encValues, jwks);
    }
}
