// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.entity;

import tools.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.UUID;

/** Server-owned state connecting a process with its limited client capability. */
@Entity
@Table(name = "t_integration_process")
public class IntegrationProcessEntity {

    @Id
    @Column(name = "process_id", nullable = false, updatable = false)
    private UUID processId;

    @Column(name = "process_token_hash", nullable = false, unique = true)
    private String processTokenHash;

    @Column(name = "client_interaction_token_hash", unique = true)
    private String clientInteractionTokenHash;

    @Column(name = "integration_credential_hash", nullable = false)
    private String integrationCredentialHash;

    @Column(name = "coordinator_connection_id")
    private String coordinatorConnectionId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "proof_scheme_id")
    private String proofSchemeId;

    @Column(name = "include_vp_token", nullable = false)
    private boolean includeVpToken;

    @Column(name = "use_dc_api", nullable = false)
    private boolean useDcApi;

    /** Tenant owning the credential schema or proof scheme used by this process. */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "tx_code")
    private String txCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "client_interaction_data", columnDefinition = "JSONB")
    private JsonNode clientInteractionData;

    /** Resolved public configuration returned to the browser with the client capability. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "client_configuration", columnDefinition = "JSONB")
    private JsonNode clientConfiguration;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "client_display_claims", nullable = false, columnDefinition = "JSONB")
    private JsonNode clientDisplayClaims;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();

    public UUID getProcessId() {
        return processId;
    }

    public void setProcessId(UUID processId) {
        this.processId = processId;
    }

    public String getProcessTokenHash() {
        return processTokenHash;
    }

    public void setProcessTokenHash(String processTokenHash) {
        this.processTokenHash = processTokenHash;
    }

    public String getClientInteractionTokenHash() {
        return clientInteractionTokenHash;
    }

    public void setClientInteractionTokenHash(String clientInteractionTokenHash) {
        this.clientInteractionTokenHash = clientInteractionTokenHash;
    }

    public String getIntegrationCredentialHash() {
        return integrationCredentialHash;
    }

    public void setIntegrationCredentialHash(String integrationCredentialHash) {
        this.integrationCredentialHash = integrationCredentialHash;
    }

    public String getCoordinatorConnectionId() {
        return coordinatorConnectionId;
    }

    public void setCoordinatorConnectionId(String coordinatorConnectionId) {
        this.coordinatorConnectionId = coordinatorConnectionId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getProofSchemeId() {
        return proofSchemeId;
    }

    public void setProofSchemeId(String proofSchemeId) {
        this.proofSchemeId = proofSchemeId;
    }

    public boolean isIncludeVpToken() {
        return includeVpToken;
    }

    public void setIncludeVpToken(boolean includeVpToken) {
        this.includeVpToken = includeVpToken;
    }

    public boolean isUseDcApi() {
        return useDcApi;
    }

    public void setUseDcApi(boolean useDcApi) {
        this.useDcApi = useDcApi;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTxCode() {
        return txCode;
    }

    public void setTxCode(String txCode) {
        this.txCode = txCode;
    }

    public JsonNode getClientInteractionData() {
        return clientInteractionData;
    }

    public void setClientInteractionData(JsonNode clientInteractionData) {
        this.clientInteractionData = clientInteractionData;
    }

    public JsonNode getClientConfiguration() {
        return clientConfiguration;
    }

    public void setClientConfiguration(JsonNode clientConfiguration) {
        this.clientConfiguration = clientConfiguration;
    }

    public JsonNode getClientDisplayClaims() {
        return clientDisplayClaims;
    }

    public void setClientDisplayClaims(JsonNode clientDisplayClaims) {
        this.clientDisplayClaims = clientDisplayClaims;
    }

    public ZonedDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(ZonedDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
