// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "t_status_list_allocation")
public class StatusListAllocationEntity {
    @Id
    @Column(name = "pk_status_list_allocation_id", nullable = false)
    private UUID id;

    @Column(name = "fk_status_list_id", nullable = false)
    private UUID statusListId;

    @Column(name = "allocation_key", nullable = false)
    private String allocationKey;

    @Column(name = "entry_index", nullable = false)
    private int index;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getStatusListId() { return statusListId; }
    public void setStatusListId(UUID statusListId) { this.statusListId = statusListId; }
    public String getAllocationKey() { return allocationKey; }
    public void setAllocationKey(String allocationKey) { this.allocationKey = allocationKey; }
    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
