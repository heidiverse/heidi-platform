// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.oid4vci;

import org.heidiverse.heidi.coordinator.model.issuance.IssuanceData;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record PreAuthIssuanceData(
        IssuanceData.SchemaIdentifier schemaIdentifier,
        Map<String, Object> values,
        String attributeUrl,
        String issuerSlug,
        Boolean includeTxCode,
        CredentialOfferType credentialOfferType,
        @NotBlank(message = "Issuance profile is required") String issuanceProfileId) {

    public PreAuthIssuanceData {
        credentialOfferType = credentialOfferType == null
                ? CredentialOfferType.VALUE : credentialOfferType;
    }
}
