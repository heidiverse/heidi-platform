// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model;

import org.heidiverse.heidi.coordinator.model.oid4vci.CredentialOfferType;

public record CredentialSchemeIssuerResponse(
        String issuerSlug, String tenantId, CredentialOfferType credentialOfferType,
        String issuanceProfileId) {}
