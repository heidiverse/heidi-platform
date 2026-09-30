// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import org.heidiverse.heidi.entity.model.tenant.TenantFeatures;

import tools.jackson.databind.JsonNode;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "t_tenant")
public class TenantEntity {

    private static final String DEFAULT_LANGUAGE = "en";

    @Id
    @Column(name = "pk_tenant_id", nullable = false, unique = true)
    private String tenantId;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "thumbnail")
    private String thumbnail;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "registrar_rp_id")
    private UUID registrarRpId;

    @Column(name = "verifier_key_uri")
    private String verifierKeyUri;

    @Column(name = "verifier_provider_id")
    private Integer verifierProviderId;

    @Column(name = "verifier_key_id")
    private UUID verifierKeyId;

    @Column(name = "verifier_key_version_id")
    private UUID verifierKeyVersionId;

    @Column(name = "verifier_public_jwk", columnDefinition = "text")
    private String verifierPublicJwk;

    @Column(name = "verifier_certificate_chain", columnDefinition = "text")
    private String verifierCertificateChain;

    @Column(name = "features", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private TenantFeatures features;

    /** Public configuration consumed by client-side integrations. */
    @Column(name = "client_configuration", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode clientConfiguration;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "t_tenant_translations",
            joinColumns = @JoinColumn(name = "fk_tenant_id"))
    @Column(name = "translation")
    private List<String> translations = new ArrayList<>(List.of(DEFAULT_LANGUAGE));

    @Column(name = "default_language", nullable = false)
    private String defaultLanguage = DEFAULT_LANGUAGE;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "t_tenant_issuers",
            joinColumns = @JoinColumn(name = "fk_tenant_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_issuer_id"))
    private List<IssuerDefinitionEntity> issuers = new ArrayList<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "t_tenant_trust_registries",
            joinColumns = @JoinColumn(name = "fk_tenant_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_trust_registry_id"))
    private List<TrustRegistryEntity> trustRegistries = new ArrayList<>();

    public boolean hasVerifierCertificate() {
        return verifierKeyUri != null && !verifierKeyUri.isEmpty();
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

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

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public List<String> getTranslations() {
        return translations;
    }

    public void setTranslations(List<String> translations) {
        this.translations = new ArrayList<>(translations);
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public List<Integer> getIssuerIds() {
        return issuers.stream().map(IssuerDefinitionEntity::getId).toList();
    }

    public List<IssuerDefinitionEntity> getIssuers() {
        return List.copyOf(issuers);
    }

    public void setIssuers(List<IssuerDefinitionEntity> issuers) {
        this.issuers = issuers;
    }

    public List<TrustRegistryEntity> getTrustRegistries() {
        return trustRegistries.stream().toList();
    }

    public void addTrustRegistry(TrustRegistryEntity trustRegistry) {
        if (this.trustRegistries == null) {
            this.trustRegistries = new ArrayList<>();
        }
        this.trustRegistries.add(trustRegistry);
    }

    public UUID getRegistrarRpId() {
        return registrarRpId;
    }

    public void setRegistrarRpId(UUID registrarRpId) {
        this.registrarRpId = registrarRpId;
    }

    public String getVerifierKeyUri() { return verifierKeyUri; }
    public void setVerifierKeyUri(String verifierKeyUri) { this.verifierKeyUri = verifierKeyUri; }
    public Integer getVerifierProviderId() { return verifierProviderId; }
    public void setVerifierProviderId(Integer verifierProviderId) { this.verifierProviderId = verifierProviderId; }
    public UUID getVerifierKeyId() { return verifierKeyId; }
    public void setVerifierKeyId(UUID verifierKeyId) { this.verifierKeyId = verifierKeyId; }
    public UUID getVerifierKeyVersionId() { return verifierKeyVersionId; }
    public void setVerifierKeyVersionId(UUID verifierKeyVersionId) { this.verifierKeyVersionId = verifierKeyVersionId; }
    public String getVerifierPublicJwk() { return verifierPublicJwk; }
    public void setVerifierPublicJwk(String verifierPublicJwk) { this.verifierPublicJwk = verifierPublicJwk; }
    public String getVerifierCertificateChain() { return verifierCertificateChain; }
    public void setVerifierCertificateChain(String verifierCertificateChain) {
        this.verifierCertificateChain = verifierCertificateChain;
    }

    public TenantFeatures getFeatures() {
        return features;
    }

    public void setFeatures(TenantFeatures features) {
        this.features = features;
    }

    public JsonNode getClientConfiguration() {
        return clientConfiguration;
    }

    public void setClientConfiguration(JsonNode clientConfiguration) {
        this.clientConfiguration = clientConfiguration;
    }
}
