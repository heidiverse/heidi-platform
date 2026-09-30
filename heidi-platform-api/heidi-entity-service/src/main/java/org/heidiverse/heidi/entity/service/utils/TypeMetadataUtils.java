// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service.utils;

import org.heidiverse.heidi.coordinator.service.utils.CryptoUtils;
import org.heidiverse.heidi.entity.model.credentialscheme.AttributeType;
import org.heidiverse.heidi.entity.model.credentialscheme.TypeMetadata;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeAttributeEntity;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeEntity;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeStyleEntity;
import org.heidiverse.heidi.shared.oca.OcaFormat;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TypeMetadataUtils {
    private static final Logger logger = LoggerFactory.getLogger(TypeMetadataUtils.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static TypeMetadata buildTypeMetadata(
            CredentialSchemeEntity schemeEntity,
            List<CredentialSchemeAttributeEntity> attributes,
            List<CredentialSchemeStyleEntity> styles,
            String entityBaseUrl,
            String issuerBaseUrl) {

        return buildTypeMetadata(schemeEntity, attributes, styles, entityBaseUrl, issuerBaseUrl, OcaFormat.LEGACY);
    }

    public static TypeMetadata buildTypeMetadata(
            CredentialSchemeEntity schemeEntity,
            List<CredentialSchemeAttributeEntity> attributes,
            List<CredentialSchemeStyleEntity> styles,
            String entityBaseUrl,
            String issuerBaseUrl,
            OcaFormat format) {

        String vct =
                (schemeEntity.getVct() != null && !schemeEntity.getVct().isEmpty())
                        ? schemeEntity.getVct()
                        : entityBaseUrl
                                + "/public/v2/schema/"
                                + schemeEntity.getCredentialIdentifier()
                                + "/"
                                + schemeEntity.getVersion();
        String description =
                schemeEntity.getDisplayName() == null
                        ? null
                        : "Issuance schema for "
                                + schemeEntity.getCredentialIdentifier()
                                + ", version "
                                + schemeEntity.getVersion()
                                + ". Managed by tenant "
                                + schemeEntity.getTenantId();

        return new TypeMetadata(
                vct,
                schemeEntity.getDisplayName(),
                description,
                buildSchema(attributes),
                buildDisplay(schemeEntity, styles, issuerBaseUrl, entityBaseUrl, format),
                buildClaims(attributes));
    }

    private static TypeMetadata.JsonSchema buildSchema(
            List<CredentialSchemeAttributeEntity> attributes) {
        Map<String, TypeMetadata.JsonSchemaProperty> properties = new LinkedHashMap<>();
        properties.put("vct", new TypeMetadata.JsonSchemaProperty("string"));
        properties.put("vct_metadata_uri", new TypeMetadata.JsonSchemaProperty("string"));
        properties.put("iss", new TypeMetadata.JsonSchemaProperty("string"));
        properties.put("nbf", new TypeMetadata.JsonSchemaProperty("number"));
        properties.put("exp", new TypeMetadata.JsonSchemaProperty("number"));
        properties.put("cnf", new TypeMetadata.JsonSchemaProperty("object"));
        properties.put("status", new TypeMetadata.JsonSchemaProperty("object"));

        for (CredentialSchemeAttributeEntity attr : attributes) {
            properties.put(
                    attr.getFieldName(),
                    new TypeMetadata.JsonSchemaProperty(
                            mapAttributeTypeToJsonSchemaType(attr.getFieldType())));
        }

        return new TypeMetadata.JsonSchema(
                "https://json-schema.org/draft/2020-12/schema",
                "object",
                properties,
                List.of("iss", "vct", "cnf"));
    }

    private static String mapAttributeTypeToJsonSchemaType(AttributeType attributeType) {
        return switch (attributeType) {
            case STRING, LINK, FILE_DOWNLOAD, MAIL, PHONE, LOCATION, DATEOFBIRTH, IMAGE, OTHER ->
                    "string";
            case NUMBER -> "number";
            case BOOLEAN -> "boolean";
            case DATE, TIME, DATETIME -> "string";
            default -> "string";
        };
    }

    private static List<TypeMetadata.Display> buildDisplay(
            CredentialSchemeEntity schemeEntity,
            List<CredentialSchemeStyleEntity> styles,
            String issuerBaseUrl,
            String entityBaseUrl,
            OcaFormat format) {
        if (styles == null || styles.isEmpty() || schemeEntity.getDisplayName() == null) {
            return List.of();
        }

        // Assume we only have one style json per credential schema
        CredentialSchemeStyleEntity styleEntity = styles.get(styles.size() - 1);

        if (format == OcaFormat.SWIYU) {
            return swiyuDisplay(schemeEntity, styleEntity, issuerBaseUrl);
        }

        TypeMetadata.Rendering rendering = null;
        try {
            JsonNode styleJson = objectMapper.readTree(styleEntity.getStyle());
            String backgroundColor = nonEmptyText(styleJson, "cardColor");
            String textColor = nonEmptyText(styleJson, "textColor");

            String integrity = CryptoUtils.computeSha256Hash(styleEntity.getOcaBundle());

            List<TypeMetadata.SvgTemplate> svgTemplates = null;
            if (styleJson.has("backgroundCard")
                    && !styleJson.get("backgroundCard").asString().isEmpty()) {
                String svgUri =
                        entityBaseUrl
                                + "/public/v2/schema/"
                                + schemeEntity.getCredentialIdentifier()
                                + "/"
                                + schemeEntity.getVersion()
                                + "/svg";
                String svgContent = generateSvgFromStyleJson(styleJson);
                String svgIntegrity = CryptoUtils.computeSha256Hash(svgContent);
                svgTemplates =
                        List.of(
                                new TypeMetadata.SvgTemplate(
                                        svgUri,
                                        svgIntegrity,
                                        new TypeMetadata.SvgTemplateProperties(
                                                "landscape",
                                                styleJson.get("textColor").asString("light"))));
            }

            if (backgroundColor != null || textColor != null) {
                rendering =
                        new TypeMetadata.Rendering(
                                new TypeMetadata.SimpleRendering(backgroundColor, textColor),
                                new TypeMetadata.OcaRendering(
                                        issuerBaseUrl
                                                + "/oca/"
                                                + styleEntity.getOcaBundleFileName()
                                                + ".json",
                                        integrity),
                                svgTemplates);
            }
        } catch (JacksonException e) {
            logger.error("Failed to parse style JSON", e);
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to compute SHA-256 hash of OCA bundle", e);
            throw new RuntimeException(e);
        }

        return List.of(new TypeMetadata.Display("en-US", schemeEntity.getDisplayName(), rendering));
    }

    private static List<TypeMetadata.Display> swiyuDisplay(
            CredentialSchemeEntity scheme, CredentialSchemeStyleEntity style, String issuerBaseUrl) {
        try {
            // Pin SRI to the persisted representation; the endpoint selects it by User-Agent.
            var oca = new TypeMetadata.OcaRendering(
                    issuerBaseUrl + "/oca/" + style.getSwiyuOcaFileName() + ".json",
                    CryptoUtils.computeSha256HashSri(style.getSwiyuOcaBundle()));
            return List.of(new TypeMetadata.Display("en-US", scheme.getDisplayName(),
                    new TypeMetadata.Rendering(null, oca, null)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nonEmptyText(JsonNode node, String fieldName) {
        return node.has(fieldName) && !node.get(fieldName).asString().isEmpty()
                ? node.get(fieldName).asString()
                : null;
    }

    private static List<TypeMetadata.Claim> buildClaims(
            List<CredentialSchemeAttributeEntity> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return List.of();
        }

        return attributes.stream()
                .map(
                        attr ->
                                new TypeMetadata.Claim(
                                        List.of(attr.getFieldName()),
                                        attr.getDisplayName().entrySet().stream()
                                                .map(
                                                        entry ->
                                                                new TypeMetadata.ClaimDisplay(
                                                                        entry.getKey(),
                                                                        entry.getValue(),
                                                                        "Attribute "
                                                                                + attr.getFieldName()
                                                                                + " for "
                                                                                + entry.getKey()))
                                                .toList(),
                                        attr.isDisclosable()
                                                ? TypeMetadata.SelectiveDisclosure.ALLOWED
                                                : TypeMetadata.SelectiveDisclosure.NEVER,
                                        attr.getFieldName()))
                .toList();
    }

    /**
     * Generates an SVG template from the provided style JSON string, including a title and subtitle
     * if present, with text color based on the textColor field.
     *
     * @param styleJson The style JSON string containing the backgroundCard, title, subtitle, and
     *     textColor fields. If backgroundCard is missing or empty, falls back to cardColor as a
     *     solid color background.
     * @return The SVG template string.
     * @throws IllegalStateException If neither backgroundCard nor cardColor is defined.
     */
    public static String generateSvgFromStyleJson(JsonNode styleJson) throws IllegalStateException {
        String backgroundCard = "";
        boolean useImage = false;
        String fallbackColor = null;

        if (styleJson.has("backgroundCard")
                && !styleJson.get("backgroundCard").asString().isEmpty()) {
            backgroundCard = styleJson.get("backgroundCard").asString();
            useImage = true;
        } else if (styleJson.has("cardColor")) {
            long colorInt = styleJson.get("cardColor").asLong(0L);
            fallbackColor = intToHexColor(colorInt);
        } else {
            throw new IllegalStateException(
                    "No backgroundCard or cardColor defined in style JSON.");
        }

        // Extract title and subtitle, default to empty strings if not present
        String title = styleJson.has("title") ? styleJson.get("title").asString() : "";
        String subtitle = styleJson.has("subtitle") ? styleJson.get("subtitle").asString() : "";

        // Determine text color based on textColor field, default to black
        String textColor = "black";
        if (styleJson.has("textColor")) {
            String colorValue = styleJson.get("textColor").asString();
            if ("light".equalsIgnoreCase(colorValue)) {
                textColor = "white";
            } else if ("dark".equalsIgnoreCase(colorValue)) {
                textColor = "black";
            }
        }

        // Start building the SVG
        StringBuilder svgBuilder = new StringBuilder();
        svgBuilder.append(
                """
                <svg width="320" height="201.76" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
                """);

        // Background: either image (from backgroundCard) or solid color rect (from cardColor
        // fallback)
        if (useImage) {
            svgBuilder.append(
                    """
                  <!-- Embedding the PNG as a background image -->
                  <image xlink:href="%s" x="0" y="0" width="320" height="201.76" preserveAspectRatio="xMidYMid meet"/>
                """
                            .formatted(backgroundCard));
        } else {
            svgBuilder.append(
                    """
                  <!-- Solid color background from cardColor fallback -->
                  <rect width="320" height="201.76" fill="%s"/>
                """
                            .formatted(fallbackColor));
        }

        // Conditionally add title if not empty
        if (!title.isEmpty()) {
            svgBuilder.append(
                    """
                  <!-- Title -->
                  <text x="20" y="30" font-family="Arial, sans-serif" font-size="20" font-weight="600" fill="%s" text-anchor="start">%s</text>
                """
                            .formatted(textColor, title));
        }

        // Conditionally add subtitle if not empty
        if (!subtitle.isEmpty()) {
            svgBuilder.append(
                    """
                  <!-- Subtitle -->
                  <text x="20" y="50" font-family="Arial, sans-serif" font-size="14" font-weight="500" fill="%s" text-anchor="start">%s</text>
                """
                            .formatted(textColor, subtitle));
        }

        // Add grid overlay and close SVG
        svgBuilder.append(
                """
                  <!-- Grid overlay -->
                  <g transform="translate(20, 20)">
                    <rect x="0" y="0" width="280" height="161.76" fill="none" stroke="none"/>
                  </g>
                </svg>
                """);

        return svgBuilder.toString();
    }

    /**
     * Converts an unsigned 32-bit ARGB color (Android style) to a CSS/SVG hex string. If alpha is
     * 0xFF (fully opaque), returns #RRGGBB. Otherwise returns #AARRGGBB.
     */
    private static String intToHexColor(long colorInt) {
        // Mask to 32-bit unsigned
        long unsignedInt = colorInt & 0xFFFFFFFFL;

        // Extract channels
        int a = (int) ((unsignedInt >> 24) & 0xFF);
        int r = (int) ((unsignedInt >> 16) & 0xFF);
        int g = (int) ((unsignedInt >> 8) & 0xFF);
        int b = (int) (unsignedInt & 0xFF);

        if (a == 0xFF) {
            // Opaque → use #RRGGBB
            return String.format("#%02X%02X%02X", r, g, b);
        } else {
            // Keep alpha → #AARRGGBB
            return String.format("#%02X%02X%02X%02X", a, r, g, b);
        }
    }
}
