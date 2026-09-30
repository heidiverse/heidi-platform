// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import org.heidiverse.heidi.entity.model.integration.IntegrationDetail;
import org.heidiverse.heidi.entity.model.integration.IntegrationOverviewEntry;

import org.heidiverse.heidi.entity.model.integration.IntegrationScope;
import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "t_integration")
public class IntegrationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String displayName;

    @Column(name = "credential_identifiers", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> credentialIdentifiers;

    @Column(name = "scopes", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<IntegrationScope> scopes;

    @Column(nullable = false, unique = true)
    private String apiKey;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private boolean isPublic = false;

    public IntegrationEntity() {}

    public IntegrationEntity(
            UUID id,
            String displayName,
            List<String> credentialIdentifiers,
            List<IntegrationScope> scopes,
            String apiKey,
            String tenantId,
            boolean isPublic) {
        this.id = id;
        this.displayName = displayName;
        this.credentialIdentifiers = credentialIdentifiers;
        this.scopes = scopes;
        this.apiKey = apiKey;
        this.tenantId = tenantId;
        this.isPublic = isPublic;
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<String> getCredentialIdentifiers() {
        return credentialIdentifiers;
    }

    public void setCredentialIdentifiers(List<String> credentialIdentifiers) {
        this.credentialIdentifiers = credentialIdentifiers;
    }

    public List<IntegrationScope> getScopes() {
        return scopes;
    }

    public void setScopes(List<IntegrationScope> scopes) {
        this.scopes = scopes;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    // Helpers
    public IntegrationDetail toDetail() {
        return new IntegrationDetail(
                this.getId(),
                this.getDisplayName(),
                this.getCredentialIdentifiers(),
                this.getScopes(),
                this.getApiKey(),
                this.getTenantId(),
                this.isPublic());
    }

    public IntegrationOverviewEntry toOverviewEntry() {
        return new IntegrationOverviewEntry(
                this.getId(),
                this.getDisplayName(),
                this.getCredentialIdentifiers(),
                this.getScopes(),
                this.getTenantId(),
                this.isPublic());
    }
}
