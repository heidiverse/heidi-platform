// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.entity;

import org.heidiverse.heidi.verifier.model.converter.HashMapConverter;

import tools.jackson.databind.JsonNode;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "t_verification_submission")
public class VerificationSubmissionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "pk_verification_submission_id", nullable = false, unique = true)
    private Integer id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = HashMapConverter.class)
    @Column(name = "disclosures", nullable = false)
    private Map<String, Object> disclosures;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vp_token", columnDefinition = "jsonb")
    private JsonNode vpToken;

    public static VerificationSubmissionEntity from(
            final String transactionId, final Map<String, Object> disclosures) {
        return from(transactionId, disclosures, null);
    }

    public static VerificationSubmissionEntity from(
            final String transactionId,
            final Map<String, Object> disclosures,
            final JsonNode vpToken) {
        final var entity = new VerificationSubmissionEntity();
        entity.transactionId = transactionId;
        entity.disclosures = disclosures;
        entity.vpToken = vpToken;
        return entity;
    }

    public Integer getId() {
        return id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Map<String, Object> getDisclosures() {
        return disclosures;
    }

    public JsonNode getVpToken() {
        return vpToken;
    }
}
