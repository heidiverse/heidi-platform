// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service.utils;

import org.heidiverse.heidi.shared.localized.LocalizedValue;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeAttribute;
import org.heidiverse.heidi.entity.model.credentialscheme.ReducedCredentialSchemeDetail;
import org.heidiverse.heidi.entity.model.entity.*;
import org.heidiverse.heidi.entity.model.issuer.IssuerDefinition;

public class ProofSchemeCredentialSchemeUtils {
    public static ReducedCredentialSchemeDetail toCredentialScheme(
            final ProofSchemeCredentialSchemeEntity proofSchemeCredentialSchemeEntity) {
        final var credentialSchemeEntity =
                proofSchemeCredentialSchemeEntity.getCredentialSchemeEntity();
        final var requestedAttributeEntities =
                proofSchemeCredentialSchemeEntity
                        .getProofSchemeCredentialSchemeRequestedAttributeEntityList();
        return new ReducedCredentialSchemeDetail(
                credentialSchemeEntity.getUuid(),
                credentialSchemeEntity.getCredentialIdentifier(),
                credentialSchemeEntity.getVersion(),
                credentialSchemeEntity.getDisplayName(),
                toIssuerDefinition(credentialSchemeEntity),
                requestedAttributeEntities.stream()
                        .map(ProofSchemeCredentialSchemeUtils::toCredentialSchemeAttribute)
                        .toList(),
                CredentialSchemeUtils.toIssuerSettings(credentialSchemeEntity));
    }

    public static IssuerDefinition toIssuerDefinition(
            final CredentialSchemeEntity credentialSchemeEntity) {
        final var issuerDefinitionEntity = credentialSchemeEntity.getIssuerDefinition();
        final var displayName = issuerDefinitionEntity.getDisplayName();
        LocalizedValue<String> localizedDisplayName = null;
        if (displayName != null && !displayName.isEmpty()) {
            localizedDisplayName = LocalizedValue.fromStringMap(displayName);
        }
        return new IssuerDefinition(
                issuerDefinitionEntity.getId(),
                issuerDefinitionEntity.getLogo(),
                issuerDefinitionEntity.getSlug(),
                localizedDisplayName);
    }

    private static CredentialSchemeAttribute toCredentialSchemeAttribute(
            final ProofSchemeCredentialSchemeRequestedAttributeEntity
                    proofSchemeCredentialSchemeRequestedAttributeEntity) {
        final var correspondingCredentialSchemeAttribute =
                proofSchemeCredentialSchemeRequestedAttributeEntity
                        .getCredentialSchemeAttributeEntity();
        return new CredentialSchemeAttribute(
                correspondingCredentialSchemeAttribute.getId(),
                correspondingCredentialSchemeAttribute.getFieldName(),
                correspondingCredentialSchemeAttribute.getFieldType(),
                correspondingCredentialSchemeAttribute.isArray(),
                correspondingCredentialSchemeAttribute.isSensitive(),
                correspondingCredentialSchemeAttribute.isDisclosable(),
                CredentialSchemeUtils.getLocalizedDisplayName(
                        correspondingCredentialSchemeAttribute.getDisplayName()),
                correspondingCredentialSchemeAttribute.getFormatSpecificAttributeName());
    }

    public static ProofSchemeCredentialSchemeRequestedAttributeEntity
            fromCredentialSchemeAttributeEntity(
                    final CredentialSchemeAttributeEntity credentialSchemeAttributeEntity,
                    final ProofSchemeCredentialSchemeEntity proofSchemeCredentialSchemeEntity) {
        final var entity = new ProofSchemeCredentialSchemeRequestedAttributeEntity();
        entity.setCredentialSchemeAttributeEntity(credentialSchemeAttributeEntity);
        entity.setProofSchemeCredentialSchemeEntity(proofSchemeCredentialSchemeEntity);
        return entity;
    }
}
