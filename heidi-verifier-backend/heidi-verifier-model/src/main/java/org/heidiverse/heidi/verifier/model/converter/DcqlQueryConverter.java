// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.converter;

import org.kapunsdk.DcqlQuerySerializer;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uniffi.kapun_dcql_rust.DcqlQuery;

@Converter
public class DcqlQueryConverter
        implements AttributeConverter<DcqlQuery, String> {

    private static final Logger logger =
            LoggerFactory.getLogger(DcqlQueryConverter.class);

    @Override
    public String convertToDatabaseColumn(DcqlQuery dcqlQuery) {

        if (dcqlQuery == null) {
            logger.info("No DCQL query found, bailing out of DB serialization. (This is intended when performing PEX Presentations)");
            return null;
        }

        String dcqlQueryJson = null;
        try {
            dcqlQueryJson = DcqlQuerySerializer.toJson(dcqlQuery);
        } catch (final Exception e) {
            logger.error("Error serializing dcql query to JSON", e);
        }

        return dcqlQueryJson;
    }

    @Override
    public DcqlQuery convertToEntityAttribute(String json) {

        if (json == null) {
            logger.info("No DCQL query found, bailing out of DB deserialization. (This is intended when performing PEX Presentations)");
            return null;
        }

        DcqlQuery dcqlQuery = null;
        try {
            dcqlQuery = DcqlQuerySerializer.fromJson(json);
        } catch (final Exception e) {
            logger.error("Error deserializing JSON to dcql query", e);
        }

        return dcqlQuery;
    }
}
