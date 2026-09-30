// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.credentialscheme;

import org.heidiverse.heidi.coordinator.model.oid4vci.CredentialOfferType;

public record CredentialSchemeIssuerResponse(
        String issuerSlug, String tenantId, CredentialOfferType credentialOfferType,
        String issuanceProfileId) {

    public CredentialSchemeIssuerResponse(
            String issuerSlug, String tenantId, CredentialOfferType credentialOfferType) {
        this(issuerSlug, tenantId, credentialOfferType, null);
    }

    public CredentialSchemeIssuerResponse(String issuerSlug) {
        this(issuerSlug, null, CredentialOfferType.VALUE);
    }

    public CredentialSchemeIssuerResponse(String issuerSlug, String tenantId) {
        this(issuerSlug, tenantId, CredentialOfferType.VALUE);
    }
}
