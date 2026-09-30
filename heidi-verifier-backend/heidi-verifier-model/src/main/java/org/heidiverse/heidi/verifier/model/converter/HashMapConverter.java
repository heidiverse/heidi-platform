// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.converter;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Converter
public class HashMapConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final Logger logger = LoggerFactory.getLogger(HashMapConverter.class);

    private final ObjectMapper objectMapper;

    public HashMapConverter(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convertToDatabaseColumn(final Map<String, Object> genericMap) {
        String genericJson = null;

        try {
            genericJson = objectMapper.writeValueAsString(genericMap);
        } catch (final JacksonException e) {
            logger.error("Error serializing hashmap to JSON", e);
        }

        return genericJson;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> convertToEntityAttribute(final String json) {

        Map<String, Object> genericMap = null;
        try {
            genericMap =
                    objectMapper.readValue(json, new TypeReference<HashMap<String, Object>>() {});
            var collapse = collapse(genericMap);
            if (collapse instanceof Map<?, ?> obj) {
                genericMap = (Map<String, Object>) obj;
            }
        } catch (final JacksonException e) {
            logger.error("Error deserializing hashmap to JSON", e);
        }

        return genericMap;
    }

    private Object collapse(final Object obj) {
        if (!(obj instanceof Map<?, ?> map)) {
            return obj;
        }

        final Map<String, Object> collapsed = new HashMap<>();
        for (final var entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                return null;
            }
            final Object value = collapse(entry.getValue());
            if ("v1".equals(key)) {
                return value;
            }
            collapsed.put(key, value);
        }
        return collapsed;
    }
}
