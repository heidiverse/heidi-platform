// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.credentialscheme;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;
import java.util.Map;

/** Typed representation of an SD-JWT VC type metadata document. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TypeMetadata(
        String vct,
        String name,
        String description,
        JsonSchema schema,
        List<Display> display,
        List<Claim> claims) {

    public record JsonSchema(
            @JsonProperty("$schema") String schema,
            String type,
            Map<String, JsonSchemaProperty> properties,
            List<String> required) {}

    public record JsonSchemaProperty(String type) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Display(String lang, String name, Rendering rendering) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Rendering(
            SimpleRendering simple,
            OcaRendering oca,
            @JsonProperty("svg_templates") List<SvgTemplate> svgTemplates) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SimpleRendering(
            @JsonProperty("background_color") String backgroundColor,
            @JsonProperty("text_color") String textColor) {}

    public record OcaRendering(
            String uri, @JsonProperty("uri#integrity") String uriIntegrity) {}

    public record SvgTemplate(
            String uri,
            @JsonProperty("uri#integrity") String uriIntegrity,
            SvgTemplateProperties properties) {}

    public record SvgTemplateProperties(
            String orientation, @JsonProperty("color_scheme") String colorScheme) {}

    public record Claim(
            List<String> path,
            List<ClaimDisplay> display,
            SelectiveDisclosure sd,
            @JsonProperty("svg_id") String svgId) {}

    public record ClaimDisplay(String lang, String label, String description) {}

    public enum SelectiveDisclosure {
        ALLOWED("allowed"),
        NEVER("never");

        private final String value;

        SelectiveDisclosure(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }
    }
}
