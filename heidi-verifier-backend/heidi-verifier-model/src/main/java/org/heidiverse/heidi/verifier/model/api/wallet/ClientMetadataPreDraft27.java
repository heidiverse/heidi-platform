// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.api.wallet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record ClientMetadataPreDraft27(
        @JsonProperty(value = "vp_formats", required = true) Map<String, Object> vpFormats,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty(value = "authorization_encrypted_response_alg")
                String authEncryptedResponseAlg,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty(value = "authorization_encrypted_response_enc")
                String authEncryptedResponseEnc,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty(value = "jwks") Map<String, Object> jwks,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) @JsonProperty(value = "client_name")
                String clientName,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) @JsonProperty(value = "logo_uri")
                String logoUri)
        implements ClientMetadata {}
