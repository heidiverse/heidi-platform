// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.credentialscheme;

import tools.jackson.databind.JsonNode;

import java.util.Map;

public record SchemaInformation(
        SchemaIdentifiers metadata,
        Map<String, SchemaCreationRequest.AttributeDetails> attributes,
        JsonNode style) {}
