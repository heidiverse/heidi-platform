// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.credentialscheme;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.util.Map;

public record SchemaInformationResponse(
        SchemaIdentifiers metadata,
        Map<String, SchemaCreationRequest.AttributeDetails> attributes,
        @JsonRawValue String style,
        @JsonRawValue String ocaBundle) {}
