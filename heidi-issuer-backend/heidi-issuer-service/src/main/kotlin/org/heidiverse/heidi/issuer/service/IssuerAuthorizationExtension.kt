// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.heidiverse.heidi.issuer.model.FlowVariant
import org.heidiverse.heidi.issuer.model.api.AuthorizationServerMetadata
import org.heidiverse.heidi.issuer.model.api.PushedAuthorizationResponse
import org.heidiverse.heidi.issuer.model.api.TokenResponse
import org.springframework.util.MultiValueMap

/**
 * Optional, deployment-specific authorization-code support for an issuer.
 *
 * The OSS issuer deliberately implements only the pre-authorized-code flow. A hosted distribution
 * can contribute this seam without making the generic issuer depend on its identity provider,
 * persistence, or product policy.
 */
interface IssuerAuthorizationExtension {
    fun supports(
        issuerSlug: String,
        variant: FlowVariant,
        credentialIdentifier: String,
        credentialVersion: String,
    ): Boolean

    fun authorizationMetadata(
        issuerSlug: String,
        variant: FlowVariant,
        credentialIdentifier: String,
        credentialVersion: String,
        base: AuthorizationServerMetadata,
    ): AuthorizationServerMetadata

    fun pushAuthorization(params: MultiValueMap<String, String>): PushedAuthorizationResponse

    fun authorize(params: Map<String, String>): String

    fun token(params: Map<String, String>, dpopProof: String?, httpMethod: String): TokenResponse
}
