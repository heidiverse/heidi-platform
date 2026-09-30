// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service;

import org.heidiverse.heidi.coordinator.model.CredentialSchemeIssuerResponse;
import org.heidiverse.heidi.coordinator.model.issuance.IssuanceData;
import org.heidiverse.heidi.coordinator.model.oid4vci.PreAuthIssuanceData;
import org.springframework.stereotype.Service;

/** Normalizes issuance data before the coordinator signs a process token. */
@Service
public class IssuanceProcessService {

    private final CoordinatorEntityGateway entityGateway;

    public IssuanceProcessService(CoordinatorEntityGateway entityGateway) {
        this.entityGateway = entityGateway;
    }

    public PreAuthIssuanceData normalize(PreAuthIssuanceData data) {
        return normalize(data, null);
    }

    public PreAuthIssuanceData normalize(
            PreAuthIssuanceData data, CredentialSchemeIssuerResponse resolvedIssuer) {
        var schema = data.schemaIdentifier();
        if (schema == null || schema.credentialIdentifier() == null || schema.version() == null) {
            throw new IllegalArgumentException("Issuance schema identifier and version are required");
        }

        var issuerOverride = data.issuerSlug() != null && !data.issuerSlug().isBlank();
        CredentialSchemeIssuerResponse response = resolvedIssuer;
        try {
            if (response == null) {
                response =
                        entityGateway.getCredentialSchemeIssuer(
                                schema.credentialIdentifier(), schema.version());
            }
        } catch (EntityNotFoundException exception) {
            throw unavailableSchema(schema);
        }

        if (response == null
                || (!issuerOverride
                        && (response.issuerSlug() == null || response.issuerSlug().isBlank()))) {
            throw unavailableSchema(schema);
        }

        if (data.issuanceProfileId() == null || data.issuanceProfileId().isBlank()) {
            throw new IllegalArgumentException("Issuance profile is required");
        }
        if (response.issuanceProfileId() == null
                || !data.issuanceProfileId().equals(response.issuanceProfileId())) {
            throw new IllegalArgumentException(
                    "Issuance profile does not match the credential configuration");
        }

        return new PreAuthIssuanceData(
                schema,
                data.values(),
                data.attributeUrl(),
                issuerOverride ? data.issuerSlug() : response.issuerSlug(),
                data.includeTxCode(),
                response.credentialOfferType(),
                data.issuanceProfileId());
    }

    private IllegalArgumentException unavailableSchema(IssuanceData.SchemaIdentifier schema) {
        return new IllegalArgumentException(
                "Issuance schema is not available for issuance: "
                        + schema.credentialIdentifier()
                        + " / "
                        + schema.version());
    }
}
