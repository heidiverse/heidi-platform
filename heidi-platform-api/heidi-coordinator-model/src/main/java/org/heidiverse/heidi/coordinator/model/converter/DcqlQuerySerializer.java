// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.converter;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import uniffi.kapun_dcql_rust.DcqlQuery;

public class DcqlQuerySerializer extends ValueSerializer<DcqlQuery> {
    @Override
    public void serialize(
            DcqlQuery dcqlQuery,
            JsonGenerator jsonGenerator,
            SerializationContext serializerProvider) {
        final var json = org.kapunsdk.DcqlQuerySerializer.toJson(dcqlQuery);
        jsonGenerator.writeRawValue(json);
    }
}
