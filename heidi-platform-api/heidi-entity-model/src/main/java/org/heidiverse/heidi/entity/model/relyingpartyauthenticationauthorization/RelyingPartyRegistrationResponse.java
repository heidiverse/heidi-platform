// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.relyingpartyauthenticationauthorization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RelyingPartyRegistrationResponse(
        String name,
        UUID id,
        @JsonProperty("EORI") String eori,
        @JsonProperty("NTR") String ntr,
        @JsonProperty("LEI") String lei,
        @JsonProperty("VAT") String vat,
        @JsonProperty("EX") String ex,
        @JsonProperty("TAX") String tax,
        @JsonProperty("EUID") String euid,
        String distinguishedName) {}
