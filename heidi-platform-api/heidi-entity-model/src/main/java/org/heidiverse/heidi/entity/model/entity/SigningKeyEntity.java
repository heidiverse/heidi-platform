// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.heidiverse.heidi.entity.model.issuer.KeyRotationMode;

/** Platform metadata for one logical signing key and its provider-backed versions. */
@Entity
@Table(
        name = "t_signing_key",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_signing_key_tenant_key",
                columnNames = {"tenant_id", "logical_key_id"}))
public class SigningKeyEntity {
    public static final long DEFAULT_ROTATION_SECONDS = java.time.Duration.ofDays(30).toSeconds();
    public static final long DEFAULT_GRACE_SECONDS = java.time.Duration.ofDays(1).toSeconds();
    @Id
    @Column(name = "pk_key_id", nullable = false)
    private UUID id;

    /** Null denotes a platform-owned key; otherwise the tenant that owns it. */
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "logical_key_id", nullable = false)
    private String logicalKeyId;

    @Column(name = "provider_id", nullable = false)
    private Integer providerId;

    /** Nullable while the first version is inserted; see the circular FK in the migration. */
    @Column(name = "active_version_id")
    private UUID activeVersionId;

    /** Time in seconds during which the previous version remains authorized after rotation. */
    @Column(name = "rotation_grace_period_seconds", nullable = false)
    private Long rotationGracePeriodSeconds = DEFAULT_GRACE_SECONDS;

    @Enumerated(EnumType.STRING)
    @Column(name = "rotation_mode", nullable = false)
    private KeyRotationMode rotationMode = KeyRotationMode.MANUAL;

    @Column(name = "rotation_interval_seconds")
    private Long rotationIntervalSeconds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getLogicalKeyId() { return logicalKeyId; }
    public void setLogicalKeyId(String logicalKeyId) { this.logicalKeyId = logicalKeyId; }
    public Integer getProviderId() { return providerId; }
    public void setProviderId(Integer providerId) { this.providerId = providerId; }
    public UUID getActiveVersionId() { return activeVersionId; }
    public void setActiveVersionId(UUID activeVersionId) { this.activeVersionId = activeVersionId; }
    public Long getRotationGracePeriodSeconds() { return rotationGracePeriodSeconds; }
    public void setRotationGracePeriodSeconds(Long value) { rotationGracePeriodSeconds = value; }
    public Long getRotationIntervalSeconds() { return rotationIntervalSeconds; }
    public void setRotationIntervalSeconds(Long value) { rotationIntervalSeconds = value; }
    public KeyRotationMode getRotationMode() { return rotationMode; }
    public void setRotationMode(KeyRotationMode value) { rotationMode = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
