// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.model.entity;

import org.heidiverse.heidi.issuer.model.FlowVariant;
import org.heidiverse.heidi.issuer.model.IssuanceStatus;
import org.heidiverse.heidi.issuer.model.TrustSystem;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "issuance_session")
public class IssuanceSessionEntity {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private String connectionId;
    @Column(nullable = false) private String issuerSlug;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private FlowVariant variant;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TrustSystem trustSystem;
    /** Immutable profile selection for this offer. */
    @Column(nullable = false)
    private String issuanceProfileId;
    @Column(nullable = false) private String credentialIdentifier;
    @Column(nullable = false) private String credentialVersion;
    @Column(columnDefinition = "text") private String processToken;
    @Column(unique = true) private String preAuthorizedCode;
    @Column(name = "access_token", unique = true) private String accessTokenDigest;
    private Instant accessTokenExpiresAt;
    @Column(name = "refresh_token", unique = true) private String refreshTokenDigest;
    private Instant refreshTokenExpiresAt;
    @Column(nullable = false) private long refreshTokenUsageCount;
    private String dpopJkt;
    @Column(columnDefinition = "text") private String encryptedSession;
    @Column(nullable = false) private boolean encryptedWithSessionKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private IssuanceStatus status;
    @Column(nullable = false) private Instant createdAt;
    @Column(columnDefinition = "text") private String signingSnapshot;
    private Instant offerExpiresAt;
    private Instant signingExpiresAt;
    @Column(nullable = false) private boolean signingFlowReleased;


    @PrePersist
    void initialize() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = IssuanceStatus.OFFER_CREATED;
        if (trustSystem == null) trustSystem = TrustSystem.Default;
    }

    public String getSigningSnapshot() { return signingSnapshot; }
    public void setSigningSnapshot(String value) { signingSnapshot = value; }
    public Instant getOfferExpiresAt() { return offerExpiresAt; }
    public void setOfferExpiresAt(Instant value) { offerExpiresAt = value; }
    public Instant getSigningExpiresAt() { return signingExpiresAt; }
    public void setSigningExpiresAt(Instant value) { signingExpiresAt = value; }
    public boolean isSigningFlowReleased() { return signingFlowReleased; }
    public void setSigningFlowReleased(boolean value) { signingFlowReleased = value; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
    public String getIssuerSlug() { return issuerSlug; }
    public void setIssuerSlug(String issuerSlug) { this.issuerSlug = issuerSlug; }
    public FlowVariant getVariant() { return variant; }
    public void setVariant(FlowVariant variant) { this.variant = variant; }
    public TrustSystem getTrustSystem() { return trustSystem; }
    public void setTrustSystem(TrustSystem trustSystem) { this.trustSystem = trustSystem; }
    public String getIssuanceProfileId() { return issuanceProfileId; }
    public void setIssuanceProfileId(String issuanceProfileId) { this.issuanceProfileId = issuanceProfileId; }
    public String getCredentialIdentifier() { return credentialIdentifier; }
    public void setCredentialIdentifier(String credentialIdentifier) { this.credentialIdentifier = credentialIdentifier; }
    public String getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(String credentialVersion) { this.credentialVersion = credentialVersion; }
    public String getProcessToken() { return processToken; }
    public void setProcessToken(String processToken) { this.processToken = processToken; }
    public String getPreAuthorizedCode() { return preAuthorizedCode; }
    public void setPreAuthorizedCode(String preAuthorizedCode) { this.preAuthorizedCode = preAuthorizedCode; }
    public String getAccessTokenDigest() { return accessTokenDigest; }
    public void setAccessTokenDigest(String accessTokenDigest) { this.accessTokenDigest = accessTokenDigest; }
    public Instant getAccessTokenExpiresAt() { return accessTokenExpiresAt; }
    public void setAccessTokenExpiresAt(Instant accessTokenExpiresAt) { this.accessTokenExpiresAt = accessTokenExpiresAt; }
    public String getRefreshTokenDigest() { return refreshTokenDigest; }
    public void setRefreshTokenDigest(String refreshTokenDigest) { this.refreshTokenDigest = refreshTokenDigest; }
    public Instant getRefreshTokenExpiresAt() { return refreshTokenExpiresAt; }
    public void setRefreshTokenExpiresAt(Instant refreshTokenExpiresAt) { this.refreshTokenExpiresAt = refreshTokenExpiresAt; }
    public long getRefreshTokenUsageCount() { return refreshTokenUsageCount; }
    public void setRefreshTokenUsageCount(long refreshTokenUsageCount) { this.refreshTokenUsageCount = refreshTokenUsageCount; }
    public String getDpopJkt() { return dpopJkt; }
    public void setDpopJkt(String dpopJkt) { this.dpopJkt = dpopJkt; }
    public String getEncryptedSession() { return encryptedSession; }
    public void setEncryptedSession(String encryptedSession) { this.encryptedSession = encryptedSession; }
    public boolean isEncryptedWithSessionKey() { return encryptedWithSessionKey; }
    public void setEncryptedWithSessionKey(boolean encryptedWithSessionKey) { this.encryptedWithSessionKey = encryptedWithSessionKey; }
    public IssuanceStatus getStatus() { return status; }
    public void setStatus(IssuanceStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
