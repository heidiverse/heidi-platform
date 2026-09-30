// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import org.heidiverse.heidi.coordinator.model.oid4vci.CredentialOfferType;
import org.heidiverse.heidi.entity.model.credentialscheme.AttributeType;
import org.heidiverse.heidi.entity.model.template.Template;

import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class I14yTemplateMapper {

    private static final String I14Y_LIBRARY_PREFIX = "i14y:";
    private static final String DEFAULT_VERSION = "1.0.0";

    private I14yTemplateMapper() {}

    static String libraryKey(UUID templateId) {
        return I14Y_LIBRARY_PREFIX + templateId;
    }

    static I14yService.Result map(
            UUID datasetId, JsonNode datasetResponse, JsonNode structureResponse) {
        JsonNode dataset = unwrap(datasetResponse);
        List<String> warnings = new ArrayList<>();
        String version = text(dataset, "version", DEFAULT_VERSION);
        String displayName = displayName(dataset, datasetId);
        List<JsonNode> propertyShapes = propertyShapes(unwrap(structureResponse));

        if (propertyShapes.isEmpty()) {
            throw new IllegalArgumentException(
                    "The I14Y dataset has no supported published structure.");
        }

        Map<String, Template.CredentialSchemeAttribute> attributes = new LinkedHashMap<>();
        Map<String, Template.AttributeRule> rules = new LinkedHashMap<>();
        Set<String> usedNames = new HashSet<>();
        for (JsonNode shape : propertyShapes) {
            String baseName = attributeName(field(shape, "path"));
            if (baseName.isBlank()) {
                warnings.add("Skipped a structure property without a stable name.");
                continue;
            }

            String name = uniqueName(baseName, usedNames);
            if (!name.equals(baseName)) {
                warnings.add("Renamed duplicate attribute '" + baseName + "' to '" + name + "'.");
            }

            AttributeType type = attributeType(shape, name, warnings);
            Map<String, String> labels = localized(field(shape, "name"));
            if (labels.isEmpty()) {
                labels = localized(field(shape, "label"));
            }
            if (labels.isEmpty()) {
                labels = Map.of("en", baseName);
            }

            boolean required = number(field(shape, "minCount"), 0) > 0;
            JsonNode maxCount = field(shape, "maxCount");
            boolean isArray = maxCount == null || number(maxCount, 1) > 1;
            if (maxCount == null) {
                warnings.add("Attribute '" + name + "' has no maximum cardinality; imported as an array.");
            }
            if (field(shape, "in") != null) {
                warnings.add("Code-list validation for '" + name + "' is not represented in Heidi.");
            }

            attributes.put(
                    name,
                    new Template.CredentialSchemeAttribute(name, type.name(), labels, isArray));
            rules.put(
                    name,
                    new Template.AttributeRule(
                            description(field(shape, "description")),
                            true,
                            required,
                            !required));
        }

        if (attributes.isEmpty()) {
            throw new IllegalArgumentException(
                    "The I14Y dataset has no named properties that Heidi can import.");
        }

        UUID templateId = UUID.nameUUIDFromBytes(
                ("i14y:dataset:" + datasetId + ":" + version)
                        .getBytes(StandardCharsets.UTF_8));
        String sourceLibraryKey = libraryKey(templateId);
        Template template = new Template(
                templateId,
                org.heidiverse.heidi.entity.model.template.TemplateType.CredentialSchemaTemplate,
                displayName,
                sourceLibraryKey,
                List.copyOf(attributes.values()),
                List.of(),
                null,
                new Template.IssuerSettings(
                        "SOFTWARE_NO_AUTH", "", "", "", CredentialOfferType.VALUE),
                Map.copyOf(rules));

        return new I14yService.Result(datasetId, template, version, List.copyOf(warnings));
    }

    private static AttributeType attributeType(
            JsonNode shape, String name, List<String> warnings) {
        String datatype = localName(textValue(field(shape, "datatype")));
        if (datatype.isBlank()) {
            String nodeKind = localName(textValue(field(shape, "nodeKind")));
            if (nodeKind.equalsIgnoreCase("IRI")) {
                return AttributeType.LINK;
            }
            warnings.add("Attribute '" + name + "' has no datatype; defaulted to STRING.");
            return AttributeType.STRING;
        }

        return switch (datatype.toLowerCase()) {
            case "boolean" -> AttributeType.BOOLEAN;
            case "date" -> AttributeType.DATE;
            case "datetime" -> AttributeType.DATETIME;
            case "time" -> AttributeType.TIME;
            case "integer", "int", "long", "decimal", "double", "float", "number" ->
                    AttributeType.NUMBER;
            case "anyuri", "uri" -> AttributeType.LINK;
            case "string", "normalizedstring", "token" -> AttributeType.STRING;
            default -> {
                warnings.add(
                        "Datatype '" + datatype + "' for '" + name + "' was imported as OTHER.");
                yield AttributeType.OTHER;
            }
        };
    }

    private static List<JsonNode> propertyShapes(JsonNode root) {
        List<JsonNode> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        collectPropertyShapes(root, result, seen);
        return result;
    }

    private static void collectPropertyShapes(
            JsonNode node, List<JsonNode> result, Set<String> seen) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectPropertyShapes(child, result, seen));
            return;
        }
        if (!node.isObject()) {
            return;
        }

        JsonNode path = field(node, "path");
        if (path != null) {
            String key = path.toString();
            if (seen.add(key)) {
                result.add(node);
            }
        }
        for (var entry : node.properties()) {
            collectPropertyShapes(entry.getValue(), result, seen);
        }
    }

    private static JsonNode unwrap(JsonNode node) {
        if (node != null && node.isObject() && node.has("data")) {
            return node.get("data");
        }
        return node;
    }

    private static JsonNode field(JsonNode node, String name) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode exact = node.get(name);
        if (exact != null) {
            return exact;
        }
        for (var entry : node.properties()) {
            String key = entry.getKey();
            if (key.equals(name)
                    || key.endsWith("#" + name)
                    || key.endsWith("/" + name)
                    || key.endsWith(":" + name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = field(node, field);
        String text = textValue(value);
        return text.isBlank() ? fallback : text;
    }

    private static String displayName(JsonNode dataset, UUID datasetId) {
        Map<String, String> title = localized(field(dataset, "title"));
        if (title.isEmpty()) {
            title = localized(field(dataset, "name"));
        }
        String displayName;
        if (!title.isEmpty()) {
            displayName = title.getOrDefault("en", title.values().iterator().next());
        } else {
            displayName = text(dataset, "identifier", datasetId.toString());
        }
        return displayName.replaceAll("\\s+", " ").trim();
    }

    private static Map<String, String> localized(JsonNode node) {
        Map<String, String> result = new LinkedHashMap<>();
        addLocalized(node, result);
        return Map.copyOf(result);
    }

    private static void addLocalized(JsonNode node, Map<String, String> result) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            result.putIfAbsent("en", node.asText());
            return;
        }
        if (node.isArray()) {
            node.forEach(value -> addLocalized(value, result));
            return;
        }
        if (!node.isObject()) {
            return;
        }

        JsonNode value = node.get("@value");
        if (value != null && value.isTextual()) {
            String language = text(node, "@language", "en");
            result.putIfAbsent(language, value.asText());
            return;
        }
        for (var entry : node.properties()) {
            String key = entry.getKey();
            JsonNode child = entry.getValue();
            if (!key.startsWith("@") && child.isTextual()) {
                result.putIfAbsent(key, child.asText());
            }
        }
    }

    private static String description(JsonNode node) {
        Map<String, String> values = localized(node);
        return values.getOrDefault("en", values.values().stream().findFirst().orElse(""));
    }

    private static int number(JsonNode node, int fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (node.isArray()) {
            for (JsonNode value : node) {
                Integer number = numberValue(value);
                if (number != null) {
                    return number;
                }
            }
            return fallback;
        }
        Integer number = numberValue(node);
        return number == null ? fallback : number;
    }

    private static Integer numberValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        if (node.isObject()) {
            JsonNode value = node.get("@value");
            return numberValue(value);
        }
        if (node.isTextual()) {
            try {
                return Integer.parseInt(node.asText());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }

    private static String attributeName(JsonNode path) {
        String value = textValue(path);
        if (value.isBlank()) {
            return "";
        }
        String local = localName(value).replaceAll("[^A-Za-z0-9_]", "_");
        if (local.isBlank()) {
            return "";
        }
        return Character.isDigit(local.charAt(0)) ? "attribute_" + local : local;
    }

    private static String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isArray()) {
            for (JsonNode value : node) {
                String text = textValue(value);
                if (!text.isBlank()) {
                    return text;
                }
            }
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject()) {
            JsonNode id = node.get("@id");
            if (id != null) {
                return textValue(id);
            }
            JsonNode value = node.get("@value");
            return textValue(value);
        }
        return "";
    }

    private static String localName(String value) {
        int hash = value.lastIndexOf('#');
        int slash = value.lastIndexOf('/');
        int colon = value.lastIndexOf(':');
        int index = Math.max(hash, Math.max(slash, colon));
        return index >= 0 && index + 1 < value.length() ? value.substring(index + 1) : value;
    }

    private static String uniqueName(String baseName, Set<String> usedNames) {
        if (usedNames.add(baseName)) {
            return baseName;
        }
        int suffix = 2;
        while (!usedNames.add(baseName + "_" + suffix)) {
            suffix++;
        }
        return baseName + "_" + suffix;
    }
}
