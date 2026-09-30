// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import org.heidiverse.heidi.issuer.model.EnforcementMode
import org.springframework.boot.context.properties.ConfigurationProperties

/** Backwards-compatible feature enforcement settings from the previous issuer. */
@ConfigurationProperties("heidi.issuer.enforcement")
data class EnforcementProperties(
    var dpop: EnforcementMode = EnforcementMode.REQUIRED,
    var statusList: EnforcementMode = EnforcementMode.OPTIONAL,
    var txCode: EnforcementMode = EnforcementMode.OPTIONAL,
    var clientAttestation: EnforcementMode = EnforcementMode.OPTIONAL,
)
