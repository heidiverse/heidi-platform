// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.heidiverse.heidi.issuer.model.EnforcementMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

/**
 * Pins the property names these classes bind from.
 *
 * The rest of the issuer's tests construct [EnforcementProperties] and
 * [SessionEncryptionProperties] directly, so a wrong `@ConfigurationProperties` prefix or a
 * renamed field compiles and passes while binding nothing - the values silently fall back to
 * their defaults and only fail when a deployment starts. These names were changed once already,
 * away from the `pidi.*` prefix inherited from the issuer this one grew out of.
 */
class RenamedPropertyBindingTest {

    @Test
    fun `enforcement settings bind from heidi issuer enforcement`() {
        val source = MapConfigurationPropertySource(
            mapOf(
                "heidi.issuer.enforcement.dpop" to "DISABLED",
                "heidi.issuer.enforcement.status-list" to "REQUIRED",
                "heidi.issuer.enforcement.tx-code" to "DISABLED",
                "heidi.issuer.enforcement.client-attestation" to "REQUIRED",
            ),
        )

        val properties = Binder(source)
            .bind("heidi.issuer.enforcement", Bindable.of(EnforcementProperties::class.java))
            .get()

        assertEquals(EnforcementMode.DISABLED, properties.dpop)
        assertEquals(EnforcementMode.REQUIRED, properties.statusList)
        assertEquals(EnforcementMode.DISABLED, properties.txCode)
        assertEquals(EnforcementMode.REQUIRED, properties.clientAttestation)
    }

    @Test
    fun `session master key binds from heidi issuer session`() {
        val key = "a".repeat(64)
        val source = MapConfigurationPropertySource(
            mapOf("heidi.issuer.session.encryption-master-key" to key),
        )

        val properties = Binder(source)
            .bind("heidi.issuer.session", Bindable.of(SessionEncryptionProperties::class.java))
            .get()

        assertEquals(key, properties.encryptionMasterKey)
    }

    @Test
    fun issuerNamesBind() {
        val source = MapConfigurationPropertySource(
            mapOf(
                "heidi.issuer.platform-internal-base-url" to "http://platform",
                "heidi.issuer.signing-provider.base-url" to "http://signing",
                "heidi.issuer.signing-provider.authentication-mode" to "none",
                "heidi.issuer.signing-provider.client-seed" to "seed",
            ),
        )

        val properties = Binder(source)
            .bind("heidi.issuer", Bindable.of(IssuerProperties::class.java))
            .get()

        assertEquals("http://platform", properties.platformInternalBaseUrl)
        assertEquals("http://signing", properties.signingProvider.baseUrl)
        assertEquals("none", properties.signingProvider.authenticationMode)
        assertEquals("seed", properties.signingProvider.clientSeed)
    }

    /**
     * The two tests above pass the prefix to the binder themselves, so they pin the field names
     * but not the annotation Spring actually reads at startup. This pins that.
     */
    @Test
    fun `configuration prefixes are the renamed ones`() {
        assertEquals(
            "heidi.issuer.enforcement",
            EnforcementProperties::class.java
                .getAnnotation(ConfigurationProperties::class.java)
                .value,
        )
        assertEquals(
            "heidi.issuer.session",
            SessionEncryptionProperties::class.java
                .getAnnotation(ConfigurationProperties::class.java)
                .value,
        )
    }
}
