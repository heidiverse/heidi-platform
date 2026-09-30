// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeState;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialType;
import org.heidiverse.heidi.entity.model.issuer.IssuerKeyType;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.coordinator.model.oid4vci.CredentialOfferType;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "t_credential_scheme")
public class CredentialSchemeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_credential_scheme_id", nullable = false)
    private Integer id;

    @NotNull
    @Column(name = "credential_identifier", nullable = false)
    private String credentialIdentifier;

    @Column(name = "version")
    private String version;

    @NotNull
    @Column(name = "key_type", nullable = false, length = Integer.MAX_VALUE)
    @Enumerated(EnumType.STRING)
    private IssuerKeyType keyType;

    @Column(name = "doctype")
    private String doctype;

    @Column(name = "namespace")
    private String namespace;

    @Column(name = "vct")
    private String vct;

    @Column(name = "bbs_credential_type")
    private String bbsCredentialType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private CredentialSchemeState state;

    @NotNull
    @Column(name = "uuid", nullable = false)
    private UUID uuid;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "template_id")
    private UUID templateId;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "max_batch_size", nullable = false)
    private Integer maxBatchSize = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_issuer_id", nullable = false)
    @NotNull
    private IssuerDefinitionEntity issuerDefinition;

    @OneToMany(mappedBy = "credentialSchemeEntity", fetch = FetchType.LAZY)
    private List<ProofSchemeCredentialSchemeEntity> proofSchemeCredentialSchemeEntities =
            new ArrayList<>();

    @OneToMany(mappedBy = "credentialSchemeEntity", fetch = FetchType.LAZY)
    private List<CredentialSchemeStyleEntity> styles = new ArrayList<>();

    @Column(name = "supported_credential_types", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private Set<CredentialType> supportedCredentialTypes;

    @Column(name = "iss_claim_override")
    private String issClaimOverride;

    @Column(name = "kid_override")
    private String kidOverride;

    @Column(name = "signing_key_ids", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<IssuerTrustSystem, String> signingKeyIds;

    @Column(name = "fk_status_list_id")
    private UUID statusListId;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_offer_type", nullable = false)
    private CredentialOfferType credentialOfferType = CredentialOfferType.VALUE;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_trust_system")
    private IssuerTrustSystem defaultTrustSystem;

    @NotNull
    @Column(name = "issuance_profile_id", nullable = false)
    private String issuanceProfileId;

    private static final Set<CredentialType> DEFAULT_CREDENTIAL_TYPES =
            Set.of(CredentialType.SD_JWT, CredentialType.MSO_MDOC, CredentialType.ZKP_VC, CredentialType.W3C_VCDM, CredentialType.OPENBADGES);

    public IssuerTrustSystem getDefaultTrustSystem() {
        return defaultTrustSystem == IssuerTrustSystem.Default ? null : defaultTrustSystem;
    }

    public void setDefaultTrustSystem(IssuerTrustSystem defaultTrustSystem) {
        this.defaultTrustSystem = defaultTrustSystem == IssuerTrustSystem.Default
                ? null : defaultTrustSystem;
    }

    public @NotNull String getIssuanceProfileId() {
        return issuanceProfileId;
    }

    public void setIssuanceProfileId(@NotNull String issuanceProfileId) {
        this.issuanceProfileId = issuanceProfileId;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }

    public Integer getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(Integer maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public String getDoctype() {
        return doctype;
    }

    public void setDoctype(String doctype) {
        this.doctype = doctype;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getVct() {
        return vct;
    }

    public void setVct(String vct) {
        this.vct = vct;
    }

    public String getBbsCredentialType() {
        return bbsCredentialType;
    }

    public void setBbsCredentialType(String bbsCredentialType) {
        this.bbsCredentialType = bbsCredentialType;
    }

    public @NotNull String getCredentialIdentifier() {
        return credentialIdentifier;
    }

    public void setCredentialIdentifier(@NotNull String credentialIdentifier) {
        this.credentialIdentifier = credentialIdentifier;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public CredentialSchemeState getState() {
        return state;
    }

    public void setState(CredentialSchemeState state) {
        this.state = state;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public List<ProofSchemeCredentialSchemeEntity> getProofSchemeCredentialSchemeEntities() {
        return proofSchemeCredentialSchemeEntities;
    }

    public void setProofSchemeCredentialSchemeEntities(
            List<ProofSchemeCredentialSchemeEntity> proofSchemeCredentialSchemeEntities) {
        this.proofSchemeCredentialSchemeEntities = proofSchemeCredentialSchemeEntities;
    }

    public IssuerDefinitionEntity getIssuerDefinition() {
        return issuerDefinition;
    }

    public void setIssuerDefinition(IssuerDefinitionEntity issuerDefinition) {
        this.issuerDefinition = issuerDefinition;
    }

    public @NotNull UUID getUuid() {
        return uuid;
    }

    public void setUuid(@NotNull UUID uuid) {
        this.uuid = uuid;
    }

    public List<CredentialSchemeStyleEntity> getStyles() {
        return styles;
    }

    public @NotNull IssuerKeyType getKeyType() {
        return keyType;
    }

    public void setKeyType(@NotNull IssuerKeyType keyType) {
        this.keyType = keyType;
    }

    public Set<CredentialType> getSupportedCredentialTypes() {
        return supportedCredentialTypes == null || supportedCredentialTypes.isEmpty()
                ? DEFAULT_CREDENTIAL_TYPES
                : supportedCredentialTypes;
    }

    public void setSupportedCredentialTypes(Set<CredentialType> supportedCredentialTypes) {
        this.supportedCredentialTypes =
                (supportedCredentialTypes == null || supportedCredentialTypes.isEmpty())
                        ? DEFAULT_CREDENTIAL_TYPES
                        : supportedCredentialTypes;
    }

    public String getIssClaimOverride() {
        return issClaimOverride;
    }

    public void setIssClaimOverride(String issClaimOverride) {
        this.issClaimOverride = issClaimOverride;
    }

    public String getKidOverride() {
        return kidOverride;
    }

    public void setKidOverride(String kidOverride) {
        this.kidOverride = kidOverride;
    }

    public Map<IssuerTrustSystem, String> getSigningKeyIds() {
        return signingKeyIds == null ? Map.of() : signingKeyIds;
    }

    public void setSigningKeyIds(Map<IssuerTrustSystem, String> signingKeyIds) {
        this.signingKeyIds = signingKeyIds == null ? Map.of() : signingKeyIds;
    }

    public UUID getStatusListId() {
        return statusListId;
    }

    public void setStatusListId(UUID statusListId) {
        this.statusListId = statusListId;
    }

    public CredentialOfferType getCredentialOfferType() {
        return credentialOfferType == null ? CredentialOfferType.VALUE : credentialOfferType;
    }

    public void setCredentialOfferType(CredentialOfferType credentialOfferType) {
        this.credentialOfferType = credentialOfferType == null
                ? CredentialOfferType.VALUE : credentialOfferType;
    }
}
