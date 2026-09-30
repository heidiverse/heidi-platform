// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import java.time.Instant;
import java.util.UUID;

import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotConsumer;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import tools.jackson.databind.JsonNode;

/** A named use of a key or provider owned by one identity. */
@Entity
@Table(name = "t_identity_key_slot")
public class IdentityKeySlotEntity {
    @Id
    @Column(name = "pk_identity_key_slot_id", nullable = false)
    private UUID id;

    @Column(name = "fk_identity_id", nullable = false)
    private Integer identityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot_type", nullable = false)
    private IdentityKeySlotType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "trust_system")
    private IssuerTrustSystem trustSystem;

    @Column(name = "operation")
    private String operation;

    @Enumerated(EnumType.STRING)
    @Column(name = "consumer")
    private IdentityKeySlotConsumer consumer;

    @Column(name = "key_id")
    private UUID keyId;

    @Column(name = "provider_id")
    private Integer providerId;

    @Column(name = "certificate_id")
    private UUID certificateId;

    @Column(name = "slot_order", nullable = false)
    private int order;

    @Column(name = "configuration", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode configuration;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public Integer getIdentityId() { return identityId; }
    public void setIdentityId(Integer value) { identityId = value; }
    public IdentityKeySlotType getType() { return type; }
    public void setType(IdentityKeySlotType value) { type = value; }
    public IssuerTrustSystem getTrustSystem() { return trustSystem; }
    public void setTrustSystem(IssuerTrustSystem value) { trustSystem = value; }
    public String getOperation() { return operation; }
    public void setOperation(String value) { operation = value; }
    public IdentityKeySlotConsumer getConsumer() { return consumer; }
    public void setConsumer(IdentityKeySlotConsumer value) { consumer = value; }
    public UUID getKeyId() { return keyId; }
    public void setKeyId(UUID value) { keyId = value; }
    public Integer getProviderId() { return providerId; }
    public void setProviderId(Integer value) { providerId = value; }
    public UUID getCertificateId() { return certificateId; }
    public void setCertificateId(UUID value) { certificateId = value; }
    public int getOrder() { return order; }
    public void setOrder(int value) { order = value; }
    public JsonNode getConfiguration() { return configuration; }
    public void setConfiguration(JsonNode value) { configuration = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
}
