// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An immutable certificate chain, independently renewable on a key version. */
@Entity
@Table(name = "t_signing_certificate")
public class SigningCertificateEntity {
    @Id
    @Column(name = "pk_certificate_id", nullable = false)
    private UUID id;

    @Column(name = "key_version_id", nullable = false)
    private UUID keyVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile", nullable = false)
    private SigningCertificateProfile profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private SigningCertificateSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "trust_system")
    private IssuerTrustSystem trustSystem;
    @Column(name = "eudi_leaf_profile")
    private String eudiLeafProfile;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "certificate_chain", nullable = false, columnDefinition = "jsonb")
    private List<String> certificateChain = List.of();

    @Column(name = "not_before")
    private Instant notBefore;

    @Column(name = "not_after")
    private Instant notAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "first_used_at")
    private Instant firstUsedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public UUID getKeyVersionId() { return keyVersionId; }
    public void setKeyVersionId(UUID value) { keyVersionId = value; }
    public SigningCertificateProfile getProfile() { return profile; }
    public void setProfile(SigningCertificateProfile value) { profile = value; }
    public SigningCertificateSource getSource() { return source; }
    public void setSource(SigningCertificateSource value) { source = value; }
    public IssuerTrustSystem getTrustSystem() { return trustSystem; }
    public void setTrustSystem(IssuerTrustSystem value) { trustSystem = value; }
    public String getEudiLeafProfile() { return eudiLeafProfile; }
    public void setEudiLeafProfile(String value) { eudiLeafProfile = value; }
    public List<String> getCertificateChain() { return certificateChain; }
    public void setCertificateChain(List<String> value) { certificateChain = List.copyOf(value); }
    public Instant getNotBefore() { return notBefore; }
    public void setNotBefore(Instant value) { notBefore = value; }
    public Instant getNotAfter() { return notAfter; }
    public void setNotAfter(Instant value) { notAfter = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getFirstUsedAt() { return firstUsedAt; }
    public void setFirstUsedAt(Instant value) { firstUsedAt = value; }
    public Instant getRetiredAt() { return retiredAt; }
    public void setRetiredAt(Instant value) { retiredAt = value; }
}
