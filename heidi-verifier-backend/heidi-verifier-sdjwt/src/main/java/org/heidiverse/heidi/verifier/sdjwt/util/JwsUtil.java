// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.util;

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.jwk.JWK;

/** JWS validation backed by kapun (Rust), with optional type checks. */
public final class JwsUtil {

    private JwsUtil() {}

    public static boolean verify(JWSObject jws, JWK jwk) {
        return verify(jws.getParsedString(), jwk);
    }

    public static boolean verify(String compact, JWK jwk) {
        return verify(
                compact,
                jwk,
                java.util.List.of(
                        "RS256", "RS384", "RS512", "PS256", "PS384", "PS512", "ES256", "ES384", "ES512"),
                java.util.List.of());
    }

    public static boolean verify(
            String compact, JWK jwk, java.util.List<String> supportedAlgorithms, java.util.List<String> types) {
        var jwt = KapunJwtUtil.parse(compact);
        return KapunJwtUtil.validate(jwt, jwk, supportedAlgorithms, types);
    }

    public static boolean verifyKbJwt(String compact, JWK jwk) {
        var jwt = KapunJwtUtil.parse(compact);
        try {
            return uniffi.kapun_crypto_rust.Kapun_crypto_rust_jvmKt.validateKbJwtWithJwk(
                    compact, KapunJwtUtil.key(jwk, KapunJwtUtil.header(jwt)));
        } catch (java.text.ParseException exception) {
            return false;
        }
    }
}
