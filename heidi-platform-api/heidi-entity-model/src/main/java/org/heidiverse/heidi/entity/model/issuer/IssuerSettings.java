// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import org.heidiverse.heidi.entity.model.credentialscheme.CredentialType;
import org.heidiverse.heidi.coordinator.model.oid4vci.CredentialOfferType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;
import java.util.Map;
import java.util.UUID;

public record IssuerSettings(
        @NotNull int id,
        @NotNull IssuerKeyType issuerKeyType,
        String doctype,
        String namespace,
        String vct,
        Set<CredentialType> supportedCredentialTypes,
        String issClaimOverride,
        String kidOverride,
        String bbsCredentialType,
        IssuerTrustSystem defaultTrustSystem,
        Map<IssuerTrustSystem, String> signingKeyIds,
        UUID statusListId,
        CredentialOfferType credentialOfferType,
        @NotBlank(message = "Issuance profile is required") String issuanceProfileId) {

    public IssuerSettings {
        credentialOfferType = credentialOfferType == null
                ? CredentialOfferType.VALUE : credentialOfferType;
    }
}
