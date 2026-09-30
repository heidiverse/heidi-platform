// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.tenant;

import jakarta.validation.constraints.Size;

import tools.jackson.databind.JsonNode;

import java.util.List;

public class TenantRequest {

    private String displayName;

    private String thumbnail;

    @Size(max = 100, message = "{tenant.languages.max}")
    private List<String> translations;

    private String defaultLanguage;

    private List<Integer> issuerIds;

    /** Public configuration consumed by client-side integrations. */
    private JsonNode clientConfiguration;

    /** Clears the organisation override and restores platform-level defaults. */
    private boolean clearClientConfiguration;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public List<String> getTranslations() {
        return translations;
    }

    public void setTranslations(List<String> translations) {
        this.translations = translations;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public List<Integer> getIssuerIds() {
        return issuerIds;
    }

    public void setIssuerIds(List<Integer> issuerIds) {
        this.issuerIds = issuerIds;
    }

    public JsonNode getClientConfiguration() {
        return clientConfiguration;
    }

    public void setClientConfiguration(JsonNode clientConfiguration) {
        this.clientConfiguration = clientConfiguration;
    }

    public boolean isClearClientConfiguration() {
        return clearClientConfiguration;
    }

    public void setClearClientConfiguration(boolean clearClientConfiguration) {
        this.clearClientConfiguration = clearClientConfiguration;
    }
}
