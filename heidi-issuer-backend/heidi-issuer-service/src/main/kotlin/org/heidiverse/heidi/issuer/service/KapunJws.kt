// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKParameterNames
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kapunsdk.crypto.jwt.Jwt
import org.kapunsdk.crypto.jwt.JwtValidator
import org.kapunsdk.util.extensions.toPlainValue

/**
 * JWS validation backed by kapun (Rust). Type checking is optional.
 * The JWK's use and key_ops metadata remains available to Kapun's verifier.
 */
internal fun String.verifyWith(jwk: JWK, type: String? = null): Boolean {
    val jwt = Jwt(this, JwtValidator(), System.currentTimeMillis())
    val header = runCatching { jwt.getHeader().jsonObject }.getOrNull() ?: return false
    val json = jwk.toPublicJWK().toJSONObject().toMutableMap()
    json[JWKParameterNames.ALGORITHM] = header[JWKParameterNames.ALGORITHM]?.jsonPrimitive?.content

    val key = json.toPlainValue()
    return if (type == null) jwt.validateJwt(key) else jwt.validateJwtWithType(type, key)
}
