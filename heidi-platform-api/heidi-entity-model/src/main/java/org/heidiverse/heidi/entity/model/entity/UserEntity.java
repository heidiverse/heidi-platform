// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "t_user")
public class UserEntity {

    @Id
    @Column(name = "pk_user_id", nullable = false, unique = true)
    private String id;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "thumbnail")
    private String thumbnail;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    public UserEntity() {}

    public UserEntity(String id) {
        this.id = id;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
