// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service.utils;

import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeAttribute;
import org.heidiverse.heidi.entity.model.exceptions.InvalidCredentialSchemeException;
import org.heidiverse.heidi.entity.model.template.Template;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TemplateUtils {

    private TemplateUtils() {
        // Prevent instantiation
    }

    public static void validateTemplate(
            Template template, List<CredentialSchemeAttribute> attributes)
            throws InvalidCredentialSchemeException {

        Map<String, CredentialSchemeAttribute> attributesMap =
                attributes.stream()
                        .collect(
                                Collectors.toMap(
                                        CredentialSchemeAttribute::name, attribute -> attribute));

        for (Map.Entry<String, Template.AttributeRule> entry :
                template.attributeRules().entrySet()) {
            String attributeName = entry.getKey();
            Template.AttributeRule rule = entry.getValue();

            validateAttribute(attributeName, rule, template, attributesMap);
        }

        validateExtraAttributes(attributes, template);
    }

    private static void validateAttribute(
            String attributeName,
            Template.AttributeRule rule,
            Template template,
            Map<String, CredentialSchemeAttribute> attributesMap)
            throws InvalidCredentialSchemeException {

        if (rule.isRequired() && !attributesMap.containsKey(attributeName)) {
            throw new InvalidCredentialSchemeException(
                    "Required attribute '"
                            + attributeName
                            + "' is missing in the credential schema.");
        }

        if (attributesMap.containsKey(attributeName)) {
            CredentialSchemeAttribute attribute = attributesMap.get(attributeName);

            validateAttributeType(attributeName, attribute, template);
            validateDisplayName(attributeName, attribute, rule, template);
        }
    }

    private static void validateAttributeType(
            String attributeName, CredentialSchemeAttribute attribute, Template template)
            throws InvalidCredentialSchemeException {

        String templateAttributeType =
                template.attributes().stream()
                        .filter(attr -> attr.name().equals(attributeName))
                        .findFirst()
                        .map(Template.CredentialSchemeAttribute::type)
                        .orElseThrow(
                                () ->
                                        new InvalidCredentialSchemeException(
                                                "Type for attribute '"
                                                        + attributeName
                                                        + "' not found in the template."));

        if (!templateAttributeType.equals(attribute.type().toString())) {
            throw new InvalidCredentialSchemeException(
                    "Type mismatch for attribute '"
                            + attributeName
                            + "': expected '"
                            + templateAttributeType
                            + "' but found '"
                            + attribute.type()
                            + "'.");
        }
    }

    private static void validateDisplayName(
            String attributeName,
            CredentialSchemeAttribute attribute,
            Template.AttributeRule rule,
            Template template)
            throws InvalidCredentialSchemeException {

        if (!rule.isDisplayNameEditable()) {
            Map<String, String> templateDisplayName =
                    template.attributes().stream()
                            .filter(attr -> attr.name().equals(attributeName))
                            .findFirst()
                            .map(Template.CredentialSchemeAttribute::displayName)
                            .orElseThrow(
                                    () ->
                                            new InvalidCredentialSchemeException(
                                                    "Display names for attribute '"
                                                            + attributeName
                                                            + "' not found in the template."));

            Map<String, String> attributeDisplayName =
                    attribute.displayName().toMapWithLanguageTagKeys();

            if (!templateDisplayName.equals(attributeDisplayName)) {
                throw new InvalidCredentialSchemeException(
                        "Display names for attribute '"
                                + attributeName
                                + "' must match the template.");
            }
        }
    }

    private static void validateExtraAttributes(
            List<CredentialSchemeAttribute> attributes, Template template)
            throws InvalidCredentialSchemeException {

        for (CredentialSchemeAttribute attribute : attributes) {
            if (!template.attributeRules().containsKey(attribute.name())) {
                throw new InvalidCredentialSchemeException(
                        "Attribute '" + attribute.name() + "' is not defined in the template.");
            }
        }
    }
}
