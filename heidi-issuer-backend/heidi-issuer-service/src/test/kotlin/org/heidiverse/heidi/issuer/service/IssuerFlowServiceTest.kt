// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.issuer.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.heidiverse.heidi.issuer.model.TrustSystem
import org.heidiverse.heidi.issuer.service.SchemaMetadataService.CredentialFormat
import org.heidiverse.heidi.shared.signing.SigningPurpose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import java.util.UUID

class IssuerFlowServiceTest {
    @Test
    fun `pins issuance profile in the flow snapshot`() {
        val snapshot = IssuerFlowSnapshot(issuanceProfileId = "SWISS_ISSUANCE_2026_1")

        assertEquals("SWISS_ISSUANCE_2026_1", IssuerFlowSnapshot.decode(snapshot.encode()).issuanceProfileId)
    }

    @Test
    fun `explicit issuer flow snapshots its profile and encryption policy`() {
        val client = mock(IssuerConfigurationClient::class.java)
        val encryption = CredentialEncryptionPolicy(
            responseEncValues = listOf("A128GCM", "A256GCM"),
            responseEncryptionRequired = true,
        )

        val snapshot = IssuerFlowService(client).capture(
            "partner", TrustSystem.EUDI, "card", "1",
            setOf(CredentialFormat.SD_JWT), encryption, "EUDI_ISSUANCE_2026_1",
        )

        assertEquals("EUDI_ISSUANCE_2026_1", snapshot.issuanceProfileId)
        assertEquals("2026.1", snapshot.issuanceProfileVersion)
        assertEquals("2026.1", IssuerFlowSnapshot.decode(snapshot.encode()).issuanceProfileVersion)
        assertEquals(encryption, snapshot.encryption)
        verify(client).resolve(
            "partner", TrustSystem.EUDI, "card", "1", "EUDI_ISSUANCE_2026_1",
        )
    }

    @Test
    fun `persisted snapshot survives renewal and operation changes`() {
        val client = mock(IssuerConfigurationClient::class.java)
        val signing = IssuerProperties.IssuerSigning(keyId = "key", keyUri = "software://kc/key/v1",
            certificateChain = listOf("original-chain"))
        val operation = signing.copy(keyUri = "software://kc/bbs/v1", algorithm = "BBS")
        `when`(client.resolve("identity", TrustSystem.EUDI, "card", "1", "EUDI_ISSUANCE_2026_1"))
            .thenReturn(IssuerConfigurationClient.RuntimeSigningConfiguration(TrustSystem.EUDI, "original-issuer", signing))
        `when`(client.resolveOperationSigningConfiguration("identity", TrustSystem.EUDI,
            IssuerFlowSnapshot.BBS_OPERATION, "card", "1", "EUDI_ISSUANCE_2026_1"))
            .thenReturn(IssuerConfigurationClient.RuntimeSigningConfiguration(TrustSystem.EUDI, null, operation))
        val parameters = Json.parseToJsonElement("""{"issuerId":"original-did"}""").jsonObject
        `when`(client.resolveOperationConfigurationOrNull(
            "identity", TrustSystem.EUDI, IssuerFlowSnapshot.BBS_OPERATION,
            "EUDI_ISSUANCE_2026_1",
        ))
            .thenReturn(IssuerConfigurationClient.RuntimeOperationConfiguration(IssuerFlowSnapshot.BBS_OPERATION, 1, parameters))
        val service = IssuerFlowService(client)
        val encoded = service.capture("identity", TrustSystem.EUDI, "card", "1",
            setOf(CredentialFormat.SD_JWT, CredentialFormat.ZKP_VC), CredentialEncryptionPolicy(),
            "EUDI_ISSUANCE_2026_1").encode()

        signing.keyUri = "software://kc/key/v2"
        signing.certificateChain = listOf("renewed-chain")
        operation.keyUri = "software://kc/bbs/v2"
        val saved = IssuerFlowSnapshot.decode(encoded)
        assertEquals("software://kc/key/v1", saved.signing?.keyUri)
        assertEquals(listOf("original-chain"), saved.signing?.certificateChain)
        assertEquals("original-issuer", saved.issuerClaim)
        assertEquals("software://kc/bbs/v1", saved.operationSigning?.keyUri)
        assertEquals(parameters, saved.operationConfiguration)
    }

    @Test
    fun `retains exact versions with their required purposes`() {
        val client = mock(IssuerConfigurationClient::class.java)
        val flow = UUID.randomUUID()
        val expiry = Instant.now().plusSeconds(3600)
        val snapshot = IssuerFlowSnapshot(
            signing = IssuerProperties.IssuerSigning(keyUri = "software://kc/sign/v1"),
            operationSigning = IssuerProperties.IssuerSigning(keyUri = "software://kc/bbs/v1"),
            encryption = CredentialEncryptionPolicy(requestKeys = listOf(
                CredentialEncryptionKey("decrypt-v1", "software://kc/decrypt/v1", "ECDH-ES", "{}"))),
        )
        IssuerFlowService(client).retain("identity", flow, snapshot, expiry)

        verify(client).retainFlow("identity", flow, "software://kc/sign/v1", SigningPurpose.SIGNING, expiry)
        verify(client).retainFlow("identity", flow, "software://kc/bbs/v1", SigningPurpose.OPERATIONS, expiry)
        verify(client).retainFlow("identity", flow, "software://kc/decrypt/v1", SigningPurpose.DECRYPT, expiry)
        verifyNoMoreInteractions(client)
    }
}
