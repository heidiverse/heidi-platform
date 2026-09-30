// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.relyingpartyauthenticationauthorization;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RelyingPartyRegistrationRequest(
        String name,
        @JsonProperty("LEI") String lei,
        @JsonProperty("EORI") String eori,
        @JsonProperty("EUID") String euid,
        @JsonProperty("TAX") String tax) {}
