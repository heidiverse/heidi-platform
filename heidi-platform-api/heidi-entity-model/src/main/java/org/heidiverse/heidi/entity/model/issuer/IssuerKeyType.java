// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import com.fasterxml.jackson.annotation.JsonAlias;

public enum IssuerKeyType {
    @JsonAlias({"HARDWARE"})
    HARDWARE_BIOMETRIC_AUTH,
    @JsonAlias({"SOFTWARE"})
    SOFTWARE_NO_AUTH,
    REMOTE_HSM_PIN_AUTH,
    NO_KEY_CLAIM_BINDING
}
