// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;

/** Public/reference metadata for one immutable provider-backed key version. */
@Entity
@Table(name = "t_signing_key_version")
public class SigningKeyVersionEntity {
    @Id
    @Column(name = "pk_key_version_id", nullable = false)
    private UUID id;

    @Column(name = "key_id", nullable = false)
    private UUID keyId;

    @Column(name = "version", nullable = false)
    private int version;

    /** Provider-local identifier used to construct the provider URI. */
    @Column(name = "provider_key_id", nullable = false)
    private String providerKeyId;

    @Column(name = "key_uri", nullable = false)
    private String keyUri;

    @Column(nullable = false)
    private String algorithm;

    @Column(name = "public_jwk", nullable = false, columnDefinition = "text")
    private String publicJwk;

    @Column(nullable = false)
    private String status;

    /** Technical provider usages, persisted so slot validation does not need private-key access. */
    @Column(nullable = false)
    private String usages = SigningKeyUsage.SIGN.name();

    /** End of the previous-version grace window; null means the legacy indefinite window. */
    @Column(name = "previous_until")
    private Instant previousUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getKeyId() { return keyId; }
    public void setKeyId(UUID keyId) { this.keyId = keyId; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getProviderKeyId() { return providerKeyId; }
    public void setProviderKeyId(String providerKeyId) { this.providerKeyId = providerKeyId; }
    public String getKeyUri() { return keyUri; }
    public void setKeyUri(String keyUri) { this.keyUri = keyUri; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    public String getPublicJwk() { return publicJwk; }
    public void setPublicJwk(String publicJwk) { this.publicJwk = publicJwk; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Set<SigningKeyUsage> getUsages() {
        return Set.of(usages.split(",")).stream()
                .filter(value -> !value.isBlank())
                .map(SigningKeyUsage::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }
    public void setUsages(Set<SigningKeyUsage> value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Signing key usages must not be empty");
        }
        usages = value.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }
    public Instant getPreviousUntil() { return previousUntil; }
    public void setPreviousUntil(Instant value) { previousUntil = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
