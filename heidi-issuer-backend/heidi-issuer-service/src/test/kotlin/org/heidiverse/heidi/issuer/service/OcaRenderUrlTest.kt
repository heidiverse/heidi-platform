// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import kotlinx.serialization.json.JsonNull
import org.heidiverse.heidi.issuer.service.SchemaMetadataService.CredentialFormat
import org.heidiverse.heidi.issuer.service.SchemaMetadataService.SchemaMetadata
import org.heidiverse.heidi.shared.oca.OcaFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OcaRenderUrlTest {
    @Test
    fun `omits render URL for swiyu metadata`() {
        val metadata = metadata(OcaFormat.SWIYU)

        assertNull(ocaRenderUrl(metadata, "https://issuer.example", null))
    }

    @Test
    fun `keeps legacy render URL for legacy metadata`() {
        val metadata = metadata(OcaFormat.LEGACY)

        assertEquals(
            "https://issuer.example/oca/Elegacy.json",
            ocaRenderUrl(metadata, "https://issuer.example/", null),
        )
    }

    @Test
    fun `swiyu header suppresses legacy render URL`() {
        val metadata = metadata(OcaFormat.LEGACY)

        assertNull(ocaRenderUrl(metadata, "https://issuer.example", "swiyuWallet/1.0"))
    }

    private fun metadata(format: OcaFormat) = SchemaMetadata(
        vct = "urn:test",
        docType = "org.example.test",
        typeMetadata = JsonNull,
        supportedFormats = setOf(CredentialFormat.SD_JWT),
        bbsCredentialType = null,
        ocaBundleFileName = if (format == OcaFormat.SWIYU) "Sswiyu" else "Elegacy",
        ocaFormat = format,
        legacyOcaBundleFileName = "Elegacy",
    )
}
