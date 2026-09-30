// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.template;

import org.heidiverse.heidi.coordinator.model.oid4vci.CredentialOfferType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Template(
        UUID id,
        TemplateType type,
        String displayName,
        String libraryKey,
        List<CredentialSchemeAttribute> attributes,
        List<MetaAttribute> metaAttributes,
        Style style,
        IssuerSettings issuerSettings,
        Map<String, AttributeRule> attributeRules) {

    public record CredentialSchemeAttribute(
            String name, String type, Map<String, String> displayName, boolean isArray) {

        public CredentialSchemeAttribute(
                String name, String type, Map<String, String> displayName) {
            this(name, type, displayName, false);
        }
    }

    public record MetaAttribute(String name, Map<String, String> displayName) {}

    public record Style(
            String title,
            String subtitle,
            long cardColor,
            String textColor,
            String backgroundCard,
            List<String> orderedProperties) {}

    public record IssuerSettings(
            String issuerKeyType,
            String doctype,
            String namespace,
            String vct,
            CredentialOfferType credentialOfferType) {

        public IssuerSettings(String issuerKeyType, String doctype, String namespace, String vct) {
            this(issuerKeyType, doctype, namespace, vct, null);
        }
    }

    public record AttributeRule(
            String description,
            boolean isDisplayNameEditable,
            boolean isRequired,
            boolean canBeEmpty) {}
}
