// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import org.heidiverse.heidi.entity.model.proofscheme.TrustedAuthorityQuery;
import org.heidiverse.heidi.entity.model.proofscheme.ValidationMode;
import org.heidiverse.heidi.entity.model.proofscheme.VerifierInfo;
import org.heidiverse.heidi.entity.model.proofscheme.VerifierClientIdScheme;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "t_proof_scheme")
public class ProofSchemeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_proof_scheme_id", nullable = false)
    private Integer id;

    @NotNull
    @Column(name = "title", nullable = false)
    private String title;

    @NotNull
    @Column(name = "purpose", nullable = false)
    private String purpose;

    @NotNull
    @Column(name = "archived", nullable = false)
    private boolean archived;

    @Column(name = "validation_logic")
    private String validationLogic;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "validation_mode", nullable = false)
    private ValidationMode validationMode = ValidationMode.DISABLED;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "redirect_uri")
    private String redirectUri;

    @NotNull
    @Column(name = "uuid", nullable = false)
    private UUID uuid;

    @Column(name = "tenant_id")
    private String tenantId;

    @OneToMany(mappedBy = "proofSchemeEntity")
    private List<ProofSchemeCredentialSchemeEntity> proofSchemeCredentialSchemeEntities =
            new ArrayList<>();

    @Column(name = "registration_certificate")
    private String registrationCertificate;

    /**
     * Explicit verifier identity. When absent, the first requested credential's identity is used.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_verifier_identity_id")
    private IssuerDefinitionEntity verifierIdentity;

    @Column(name = "verifier_signing_key_id")
    private String verifierSigningKeyId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_proof_signing_provider_id")
    private SigningProviderEntity proofSigningProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "verifier_client_id_scheme")
    private VerifierClientIdScheme verifierClientIdScheme;

    @Column(name = "swiss_identity_statement", columnDefinition = "text")
    private String swissIdentityStatement;

    @Column(name = "swiss_verification_query_statement", columnDefinition = "text")
    private String swissVerificationQueryStatement;

    @Column(name = "swiss_verification_query_enabled", nullable = false)
    private boolean swissVerificationQueryEnabled = true;

    @Column(name = "always_include_dcql_query", nullable = false)
    private boolean alwaysIncludeDcqlQuery;


    @Column(name = "swiss_protected_verification_statements", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private List<String> swissProtectedVerificationStatements = new ArrayList<>();

    @Column(name = "trusted_authorities", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private List<TrustedAuthorityQuery> trustedAuthorities = new ArrayList<>();

    @Column(name = "verifier_infos", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private List<VerifierInfo> verifierInfos = new ArrayList<>();

    @NotNull
    @Column(name = "presentation_profile_id", nullable = false)
    private String presentationProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verifier_trust_system")
    private IssuerTrustSystem verifierTrustSystem;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public @NotNull String getTitle() {
        return title;
    }

    public void setTitle(@NotNull String title) {
        this.title = title;
    }

    public @NotNull String getPurpose() {
        return purpose;
    }

    public void setPurpose(@NotNull String purpose) {
        this.purpose = purpose;
    }

    @NotNull
    public boolean isArchived() {
        return archived;
    }

    public void setArchived(@NotNull boolean archived) {
        this.archived = archived;
    }

    public @NotNull Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(@NotNull Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ProofSchemeCredentialSchemeEntity> getProofSchemeCredentialSchemeEntities() {
        return proofSchemeCredentialSchemeEntities;
    }

    public void setProofSchemeCredentialSchemeEntities(
            List<ProofSchemeCredentialSchemeEntity> proofSchemeCredentialSchemeEntities) {
        this.proofSchemeCredentialSchemeEntities = proofSchemeCredentialSchemeEntities;
    }

    public @NotNull UUID getUuid() {
        return uuid;
    }

    public void setUuid(@NotNull UUID uuid) {
        this.uuid = uuid;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getValidationLogic() {
        return validationLogic;
    }

    public void setValidationLogic(String validationLogic) {
        this.validationLogic = validationLogic;
    }

    public ValidationMode getValidationMode() {
        return validationMode;
    }

    public void setValidationMode(ValidationMode validationMode) {
        this.validationMode = validationMode == null ? ValidationMode.DISABLED : validationMode;
    }

    public String getRegistrationCertificate() {
        return registrationCertificate;
    }

    public void setRegistrationCertificate(String registrationCertificate) {
        this.registrationCertificate = registrationCertificate;
    }

    public IssuerDefinitionEntity getVerifierIdentity() {
        return verifierIdentity;
    }

    public void setVerifierIdentity(IssuerDefinitionEntity value) {
        this.verifierIdentity = value;
    }

    public String getVerifierSigningKeyId() {
        return verifierSigningKeyId;
    }

    public void setVerifierSigningKeyId(String value) {
        this.verifierSigningKeyId = value;
    }

    public SigningProviderEntity getProofSigningProvider() {
        return proofSigningProvider;
    }

    public void setProofSigningProvider(SigningProviderEntity value) {
        this.proofSigningProvider = value;
    }

    public VerifierClientIdScheme getVerifierClientIdScheme() {
        return verifierClientIdScheme;
    }

    public void setVerifierClientIdScheme(VerifierClientIdScheme value) {
        this.verifierClientIdScheme = value;
    }

    public String getSwissIdentityStatement() {
        return swissIdentityStatement;
    }

    public void setSwissIdentityStatement(String value) {
        this.swissIdentityStatement = value;
    }

    public String getSwissVerificationQueryStatement() {
        return swissVerificationQueryStatement;
    }

    public void setSwissVerificationQueryStatement(String value) {
        this.swissVerificationQueryStatement = value;
    }

    public boolean isSwissVerificationQueryEnabled() {
        return swissVerificationQueryEnabled;
    }

    public void setSwissVerificationQueryEnabled(boolean value) {
        this.swissVerificationQueryEnabled = value;
    }

    public boolean isAlwaysIncludeDcqlQuery() {
        return alwaysIncludeDcqlQuery;
    }

    public void setAlwaysIncludeDcqlQuery(boolean value) {
        this.alwaysIncludeDcqlQuery = value;
    }

    public List<String> getSwissProtectedVerificationStatements() {
        return swissProtectedVerificationStatements == null
                ? List.of()
                : swissProtectedVerificationStatements;
    }

    public void setSwissProtectedVerificationStatements(List<String> value) {
        this.swissProtectedVerificationStatements =
                value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    public List<TrustedAuthorityQuery> getTrustedAuthorities() {
        return trustedAuthorities == null ? List.of() : trustedAuthorities;
    }

    public void setTrustedAuthorities(List<TrustedAuthorityQuery> value) {
        this.trustedAuthorities = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    public List<VerifierInfo> getVerifierInfos() {
        return verifierInfos == null ? List.of() : verifierInfos;
    }

    public void setVerifierInfos(List<VerifierInfo> value) {
        this.verifierInfos = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    public @NotNull String getPresentationProfileId() {
        return presentationProfileId;
    }

    public void setPresentationProfileId(@NotNull String presentationProfileId) {
        this.presentationProfileId = presentationProfileId;
    }

    public IssuerTrustSystem getVerifierTrustSystem() {
        return verifierTrustSystem;
    }

    public void setVerifierTrustSystem(IssuerTrustSystem verifierTrustSystem) {
        this.verifierTrustSystem = verifierTrustSystem == IssuerTrustSystem.Default
                ? null : verifierTrustSystem;
    }
}
