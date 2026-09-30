// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.util;

import com.nimbusds.jose.jwk.JWK;

import org.kapunsdk.crypto.jwt.Jwt;
import org.kapunsdk.crypto.jwt.JwtValidator;
import org.kapunsdk.util.extensions.ValueExtensionKt;

import uniffi.kapun_util_rust.Value;

import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared adapter from Kapun's JWT JSON view to the Nimbus types used by this module. */
public final class KapunJwtUtil {
    private KapunJwtUtil() {}

    public static Jwt parse(String compact) {
        return new Jwt(compact, new JwtValidator(), System.currentTimeMillis());
    }

    public static Map<String, Object> header(Jwt jwt) throws ParseException {
        return object(jwt.getHeader(), "JWT header");
    }

    public static Map<String, Object> payload(Jwt jwt) throws ParseException {
        return object(jwt.insecureGetPayload(), "JWT payload");
    }

    public static Value key(JWK jwk, Map<String, Object> header) {
        var json = new HashMap<>(jwk.toPublicJWK().toJSONObject());
        json.put("alg", header.get("alg"));
        return ValueExtensionKt.toPlainValue(json);
    }

    public static boolean validate(
            Jwt jwt, JWK jwk, List<String> supportedAlgorithms, List<String> types) {
        var header = headerOrNull(jwt);
        if (header == null || !supportedAlgorithms.contains(header.get("alg"))) return false;

        var key = key(jwk, header);
        if (types.isEmpty()) return jwt.validateJwt(key);
        return types.stream().anyMatch(type -> jwt.validateJwtWithType(type, key));
    }

    private static Map<String, Object> headerOrNull(Jwt jwt) {
        try {
            return header(jwt);
        } catch (ParseException ignored) {
            return null;
        }
    }

    private static Map<String, Object> object(
            kotlinx.serialization.json.JsonElement element, String description)
            throws ParseException {
        if (!(element instanceof kotlinx.serialization.json.JsonObject)) {
            throw new ParseException(description + " is not a JSON object", 0);
        }
        var plain = ValueExtensionKt.toPlainObject(
                ValueExtensionKt.fromJsonElement(Value.Companion, element));
        if (!(plain instanceof Map<?, ?> map)) {
            throw new ParseException("Invalid " + description, 0);
        }
        @SuppressWarnings("unchecked")
        var result = (Map<String, Object>) map;
        return result;
    }
}
