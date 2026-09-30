// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service;

import org.kapunsdk.util.extensions.ValueExtensionKt;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

import uniffi.kapun_util_rust.Value;
import uniffi.heidi_expression.PossumExpression;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PossumValidationService {
    private final ObjectMapper objectMapper;

    public PossumValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean validate(String validationLogic, Map<String, Object> disclosures) {
        if (validationLogic == null || validationLogic.isBlank()) {
            throw new IllegalArgumentException("Possum validation logic must not be empty");
        }
        var result =
                PossumExpression.Companion.fromStr(validationLogic)
                        .evaluate(objectMapper.writeValueAsString(toPlainObject(disclosures)));
        return result.isTruthy();
    }

    private Object toPlainObject(Object value) {
        if (value instanceof Value kapunValue) {
            return ValueExtensionKt.toPlainObject(kapunValue);
        }
        if (value instanceof Map<?, ?> map) {
            final Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach(
                    (key, entryValue) ->
                            normalized.put(String.valueOf(key), toPlainObject(entryValue)));
            return normalized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::toPlainObject).toList();
        }
        return value;
    }
}
