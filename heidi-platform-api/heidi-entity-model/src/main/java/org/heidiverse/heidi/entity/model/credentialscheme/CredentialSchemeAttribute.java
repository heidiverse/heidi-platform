// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.credentialscheme;

import org.heidiverse.heidi.shared.localized.LocalizedValue;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CredentialSchemeAttribute(
        @NotNull Integer id,
        @NotNull String name,
        @NotNull AttributeType type,
        Boolean isArray,
        Boolean isSensitive,
        Boolean isDisclosable,
        @Valid LocalizedValue<@NotNull String> displayName,
        @JsonProperty(defaultValue = "{}") Map<String, String> attributeNameOverrides) {

    // Default value for `isSensitive` (false)
    public Boolean isSensitive() {
        return isSensitive != null && isSensitive;
    }

    // Default value for `isArray` (false)
    public Boolean isArray() {
        return isArray != null && isArray;
    }

    // Default value for `isDisclosable` (true)
    public Boolean isDisclosable() {
        return isDisclosable != null ? isDisclosable : true;
    }

    // Default value for `attributeNameOverrides`
    public Map<String, String> attributeNameOverrides() {
        return attributeNameOverrides != null ? attributeNameOverrides : Map.of();
    }
}
