// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import org.heidiverse.heidi.entity.model.tenant.TrustRegistryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "t_trust_registry")
public class TrustRegistryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_trust_registry_id", nullable = false)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "trust_registry", nullable = false, columnDefinition = "char(2)", unique = true)
    private TrustRegistryType trustRegistry;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public TrustRegistryType getTrustRegistry() {
        return trustRegistry;
    }

    public void setTrustRegistry(TrustRegistryType trustRegistry) {
        this.trustRegistry = trustRegistry;
    }
}
