// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "t_signing_provider")
public class SigningProviderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_signing_provider_id")
    private Integer id;

    /** Null means that this is the platform-wide provider. */
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "encryption_salt", nullable = false, unique = true)
    private String encryptionSalt;

    @Column(name = "encrypted_configuration", nullable = false, columnDefinition = "text")
    private String encryptedConfiguration;

    @Column(name = "default_provider", nullable = false)
    private boolean defaultProvider;

    public Integer getId() { return id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEncryptionSalt() { return encryptionSalt; }
    public void setEncryptionSalt(String encryptionSalt) { this.encryptionSalt = encryptionSalt; }
    public String getEncryptedConfiguration() { return encryptedConfiguration; }
    public void setEncryptedConfiguration(String encryptedConfiguration) {
        this.encryptedConfiguration = encryptedConfiguration;
    }
    public boolean isDefaultProvider() { return defaultProvider; }
    public void setDefaultProvider(boolean defaultProvider) { this.defaultProvider = defaultProvider; }
}
