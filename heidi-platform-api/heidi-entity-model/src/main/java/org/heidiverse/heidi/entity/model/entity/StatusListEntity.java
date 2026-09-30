// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.heidiverse.heidi.entity.model.statuslist.StatusListPublishMode;
import org.heidiverse.heidi.entity.model.statuslist.StatusListType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "t_status_list")
public class StatusListEntity {
    @Id
    @Column(name = "pk_status_list_id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusListType type;

    @Column(nullable = false)
    private int bits;

    @Column(name = "entry_count", nullable = false)
    private int entryCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "publish_mode", nullable = false)
    private StatusListPublishMode publishMode;

    @Column
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private URI endpoint;

    @Column(name = "signing_key_id", nullable = false)
    private UUID signingKeyId;

    @Column
    private Long ttl;

    @Column(name = "status_data", nullable = false)
    private byte[] statusData;

    @Column(name = "published_token")
    private String publishedToken;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "swiss_status_list_id")
    private UUID swissStatusListId;

    @Column(name = "swiss_status_list_url")
    private String swissStatusListUrl;

    @Column(name = "swiss_published_at")
    private Instant swissPublishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public StatusListType getType() { return type; }
    public void setType(StatusListType type) { this.type = type; }
    public int getBits() { return bits; }
    public void setBits(int bits) { this.bits = bits; }
    public int getEntryCount() { return entryCount; }
    public void setEntryCount(int entryCount) { this.entryCount = entryCount; }
    public StatusListPublishMode getPublishMode() { return publishMode; }
    public void setPublishMode(StatusListPublishMode publishMode) { this.publishMode = publishMode; }
    public URI getEndpoint() { return endpoint; }
    public void setEndpoint(URI endpoint) { this.endpoint = endpoint; }
    public UUID getSigningKeyId() { return signingKeyId; }
    public void setSigningKeyId(UUID signingKeyId) { this.signingKeyId = signingKeyId; }
    public Long getTtl() { return ttl; }
    public void setTtl(Long ttl) { this.ttl = ttl; }
    public byte[] getStatusData() { return statusData; }
    public void setStatusData(byte[] statusData) { this.statusData = statusData; }
    public String getPublishedToken() { return publishedToken; }
    public void setPublishedToken(String publishedToken) { this.publishedToken = publishedToken; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public UUID getSwissStatusListId() { return swissStatusListId; }
    public void setSwissStatusListId(UUID swissStatusListId) { this.swissStatusListId = swissStatusListId; }
    public String getSwissStatusListUrl() { return swissStatusListUrl; }
    public void setSwissStatusListUrl(String swissStatusListUrl) { this.swissStatusListUrl = swissStatusListUrl; }
    public Instant getSwissPublishedAt() { return swissPublishedAt; }
    public void setSwissPublishedAt(Instant swissPublishedAt) { this.swissPublishedAt = swissPublishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
