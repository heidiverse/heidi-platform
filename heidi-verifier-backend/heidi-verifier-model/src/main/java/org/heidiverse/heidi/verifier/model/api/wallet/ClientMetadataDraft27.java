// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.api.wallet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record ClientMetadataDraft27(
        @JsonProperty(value = "vp_formats_supported", required = true)
                Map<String, Object> vpFormatsSupported,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty(value = "encrypted_response_enc_values_supported")
                List<String> encryptedResponseEncValuesSupported,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty(value = "jwks") Map<String, Object> jwks)
        implements ClientMetadata {}
