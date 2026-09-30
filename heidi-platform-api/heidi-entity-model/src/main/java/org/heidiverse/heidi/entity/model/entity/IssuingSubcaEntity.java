// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Organisation CA metadata; its private key remains with the signing provider. */
@Entity
@Table(name = "t_issuing_subca")
public class IssuingSubcaEntity {
    @Id
    @Column(name = "pk_subca_id")
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
    @Column(name = "key_id", nullable = false)
    private UUID keyId;
    @Column(name = "key_version_id", nullable = false)
    private UUID keyVersionId;
    @Column(name = "subject_dn", nullable = false)
    private String subjectDn;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "certificate_chain", nullable = false, columnDefinition = "jsonb")
    private List<String> certificateChain = List.of();
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "certificate_history", nullable = false, columnDefinition = "jsonb")
    private List<String> certificateHistory = List.of();
    @Column(name = "certificate_source")
    private String certificateSource;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String value) { tenantId = value; }
    public UUID getKeyId() { return keyId; }
    public void setKeyId(UUID value) { keyId = value; }
    public UUID getKeyVersionId() { return keyVersionId; }
    public void setKeyVersionId(UUID value) { keyVersionId = value; }
    public String getSubjectDn() { return subjectDn; }
    public void setSubjectDn(String value) { subjectDn = value; }
    public List<String> getCertificateChain() { return certificateChain; }
    public void setCertificateChain(List<String> value) { certificateChain = List.copyOf(value); }
    public List<String> getCertificateHistory() { return certificateHistory; }
    public void setCertificateHistory(List<String> value) { certificateHistory = List.copyOf(value); }
    public String getCertificateSource() { return certificateSource; }
    public void setCertificateSource(String value) { certificateSource = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
