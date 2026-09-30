// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.converter;

import org.kapunsdk.DcqlQuerySerializer;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import uniffi.kapun_dcql_rust.DcqlQuery;

public class DcqlQueryDeserializer extends ValueDeserializer<DcqlQuery> {
    @Override
    public DcqlQuery deserialize(
            JsonParser jsonParser, DeserializationContext deserializationContext) {
        final var node = jsonParser.readValueAsTree();
        final var json = node.toString();
        return DcqlQuerySerializer.fromJson(json);
    }
}
