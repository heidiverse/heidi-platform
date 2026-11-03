// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service.bbs

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.heidiverse.heidi.issuer.service.CredentialData
import org.heidiverse.heidi.issuer.service.IssuerConfigurationClient
import org.heidiverse.heidi.issuer.service.IssuerFlowSnapshot
import org.heidiverse.heidi.issuer.service.IssuerProperties
import org.heidiverse.heidi.issuer.service.SigningProviderClient
import org.heidiverse.heidi.shared.signing.SigningKeyRef
import org.heidiverse.heidi.shared.signing.SigningOperationRequest
import org.heidiverse.heidi.shared.signing.SigningOperationResult
import org.heidiverse.heidi.signing.adapters.RemoteSigningKeyProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Base64

class BbsCredentialIssuerTest {
    @Test
    fun `omits device binding without a holder key`() {
        assertFalse(input(null).containsKey("deviceBinding"))
    }

    @Test
    fun `passes holder coordinates to the provider`() {
        val holder = ECKeyGenerator(Curve.P_256).generate().toPublicJWK()

        val binding = input(holder.toJSONString()).getValue("deviceBinding").jsonObject

        assertEquals(Base64.getEncoder().encodeToString(holder.x.decode()), binding.getValue("x").jsonPrimitive.content)
        assertEquals(Base64.getEncoder().encodeToString(holder.y.decode()), binding.getValue("y").jsonPrimitive.content)
    }

    private fun input(holderJwk: String?): JsonObject {
        val providerClient = mock(SigningProviderClient::class.java)
        val provider = mock(RemoteSigningKeyProvider::class.java)
        val signing = IssuerProperties.IssuerSigning(
            keyId = "bbs-key",
            keyUri = "software://bbs-key",
            algorithm = "BBS",
        )
        val ref = SigningKeyRef("software://bbs-key", "{}", "BBS")
        `when`(providerClient.resolve(signing, "acme", signing.keyUri))
            .thenReturn(SigningProviderClient.ResolvedKey(provider, ref))
        `when`(provider.execute(eq(ref), any(SigningOperationRequest::class.java)))
            .thenReturn(SigningOperationResult("COMPLETED", """{"credential":"bbs-credential"}""", null, null))
        val issuer = BbsCredentialIssuer(mock(IssuerConfigurationClient::class.java), providerClient)

        val credential = issuer.issue(
            CredentialData("diploma", "1.0.0", "acme", emptyMap()),
            "acme",
            "urn:example:diploma",
            holderJwk,
            IssuerFlowSnapshot(operationSigning = signing),
        )

        assertEquals("bbs-credential", credential)
        val request = ArgumentCaptor.forClass(SigningOperationRequest::class.java)
        verify(provider).execute(eq(ref), request.capture())
        assertEquals(IssuerFlowSnapshot.BBS_OPERATION, request.value.operation())
        return Json.parseToJsonElement(request.value.inputJson()).jsonObject
    }
}
