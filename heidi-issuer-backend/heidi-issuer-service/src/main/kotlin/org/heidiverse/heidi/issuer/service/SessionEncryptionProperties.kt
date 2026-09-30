// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("heidi.issuer.session")
data class SessionEncryptionProperties(
    var encryptionMasterKey: String = "",
)
