// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.exceptions;

public class TenantNotFoundException extends Exception {
    private final String tenantId;

    public TenantNotFoundException(String tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public String getMessage() {
        return "Tenant " + this.tenantId + " not found!";
    }
}
