// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service.utils;

import org.heidiverse.heidi.shared.localized.LocalizedValue;
import org.heidiverse.heidi.entity.model.credentialscheme.*;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeAttributeEntity;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeEntity;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeStyleEntity;
import org.heidiverse.heidi.entity.model.issuer.IssuerSettings;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class CredentialSchemeUtils {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(CredentialSchemeUtils.class);

    public static boolean isPublished(final CredentialSchemeEntity credentialSchemeEntity) {
        return credentialSchemeEntity.getState() == CredentialSchemeState.PUBLISHED;
    }

    public static CredentialSchemeOverviewEntry toCredentialSchemeOverviewEntry(
            final CredentialSchemeEntity credentialSchemeEntity,
            final List<CredentialSchemeStyleDetail> styleDetails) {
        return new CredentialSchemeOverviewEntry(
                credentialSchemeEntity.getUuid(),
                credentialSchemeEntity.getCredentialIdentifier(),
                credentialSchemeEntity.getVersion(),
                credentialSchemeEntity.getDisplayName(),
                credentialSchemeEntity.getCreatedAt(),
                credentialSchemeEntity.getUpdatedAt(),
                credentialSchemeEntity.getState(),
                toIssuerSettings(credentialSchemeEntity),
                styleDetails,
                credentialSchemeEntity.getTenantId());
    }

    public static IssuerSettings toIssuerSettings(
            final CredentialSchemeEntity credentialSchemeEntity) {
        return new IssuerSettings(
                credentialSchemeEntity.getIssuerDefinition().getId(),
                credentialSchemeEntity.getKeyType(),
                credentialSchemeEntity.getDoctype(),
                credentialSchemeEntity.getNamespace(),
                credentialSchemeEntity.getVct(),
                credentialSchemeEntity.getSupportedCredentialTypes(),
                credentialSchemeEntity.getIssClaimOverride(),
                credentialSchemeEntity.getKidOverride(),
                credentialSchemeEntity.getBbsCredentialType(),
                credentialSchemeEntity.getDefaultTrustSystem(),
                credentialSchemeEntity.getSigningKeyIds(),
                credentialSchemeEntity.getStatusListId(),
                credentialSchemeEntity.getCredentialOfferType(),
                credentialSchemeEntity.getIssuanceProfileId());
    }

    public static CredentialSchemeAttribute toCredentialSchemeAttribute(
            final CredentialSchemeAttributeEntity credentialSchemeAttributeEntity) {
        return new CredentialSchemeAttribute(
                credentialSchemeAttributeEntity.getId(),
                credentialSchemeAttributeEntity.getFieldName(),
                credentialSchemeAttributeEntity.getFieldType(),
                credentialSchemeAttributeEntity.isArray(),
                credentialSchemeAttributeEntity.isSensitive(),
                credentialSchemeAttributeEntity.isDisclosable(),
                getLocalizedDisplayName(credentialSchemeAttributeEntity.getDisplayName()),
                credentialSchemeAttributeEntity.getFormatSpecificAttributeName());
    }

    public static LocalizedValue<String> getLocalizedDisplayName(
            final Map<String, String> displayName) {
        if (displayName == null || displayName.isEmpty()) {
            return null;
        }
        return LocalizedValue.fromStringMap(displayName);
    }

    public static CredentialSchemeStyleDetail toCredentialSchemeStyleDetail(
            final CredentialSchemeStyleEntity credentialSchemeStyleEntity) {
        return new CredentialSchemeStyleDetail(
                credentialSchemeStyleEntity.getStyle(),
                credentialSchemeStyleEntity.getOcaBundle(),
                credentialSchemeStyleEntity.getOcaBundleFileName(),
                credentialSchemeStyleEntity.getTypstTemplate(),
                credentialSchemeStyleEntity.getOcaVersion());
    }

    public static CredentialSchemeStyleDetail removeImagesIfRequired(
            CredentialSchemeStyleDetail styleDetail, boolean includeImages) {
        if (includeImages || styleDetail.style() == null || styleDetail.ocaBundle() == null) {
            return styleDetail;
        }

        try {
            // Parse the style JSON and remove "backgroundCard"
            JsonNode styleJson = objectMapper.readTree(styleDetail.style());
            if (styleJson.has("backgroundCard")) {
                ((ObjectNode) styleJson).put("backgroundCard", "");
            }

            // Parse the OCA bundle JSON and remove "backgroundCard" in style overlays
            JsonNode ocaBundleJson = objectMapper.readTree(styleDetail.ocaBundle());
            if (ocaBundleJson.has("overlays")) {
                for (JsonNode overlay : ocaBundleJson.get("overlays")) {
                    if (overlay.has("backgroundCard")) {
                        ((ObjectNode) overlay).put("backgroundCard", "");
                    }
                }
            }
            // Return new CredentialSchemeStyleDetail with modified JSON strings
            return new CredentialSchemeStyleDetail(
                    objectMapper.writeValueAsString(styleJson),
                    objectMapper.writeValueAsString(ocaBundleJson),
                    styleDetail.ocaBundleFilename(),
                    styleDetail.typstTemplate(),
                    styleDetail.ocaVersion());
        } catch (Exception e) {
            logger.warn("Failed to remove images", e);
            return styleDetail;
        }
    }
}
