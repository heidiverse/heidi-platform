// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import org.heidiverse.heidi.shared.localized.LocalizedValue;
import org.heidiverse.heidi.entity.model.issuer.IssuerCredentialEncryption;
import org.heidiverse.heidi.entity.model.issuer.IssuerDefinitionRequest;
import org.heidiverse.heidi.entity.model.issuer.IssuerDefinitionResponse;
import org.heidiverse.heidi.entity.model.issuer.IssuerFederation;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "t_issuer")
public class IssuerDefinitionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_issuer_id", nullable = false)
    private Integer id;

    @NotNull
    @Column(name = "logo", nullable = false)
    private String logo;

    @NotNull
    @Column(name = "slug", nullable = false, length = Integer.MAX_VALUE)
    private String slug;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "tenant_id")
    private String tenantId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "t_issuer_detail", joinColumns = @JoinColumn(name = "fk_issuer_id"))
    @MapKeyColumn(name = "language")
    private Map<String, String> displayName = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "default_trust_system")
    private IssuerTrustSystem defaultTrustSystem;

    /** Optional label for this identity's custom ecosystem profile. */
    @Column(name = "custom_profile_name")
    private String customProfileName;

    @Column(name = "eudi_verification_trust_anchors", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> eudiVerificationTrustAnchors = new ArrayList<>();

    /** Also trust this organisation's Issuing PKI SubCAs when verifying EUDI credentials. */
    @Column(name = "eudi_trust_own_pki", nullable = false)
    private boolean eudiTrustOwnPki = false;

    @Column(name = "swiss_verification_trust_anchor", columnDefinition = "text")
    private String swissVerificationTrustAnchor;

    /** Which JWE parameters this issuer advertises and accepts. Empty lists mean no restriction. */
    @Column(name = "credential_encryption", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private IssuerCredentialEncryption credentialEncryption;

    /** OpenID Federation settings. Null leaves the identity outside any federation. */
    @Column(name = "federation", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private IssuerFederation federation;

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public @NotNull String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Map<String, String> getDisplayName() {
        return displayName;
    }

    public void setDisplayName(Map<String, String> displayName) {
        this.displayName = displayName;
    }

    public IssuerCredentialEncryption getCredentialEncryption() {
        return credentialEncryption == null
                ? IssuerCredentialEncryption.unrestricted() : credentialEncryption;
    }

    public void setCredentialEncryption(IssuerCredentialEncryption credentialEncryption) {
        this.credentialEncryption = credentialEncryption;
    }

    public IssuerFederation getFederation() {
        return federation == null ? IssuerFederation.disabled() : federation;
    }

    public void setFederation(IssuerFederation federation) {
        this.federation = federation;
    }

    public IssuerTrustSystem getDefaultTrustSystem() { return defaultTrustSystem; }

    public void setDefaultTrustSystem(IssuerTrustSystem defaultTrustSystem) {
        this.defaultTrustSystem = defaultTrustSystem == IssuerTrustSystem.Default
                ? null : defaultTrustSystem;
    }

    public String getCustomProfileName() { return customProfileName; }

    public void setCustomProfileName(String customProfileName) {
        if (customProfileName == null) {
            this.customProfileName = null;
            return;
        }
        var trimmed = customProfileName.trim();
        this.customProfileName = trimmed.isEmpty() ? null : trimmed;
    }

    public List<String> getEudiVerificationTrustAnchors() {
        return eudiVerificationTrustAnchors == null ? List.of() : eudiVerificationTrustAnchors;
    }

    public void setEudiVerificationTrustAnchors(List<String> trustAnchors) {
        this.eudiVerificationTrustAnchors = trustAnchors == null
                ? new ArrayList<>() : new ArrayList<>(trustAnchors);
    }

    public String getSwissVerificationTrustAnchor() { return swissVerificationTrustAnchor; }

    public boolean isEudiTrustOwnPki() {
        return eudiTrustOwnPki;
    }

    public void setEudiTrustOwnPki(boolean trustOwnPki) {
        this.eudiTrustOwnPki = trustOwnPki;
    }

    public void setSwissVerificationTrustAnchor(String trustAnchor) {
        this.swissVerificationTrustAnchor = trustAnchor;
    }

    /** Convert a slot-backed identity without exposing internal key records. */
    public IssuerDefinitionResponse toSlotDefinitionResponse(Set<IssuerTrustSystem> trustSystems) {
        return new IssuerDefinitionResponse(
                this.id,
                this.slug,
                this.logo,
                LocalizedValue.fromStringMap(
                        this.displayName == null || this.displayName.isEmpty()
                                ? Map.of("en", "Unknown Issuer") : this.displayName),
                this.tenantId,
                this.getDefaultTrustSystem(),
                trustSystems == null ? Set.of() : Set.copyOf(trustSystems),
                this.customProfileName);
    }

    /** Convert DTO to Entity */
    public static IssuerDefinitionEntity fromIssuerDefinition(IssuerDefinitionRequest issuer) {
        IssuerDefinitionEntity entity = new IssuerDefinitionEntity();
        entity.setLogo(issuer.logo());
        entity.setSlug(issuer.slug());
        entity.setDisplayName(issuer.displayName().toMapWithLanguageTagKeys());
        entity.setDefaultTrustSystem(issuer.defaultTrustSystem());
        entity.setCustomProfileName(issuer.customProfileName());
        return entity;
    }
}
