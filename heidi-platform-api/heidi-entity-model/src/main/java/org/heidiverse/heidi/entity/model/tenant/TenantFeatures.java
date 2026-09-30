// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.tenant;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

/**
 * Core tenant feature flags with an open JSON surface for assembled extensions.
 *
 * <p>Extension flags are kept at the same level as the core flags so an OSS API
 * can read and write a tenant feature document without knowing private feature
 * names. This also keeps existing persisted extension flags intact when the
 * public model evolves.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class TenantFeatures {

    private boolean credentialSchemas;
    private boolean proofSchemas;
    private boolean integrations;
    private boolean apiDocs;
    private boolean user;
    private boolean tenant;
    private boolean settings;

    private final Map<String, Boolean> extensionFeatures = new LinkedHashMap<>();

    public TenantFeatures() {}

    public TenantFeatures(
            @NotNull boolean credentialSchemas,
            @NotNull boolean proofSchemas,
            @NotNull boolean integrations,
            @NotNull boolean apiDocs,
            @NotNull boolean user,
            @NotNull boolean tenant,
            @NotNull boolean settings) {
        this.credentialSchemas = credentialSchemas;
        this.proofSchemas = proofSchemas;
        this.integrations = integrations;
        this.apiDocs = apiDocs;
        this.user = user;
        this.tenant = tenant;
        this.settings = settings;
    }

    public static TenantFeatures allEnabled() {
        return new TenantFeatures(
                true, true, true, true, true, true, true);
    }

    public boolean credentialSchemas() { return credentialSchemas; }
    public boolean isCredentialSchemas() { return credentialSchemas; }
    public void setCredentialSchemas(boolean value) { credentialSchemas = value; }
    public boolean proofSchemas() { return proofSchemas; }
    public boolean isProofSchemas() { return proofSchemas; }
    public void setProofSchemas(boolean value) { proofSchemas = value; }
    public boolean integrations() { return integrations; }
    public boolean isIntegrations() { return integrations; }
    public void setIntegrations(boolean value) { integrations = value; }
    public boolean apiDocs() { return apiDocs; }
    public boolean isApiDocs() { return apiDocs; }
    public void setApiDocs(boolean value) { apiDocs = value; }
    public boolean user() { return user; }
    public boolean isUser() { return user; }
    public void setUser(boolean value) { user = value; }
    public boolean tenant() { return tenant; }
    public boolean isTenant() { return tenant; }
    public void setTenant(boolean value) { tenant = value; }
    public boolean settings() { return settings; }
    public boolean isSettings() { return settings; }
    public void setSettings(boolean value) { settings = value; }
    @JsonAnyGetter
    public Map<String, Boolean> getExtensionFeatures() { return extensionFeatures; }

    @JsonAnySetter
    public void setExtensionFeature(String name, Boolean enabled) {
        extensionFeatures.put(name, enabled);
    }
}
